package ai.arena.portscanner;

import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * HTTP/1.1 host + path probe used in Phase D. Speaks raw HTTP over a fresh TLS
 * socket so we can SNI-pin to any IP. Detects CDN vendor + PoP from response
 * headers.
 */
final class HttpProbe {

    private HttpProbe() {}

    /** Tags returned in {@link Result#cdnVendor}. */
    static final String VENDOR_CLOUDFLARE = "Cloudflare";
    static final String VENDOR_FASTLY     = "Fastly";
    static final String VENDOR_AKAMAI     = "Akamai";
    static final String VENDOR_CLOUDFRONT = "CloudFront";
    static final String VENDOR_GOOGLE     = "Google";
    static final String VENDOR_BUNNY      = "BunnyCDN";
    static final String VENDOR_ARVAN      = "ArvanCloud";
    static final String VENDOR_UNKNOWN    = "";

    /** Status codes considered acceptable by default when no expectedStatus is set. */
    private static final int[] DEFAULT_OK_STATUSES = new int[]{200, 206, 301, 302, 304, 403};

    static final class Result {
        final boolean ok;
        final int statusCode;
        final int ttfbMs;
        final int totalMs;
        final int bodyBytes;
        final String alpn;
        final String cdnVendor;
        final String popId;
        final String cacheStatus;
        final boolean markerFound;
        final String reason;

        Result(boolean ok, int statusCode, int ttfbMs, int totalMs, int bodyBytes,
               String alpn, String cdnVendor, String popId, String cacheStatus,
               boolean markerFound, String reason) {
            this.ok = ok;
            this.statusCode = statusCode;
            this.ttfbMs = ttfbMs;
            this.totalMs = totalMs;
            this.bodyBytes = bodyBytes;
            this.alpn = alpn == null ? "" : alpn;
            this.cdnVendor = cdnVendor == null ? VENDOR_UNKNOWN : cdnVendor;
            this.popId = popId == null ? "" : popId;
            this.cacheStatus = cacheStatus == null ? "" : cacheStatus;
            this.markerFound = markerFound;
            this.reason = reason == null ? "" : reason;
        }
    }

    /**
     * Run a GET against {@code https://{ip}:{port}{path}} with Host: {host} and
     * SNI: {sni}. {@code maxBodyBytes} caps the body buffer we hold in memory.
     */
    static Result request(String ip, int port, int timeoutMs,
                          String sni, String host, String path,
                          int expectedStatus, String marker, int maxBodyBytes,
                          String[] alpnProtocols) {
        Socket plain = new Socket();
        SSLSocket ssl = null;
        long t0 = System.nanoTime();
        try {
            plain.connect(new InetSocketAddress(ip, port), Math.max(500, timeoutMs));
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, null, null);
            SSLSocketFactory factory = ctx.getSocketFactory();
            String sniForSocket = (sni == null || sni.isEmpty()) ? host : sni;
            if (sniForSocket == null || sniForSocket.isEmpty()) sniForSocket = ip;
            ssl = (SSLSocket) factory.createSocket(plain, sniForSocket, port, true);
            ssl.setSoTimeout(Math.max(500, timeoutMs));

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
            ssl.startHandshake();

            String alpn = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try { alpn = ssl.getApplicationProtocol(); } catch (Throwable ignored) {}
                if (alpn == null) alpn = "";
            }

            // ---- send GET ----
            String hostHeader = (host == null || host.isEmpty()) ? sni : host;
            if (hostHeader == null || hostHeader.isEmpty()) hostHeader = ip;
            String req = "GET " + (path == null || path.isEmpty() ? "/" : path) + " HTTP/1.1\r\n" +
                    "Host: " + hostHeader + "\r\n" +
                    "User-Agent: EdgePulse/2.0\r\n" +
                    "Accept: */*\r\n" +
                    "Accept-Encoding: identity\r\n" +
                    "Connection: close\r\n\r\n";
            OutputStream out = ssl.getOutputStream();
            out.write(req.getBytes("UTF-8"));
            out.flush();

            // ---- read status + headers ----
            InputStream in = ssl.getInputStream();
            long firstByteAt = -1L;
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream(1024);
            int headerEnd = -1;
            byte[] tmp = new byte[1024];
            int read;
            while ((read = in.read(tmp)) != -1) {
                if (firstByteAt < 0) firstByteAt = System.nanoTime();
                headerBuf.write(tmp, 0, read);
                int idx = indexOfCrlfCrlf(headerBuf.toByteArray());
                if (idx >= 0) { headerEnd = idx; break; }
                if (headerBuf.size() > 32 * 1024) { headerEnd = headerBuf.size(); break; }
            }
            if (firstByteAt < 0) firstByteAt = System.nanoTime();
            int ttfb = (int) ((firstByteAt - t0) / 1_000_000L);

            byte[] all = headerBuf.toByteArray();
            int headerLen = headerEnd >= 0 ? headerEnd + 4 : all.length;
            String headerStr = new String(all, 0, headerLen, "ISO-8859-1");
            ParsedHeaders ph = parseHeaders(headerStr);

            // ---- read body (capped) ----
            ByteArrayOutputStream body = new ByteArrayOutputStream(Math.min(maxBodyBytes, 8192));
            if (headerLen < all.length) {
                int extra = all.length - headerLen;
                int take = Math.min(extra, maxBodyBytes);
                body.write(all, headerLen, take);
            }
            while (body.size() < maxBodyBytes && (read = in.read(tmp, 0, Math.min(tmp.length, maxBodyBytes - body.size()))) != -1) {
                body.write(tmp, 0, read);
            }
            int total = (int) ((System.nanoTime() - t0) / 1_000_000L);

            int status = ph.statusCode;
            boolean statusOk = isStatusAcceptable(status, expectedStatus);
            String cdn = detectVendor(ph.headers);
            String popId = extractPop(ph.headers, cdn);
            String cacheStatus = extractCache(ph.headers, cdn);
            boolean markerFound = false;
            if (marker != null && !marker.isEmpty()) {
                String bodyStr = new String(body.toByteArray(), 0, body.size(), "UTF-8");
                markerFound = bodyStr.contains(marker);
            } else {
                markerFound = true;
            }

            String reason = "";
            if (!statusOk) {
                if (status == 421) reason = "misdirected";
                else if (status == 525 || status == 526) reason = "cert-error";
                else if (status >= 500) reason = "http-5xx";
                else if (status >= 400) reason = "http-4xx";
                else reason = "http-unexpected";
            } else if (marker != null && !marker.isEmpty() && !markerFound) {
                reason = "marker-missing";
            }

            boolean ok = statusOk && (marker == null || marker.isEmpty() || markerFound);
            return new Result(ok, status, ttfb, total, body.size(), alpn, cdn, popId, cacheStatus, markerFound, reason);
        } catch (java.net.SocketTimeoutException e) {
            return new Result(false, 0, -1, -1, 0, "", VENDOR_UNKNOWN, "", "", false, "timeout");
        } catch (IOException e) {
            String msg = e.getMessage() == null ? "io" : e.getMessage().toLowerCase(Locale.US);
            String reason;
            if (msg.contains("reset")) reason = "connection-reset";
            else if (msg.contains("certificate") || msg.contains("trust")) reason = "cert-mismatch";
            else if (msg.contains("alpn")) reason = "unsupported-alpn";
            else reason = "http-fail";
            return new Result(false, 0, -1, -1, 0, "", VENDOR_UNKNOWN, "", "", false, reason);
        } catch (Exception e) {
            return new Result(false, 0, -1, -1, 0, "", VENDOR_UNKNOWN, "", "", false, "http-fail");
        } finally {
            try { if (ssl != null) ssl.close(); } catch (Exception ignored) {}
            try { if (!plain.isClosed()) plain.close(); } catch (Exception ignored) {}
        }
    }

    // ---------- parsing helpers ----------

    private static final class ParsedHeaders {
        int statusCode;
        Map<String, String> headers = new LinkedHashMap<>();
    }

    private static ParsedHeaders parseHeaders(String raw) {
        ParsedHeaders p = new ParsedHeaders();
        if (raw == null) return p;
        String[] lines = raw.split("\r\n");
        if (lines.length == 0) return p;
        String status = lines[0];
        // HTTP/1.1 200 OK
        String[] sParts = status.split(" ");
        if (sParts.length >= 2) {
            try { p.statusCode = Integer.parseInt(sParts[1]); } catch (NumberFormatException ignored) {}
        }
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i];
            if (l == null || l.isEmpty()) continue;
            int c = l.indexOf(':');
            if (c <= 0) continue;
            String k = l.substring(0, c).trim().toLowerCase(Locale.US);
            String v = l.substring(c + 1).trim();
            // Last header wins; servers rarely repeat important CDN headers.
            p.headers.put(k, v);
        }
        return p;
    }

    private static int indexOfCrlfCrlf(byte[] buf) {
        for (int i = 0; i <= buf.length - 4; i++) {
            if (buf[i] == 13 && buf[i + 1] == 10 && buf[i + 2] == 13 && buf[i + 3] == 10) return i;
        }
        return -1;
    }

    static boolean isStatusAcceptable(int status, int expectedStatus) {
        if (status <= 0) return false;
        if (status == 421 || status == 525 || status == 526) return false;
        if (expectedStatus > 0) return status == expectedStatus;
        for (int s : DEFAULT_OK_STATUSES) if (s == status) return true;
        return status >= 200 && status < 400;
    }

    private static String hdr(Map<String, String> h, String key) {
        if (h == null || key == null) return "";
        String v = h.get(key);
        return v == null ? "" : v;
    }

    /** Public for tests. */
    static String detectVendor(Map<String, String> h) {
        if (h == null) return VENDOR_UNKNOWN;
        String server = hdr(h, "server");
        String serverL = server.toLowerCase(Locale.US);

        if (h.containsKey("cf-ray") || h.containsKey("cf-cache-status") || serverL.contains("cloudflare")) return VENDOR_CLOUDFLARE;
        if (h.containsKey("x-amz-cf-id") || h.containsKey("x-amz-cf-pop") || serverL.contains("cloudfront")) return VENDOR_CLOUDFRONT;
        String xCache = hdr(h, "x-cache").toLowerCase(Locale.US);
        String via = hdr(h, "via").toLowerCase(Locale.US);
        if (h.containsKey("x-served-by") || via.contains("varnish") || xCache.contains("fastly")) return VENDOR_FASTLY;
        if (serverL.contains("akamai") || via.contains("akamai")) return VENDOR_AKAMAI;
        for (String k : h.keySet()) if (k.startsWith("x-akamai-")) return VENDOR_AKAMAI;
        if (xCache.startsWith("tcp_") && (h.containsKey("x-check-cacheable") || via.contains("akamai"))) return VENDOR_AKAMAI;
        if (serverL.equals("gws") || serverL.equals("gvs") || serverL.startsWith("gvs ") || via.contains("google")) return VENDOR_GOOGLE;
        if (serverL.contains("bunnycdn")) return VENDOR_BUNNY;
        if (serverL.contains("arvancloud") || h.containsKey("ar-poweredby")) return VENDOR_ARVAN;
        return VENDOR_UNKNOWN;
    }

    private static String extractPop(Map<String, String> h, String vendor) {
        if (h == null || vendor == null) return "";
        switch (vendor) {
            case VENDOR_CLOUDFLARE: {
                String ray = hdr(h, "cf-ray");
                int dash = ray.lastIndexOf('-');
                return dash > 0 ? ray.substring(dash + 1) : ray;
            }
            case VENDOR_CLOUDFRONT:
                return hdr(h, "x-amz-cf-pop");
            case VENDOR_FASTLY:
                return hdr(h, "x-served-by");
            case VENDOR_AKAMAI:
                return hdr(h, "x-akamai-request-id");
            default:
                return "";
        }
    }

    private static String extractCache(Map<String, String> h, String vendor) {
        if (h == null) return "";
        if (VENDOR_CLOUDFLARE.equals(vendor)) return hdr(h, "cf-cache-status");
        if (h.containsKey("x-cache")) return h.get("x-cache");
        if (h.containsKey("cache-status")) return h.get("cache-status");
        return "";
    }

    /** Allows callers to predeclare the default-accept set; visible for {@link Scorer}. */
    static List<Integer> defaultAcceptedStatuses() {
        List<Integer> out = new ArrayList<>(DEFAULT_OK_STATUSES.length);
        for (int s : DEFAULT_OK_STATUSES) out.add(s);
        return Collections.unmodifiableList(out);
    }
}
