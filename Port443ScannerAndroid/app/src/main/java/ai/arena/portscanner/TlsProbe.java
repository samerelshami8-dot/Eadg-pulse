package ai.arena.portscanner;

import android.os.Build;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * TLS handshake probe with SNI, ALPN advertisement (h2 + http/1.1), hostname
 * verification, and certificate metadata extraction. Used in pipeline Phase C.
 */
final class TlsProbe {

    private TlsProbe() {}

    static final class Result {
        final boolean ok;
        final int rttMs;
        final String alpn;
        final String certCN;
        final int sanCount;
        final boolean hostnameVerified;
        final String reason;
        /** Created so the caller can hand the live SSLSocket to {@link HttpProbe}. */
        final SSLSocket socket;

        Result(boolean ok, int rttMs, String alpn, String certCN, int sanCount,
               boolean hostnameVerified, String reason, SSLSocket socket) {
            this.ok = ok;
            this.rttMs = rttMs;
            this.alpn = alpn == null ? "" : alpn;
            this.certCN = certCN == null ? "" : certCN;
            this.sanCount = sanCount;
            this.hostnameVerified = hostnameVerified;
            this.reason = reason == null ? "" : reason;
            this.socket = socket;
        }
    }

    /**
     * Open + TLS-handshake against {@code ip:port} announcing {@code sni}. The
     * caller may pass extra names (e.g. HTTP Host) in {@code verifyNames} to OR
     * with the SNI when checking the cert against the default verifier.
     *
     * <p>Returned {@link Result#socket} is non-null only when {@code ok==true} and
     * {@code keepOpen==true}. Callers are responsible for closing it.
     */
    static Result handshake(String ip, int port, int timeoutMs, String sni,
                            List<String> verifyNames, String[] alpnProtocols,
                            boolean keepOpen) {
        Socket plain = new Socket();
        SSLSocket ssl = null;
        long t0 = System.nanoTime();
        try {
            plain.connect(new InetSocketAddress(ip, port), Math.max(500, timeoutMs));
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, null, null);
            SSLSocketFactory factory = ctx.getSocketFactory();
            ssl = (SSLSocket) factory.createSocket(plain, sni == null ? ip : sni, port, true);
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
            int rtt = (int) ((System.nanoTime() - t0) / 1_000_000L);

            String alpn = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try { alpn = ssl.getApplicationProtocol(); } catch (Throwable ignored) {}
                if (alpn == null) alpn = "";
            }

            SSLSession session = ssl.getSession();
            String cn = "";
            int sanCount = 0;
            try {
                Certificate[] peers = session.getPeerCertificates();
                if (peers != null && peers.length > 0 && peers[0] instanceof X509Certificate) {
                    X509Certificate x = (X509Certificate) peers[0];
                    cn = extractCN(x.getSubjectDN() == null ? "" : x.getSubjectDN().getName());
                    Collection<List<?>> alts = x.getSubjectAlternativeNames();
                    if (alts != null) sanCount = alts.size();
                }
            } catch (SSLPeerUnverifiedException ignored) {
                // tolerated; verify step below will report
            } catch (Exception ignored) {}

            boolean verified = verifyAny(verifyNames, sni, session);
            if (!verified && hasNamesToCheck(verifyNames, sni)) {
                safeClose(ssl, plain);
                return new Result(false, rtt, alpn, cn, sanCount, false, "cert-mismatch", null);
            }

            if (keepOpen) {
                return new Result(true, rtt, alpn, cn, sanCount, verified, "", ssl);
            } else {
                safeClose(ssl, plain);
                return new Result(true, rtt, alpn, cn, sanCount, verified, "", null);
            }
        } catch (java.net.SocketTimeoutException e) {
            safeClose(ssl, plain);
            return new Result(false, -1, "", "", 0, false, "timeout", null);
        } catch (IOException e) {
            safeClose(ssl, plain);
            String msg = e.getMessage() == null ? "io" : e.getMessage().toLowerCase(Locale.US);
            String reason;
            if (msg.contains("reset")) reason = "connection-reset";
            else if (msg.contains("alpn") || msg.contains("protocol")) reason = "unsupported-alpn";
            else if (msg.contains("certificate") || msg.contains("trust") || msg.contains("verify")) reason = "cert-mismatch";
            else if (msg.contains("handshake")) reason = "tls-fail";
            else reason = "tls-fail";
            return new Result(false, -1, "", "", 0, false, reason, null);
        } catch (Exception e) {
            safeClose(ssl, plain);
            return new Result(false, -1, "", "", 0, false, "tls-fail", null);
        }
    }

    private static boolean hasNamesToCheck(List<String> verifyNames, String sni) {
        if (verifyNames != null && !verifyNames.isEmpty()) return true;
        return sni != null && !sni.isEmpty();
    }

    private static boolean verifyAny(List<String> verifyNames, String sni, SSLSession session) {
        List<String> names = (verifyNames != null && !verifyNames.isEmpty())
                ? verifyNames
                : (sni == null || sni.isEmpty() ? Collections.<String>emptyList() : Collections.singletonList(sni));
        if (names.isEmpty()) return true;
        try {
            javax.net.ssl.HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
            for (String n : names) {
                if (n == null || n.isEmpty()) continue;
                try { if (hv.verify(n, session)) return true; } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** Pulls CN= value out of a distinguished name string. */
    static String extractCN(String dn) {
        if (dn == null) return "";
        for (String part : dn.split(",")) {
            String t = part.trim();
            if (t.regionMatches(true, 0, "CN=", 0, 3)) return t.substring(3);
        }
        return "";
    }

    private static void safeClose(SSLSocket ssl, Socket plain) {
        try { if (ssl != null) ssl.close(); } catch (Exception ignored) {}
        try { if (plain != null && !plain.isClosed()) plain.close(); } catch (Exception ignored) {}
    }
}
