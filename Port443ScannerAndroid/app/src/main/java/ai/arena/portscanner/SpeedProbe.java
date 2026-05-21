package ai.arena.portscanner;

import android.os.Build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Pulls / pushes real bytes over TLS to measure observed throughput. Used in
 * Phase E (Top-N two-stage download and optional upload).
 */
final class SpeedProbe {

    private SpeedProbe() {}

    static final class Result {
        final boolean ok;
        final double mbps;
        final int ttfbMs;
        final int totalMs;
        final int bytes;
        final String reason;

        Result(boolean ok, double mbps, int ttfbMs, int totalMs, int bytes, String reason) {
            this.ok = ok;
            this.mbps = mbps;
            this.ttfbMs = ttfbMs;
            this.totalMs = totalMs;
            this.bytes = bytes;
            this.reason = reason == null ? "" : reason;
        }

        static Result skipped(String reason) {
            return new Result(false, 0.0, -1, -1, 0, reason);
        }
    }

    /** GET request that drains up to {@code targetBytes} of body. */
    static Result download(String ip, int port, int timeoutMs, String sni,
                           String host, String path, int targetBytes,
                           String[] alpnProtocols) {
        Socket plain = new Socket();
        SSLSocket ssl = null;
        long t0 = System.nanoTime();
        try {
            plain.connect(new InetSocketAddress(ip, port), Math.max(800, timeoutMs));
            ssl = wrap(plain, ip, port, sni, alpnProtocols, timeoutMs);
            ssl.startHandshake();

            String hostHeader = (host == null || host.isEmpty()) ? (sni == null || sni.isEmpty() ? ip : sni) : host;
            String req = "GET " + (path == null || path.isEmpty() ? "/" : path) + " HTTP/1.1\r\n" +
                    "Host: " + hostHeader + "\r\n" +
                    "User-Agent: EdgePulse/2.0\r\n" +
                    "Accept-Encoding: identity\r\n" +
                    "Range: bytes=0-" + Math.max(1, targetBytes - 1) + "\r\n" +
                    "Connection: close\r\n\r\n";
            OutputStream out = ssl.getOutputStream();
            out.write(req.getBytes("UTF-8"));
            out.flush();

            InputStream in = ssl.getInputStream();
            BufferedReader hr = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));
            String statusLine = hr.readLine();
            long firstByteAt = System.nanoTime();
            int ttfb = (int) ((firstByteAt - t0) / 1_000_000L);
            int statusCode = parseStatus(statusLine);
            // Drain headers
            String h;
            while ((h = hr.readLine()) != null) {
                if (h.isEmpty()) break;
            }
            if (statusCode < 200 || statusCode >= 400) {
                return new Result(false, 0.0, ttfb, ttfb, 0, "http-" + statusCode);
            }

            // Read body via the raw socket to count bytes correctly
            byte[] buf = new byte[4096];
            int total = 0;
            int read;
            while (total < targetBytes && (read = in.read(buf, 0, Math.min(buf.length, targetBytes - total))) != -1) {
                total += read;
            }
            int elapsed = (int) ((System.nanoTime() - t0) / 1_000_000L);
            double mbps = mbpsOf(total, elapsed);
            return new Result(true, mbps, ttfb, elapsed, total, "");
        } catch (java.net.SocketTimeoutException e) {
            return new Result(false, 0.0, -1, -1, 0, "timeout");
        } catch (IOException e) {
            return new Result(false, 0.0, -1, -1, 0, "speed-fail");
        } catch (Exception e) {
            return new Result(false, 0.0, -1, -1, 0, "speed-fail");
        } finally {
            try { if (ssl != null) ssl.close(); } catch (Exception ignored) {}
            try { if (!plain.isClosed()) plain.close(); } catch (Exception ignored) {}
        }
    }

    /** POST body of {@code targetBytes} of zeros; throughput counted on the upload side only. */
    static Result upload(String ip, int port, int timeoutMs, String sni,
                         String host, String path, int targetBytes,
                         String[] alpnProtocols) {
        Socket plain = new Socket();
        SSLSocket ssl = null;
        long t0 = System.nanoTime();
        try {
            plain.connect(new InetSocketAddress(ip, port), Math.max(800, timeoutMs));
            ssl = wrap(plain, ip, port, sni, alpnProtocols, timeoutMs);
            ssl.startHandshake();

            String hostHeader = (host == null || host.isEmpty()) ? (sni == null || sni.isEmpty() ? ip : sni) : host;
            String req = "POST " + (path == null || path.isEmpty() ? "/" : path) + " HTTP/1.1\r\n" +
                    "Host: " + hostHeader + "\r\n" +
                    "User-Agent: EdgePulse/2.0\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "Content-Length: " + Math.max(0, targetBytes) + "\r\n" +
                    "Accept-Encoding: identity\r\n" +
                    "Connection: close\r\n\r\n";
            OutputStream out = ssl.getOutputStream();
            out.write(req.getBytes("UTF-8"));
            out.flush();
            byte[] chunk = new byte[4096];
            long t1 = System.nanoTime();
            int sent = 0;
            while (sent < targetBytes) {
                int n = Math.min(chunk.length, targetBytes - sent);
                out.write(chunk, 0, n);
                sent += n;
            }
            out.flush();
            int uploadMs = (int) ((System.nanoTime() - t1) / 1_000_000L);

            // Read response status (best-effort), then close
            InputStream in = ssl.getInputStream();
            BufferedReader hr = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));
            String statusLine = hr.readLine();
            int statusCode = parseStatus(statusLine);
            if (statusCode < 200 || statusCode >= 500) {
                return new Result(false, 0.0, -1, uploadMs, sent, "http-" + statusCode);
            }
            double mbps = mbpsOf(sent, uploadMs);
            return new Result(true, mbps, -1, uploadMs, sent, "");
        } catch (java.net.SocketTimeoutException e) {
            return new Result(false, 0.0, -1, -1, 0, "timeout");
        } catch (IOException e) {
            return new Result(false, 0.0, -1, -1, 0, "speed-fail");
        } catch (Exception e) {
            return new Result(false, 0.0, -1, -1, 0, "speed-fail");
        } finally {
            try { if (ssl != null) ssl.close(); } catch (Exception ignored) {}
            try { if (!plain.isClosed()) plain.close(); } catch (Exception ignored) {}
        }
    }

    private static SSLSocket wrap(Socket plain, String ip, int port, String sni,
                                  String[] alpnProtocols, int timeoutMs) throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, null, null);
        SSLSocketFactory factory = ctx.getSocketFactory();
        String sniForSocket = (sni == null || sni.isEmpty()) ? ip : sni;
        SSLSocket ssl = (SSLSocket) factory.createSocket(plain, sniForSocket, port, true);
        ssl.setSoTimeout(Math.max(1500, timeoutMs));
        SSLParameters params = ssl.getSSLParameters();
        if (sni != null && !sni.isEmpty()) {
            List<SNIServerName> names = new ArrayList<>(1);
            try { names.add(new SNIHostName(sni)); } catch (IllegalArgumentException ignored) {}
            if (!names.isEmpty()) params.setServerNames(names);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && alpnProtocols != null && alpnProtocols.length > 0) {
            try { params.setApplicationProtocols(alpnProtocols); } catch (Throwable ignored) {}
        }
        ssl.setSSLParameters(params);
        return ssl;
    }

    private static int parseStatus(String line) {
        if (line == null) return 0;
        String[] parts = line.split(" ");
        if (parts.length < 2) return 0;
        try { return Integer.parseInt(parts[1]); } catch (NumberFormatException e) { return 0; }
    }

    /** Bytes + milliseconds → megabits per second. */
    static double mbpsOf(int bytes, int ms) {
        if (ms <= 0) return 0.0;
        double bits = bytes * 8.0;
        double secs = ms / 1000.0;
        if (secs <= 0.0) return 0.0;
        return (bits / secs) / 1_000_000.0;
    }

    /** Median of a small list; returns 0 for empty input. */
    static double median(List<Double> xs) {
        if (xs == null || xs.isEmpty()) return 0.0;
        List<Double> copy = new ArrayList<>(xs);
        java.util.Collections.sort(copy);
        int n = copy.size();
        if (n % 2 == 1) return copy.get(n / 2);
        return (copy.get(n / 2 - 1) + copy.get(n / 2)) / 2.0;
    }

    /** Standard deviation of values, used as jitter proxy. */
    static double stddev(List<Double> xs) {
        if (xs == null || xs.size() < 2) return 0.0;
        double mean = 0.0;
        for (double v : xs) mean += v;
        mean /= xs.size();
        double sq = 0.0;
        for (double v : xs) sq += (v - mean) * (v - mean);
        return Math.sqrt(sq / xs.size());
    }
}
