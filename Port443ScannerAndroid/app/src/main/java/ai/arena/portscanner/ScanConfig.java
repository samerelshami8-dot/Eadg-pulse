package ai.arena.portscanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of the scan settings captured at the moment Start is pressed.
 *
 * <p>Everything the {@link ScannerEngine} pipeline needs lives here. Once built the
 * instance must not be mutated; downstream stages may read it from any thread.
 */
final class ScanConfig {

    /** Two-phase scan engine selector. */
    enum Engine {
        /** TCP + TLS only, no HTTP host validation. Fast surveys. */
        QUICK,
        /** TCP + TLS + HTTP Host/Path validation (Pair / Fronting). */
        PAIR
    }

    // ---- Connection ----
    final int port;
    final int fastTcpTimeoutMs;
    final int tlsTimeoutMs;
    final int httpTimeoutMs;
    final int concurrency;
    final int attempts;
    final int minSuccess;
    final boolean skipPrivate;

    // ---- TLS ----
    final String sni;            // SNI host, may be empty in Quick mode
    /** Names to feed into {@link javax.net.ssl.HostnameVerifier#verify(String, javax.net.ssl.SSLSession)}. */
    final List<String> verifyNames;
    /** Prefer h2 in ALPN; falls back to http/1.1. */
    final boolean alpnPreferH2;

    // ---- HTTP / Pair test ----
    final Engine engine;
    final String host;           // HTTP Host header
    final String path;           // request path, defaults to "/"
    final int expectedStatus;    // 0 = accept any 2xx/3xx
    final String marker;         // optional body marker substring
    final int httpMaxBytes;      // body bytes to read

    // ---- Speed test ----
    final boolean speedEnabled;
    final String speedHost;
    final String downloadPath;
    final int downloadKbStage1;
    final int downloadKbStage2;
    final boolean uploadEnabled;
    final String uploadPath;
    final int uploadKb;
    final int twoStageTopN;

    // ---- Scoring ----
    final int distinctPrefixLen;
    final int copyTopN;          // 0 = all

    private ScanConfig(Builder b) {
        this.port = b.port;
        this.fastTcpTimeoutMs = b.fastTcpTimeoutMs;
        this.tlsTimeoutMs = b.tlsTimeoutMs;
        this.httpTimeoutMs = b.httpTimeoutMs;
        this.concurrency = b.concurrency;
        this.attempts = b.attempts;
        this.minSuccess = b.minSuccess;
        this.skipPrivate = b.skipPrivate;
        this.sni = b.sni == null ? "" : b.sni.trim();
        this.verifyNames = Collections.unmodifiableList(new ArrayList<>(b.verifyNames));
        this.alpnPreferH2 = b.alpnPreferH2;
        this.engine = b.engine;
        this.host = b.host == null ? "" : b.host.trim();
        this.path = (b.path == null || b.path.trim().isEmpty()) ? "/" : b.path.trim();
        this.expectedStatus = b.expectedStatus;
        this.marker = b.marker == null ? "" : b.marker;
        this.httpMaxBytes = b.httpMaxBytes;
        this.speedEnabled = b.speedEnabled;
        this.speedHost = b.speedHost == null ? "" : b.speedHost.trim();
        this.downloadPath = (b.downloadPath == null || b.downloadPath.trim().isEmpty()) ? "/" : b.downloadPath.trim();
        this.downloadKbStage1 = b.downloadKbStage1;
        this.downloadKbStage2 = b.downloadKbStage2;
        this.uploadEnabled = b.uploadEnabled;
        this.uploadPath = (b.uploadPath == null || b.uploadPath.trim().isEmpty()) ? "/" : b.uploadPath.trim();
        this.uploadKb = b.uploadKb;
        this.twoStageTopN = b.twoStageTopN;
        this.distinctPrefixLen = b.distinctPrefixLen;
        this.copyTopN = b.copyTopN;
    }

    static Builder builder() { return new Builder(); }

    /** Builder so MainActivity can collect fields gradually and we still ship an immutable snapshot. */
    static final class Builder {
        int port = 443;
        int fastTcpTimeoutMs = 800;
        int tlsTimeoutMs = 3000;
        int httpTimeoutMs = 4000;
        int concurrency = 80;
        int attempts = 2;
        int minSuccess = 1;
        boolean skipPrivate = true;
        String sni = "";
        List<String> verifyNames = new ArrayList<>();
        boolean alpnPreferH2 = true;
        Engine engine = Engine.QUICK;
        String host = "";
        String path = "/";
        int expectedStatus = 0;
        String marker = "";
        int httpMaxBytes = 4096;
        boolean speedEnabled = false;
        String speedHost = "";
        String downloadPath = "/";
        int downloadKbStage1 = 256;
        int downloadKbStage2 = 2048;
        boolean uploadEnabled = false;
        String uploadPath = "/";
        int uploadKb = 256;
        int twoStageTopN = 10;
        int distinctPrefixLen = 24;
        int copyTopN = 0;

        Builder port(int v) { port = v; return this; }
        Builder fastTcpTimeoutMs(int v) { fastTcpTimeoutMs = v; return this; }
        Builder tlsTimeoutMs(int v) { tlsTimeoutMs = v; return this; }
        Builder httpTimeoutMs(int v) { httpTimeoutMs = v; return this; }
        Builder concurrency(int v) { concurrency = v; return this; }
        Builder attempts(int v) { attempts = v; return this; }
        Builder minSuccess(int v) { minSuccess = v; return this; }
        Builder skipPrivate(boolean v) { skipPrivate = v; return this; }
        Builder sni(String v) { sni = v; return this; }
        Builder verifyNames(List<String> v) {
            verifyNames = (v == null) ? new ArrayList<>() : new ArrayList<>(v);
            return this;
        }
        Builder verifyNamesCsv(String csv) {
            verifyNames = new ArrayList<>();
            if (csv != null) {
                for (String part : csv.split(",")) {
                    String t = part.trim();
                    if (!t.isEmpty()) verifyNames.add(t);
                }
            }
            return this;
        }
        Builder alpnPreferH2(boolean v) { alpnPreferH2 = v; return this; }
        Builder engine(Engine v) { engine = v; return this; }
        Builder host(String v) { host = v; return this; }
        Builder path(String v) { path = v; return this; }
        Builder expectedStatus(int v) { expectedStatus = v; return this; }
        Builder marker(String v) { marker = v; return this; }
        Builder httpMaxBytes(int v) { httpMaxBytes = v; return this; }
        Builder speedEnabled(boolean v) { speedEnabled = v; return this; }
        Builder speedHost(String v) { speedHost = v; return this; }
        Builder downloadPath(String v) { downloadPath = v; return this; }
        Builder downloadKbStage1(int v) { downloadKbStage1 = v; return this; }
        Builder downloadKbStage2(int v) { downloadKbStage2 = v; return this; }
        Builder uploadEnabled(boolean v) { uploadEnabled = v; return this; }
        Builder uploadPath(String v) { uploadPath = v; return this; }
        Builder uploadKb(int v) { uploadKb = v; return this; }
        Builder twoStageTopN(int v) { twoStageTopN = v; return this; }
        Builder distinctPrefixLen(int v) { distinctPrefixLen = v; return this; }
        Builder copyTopN(int v) { copyTopN = v; return this; }

        ScanConfig build() { return new ScanConfig(this); }
    }

    /** Resolve the host header for HTTP probing. Falls back to SNI, then to IP literal. */
    String effectiveHost(String ip) {
        if (!host.isEmpty()) return host;
        if (!sni.isEmpty()) return sni;
        return ip;
    }

    /** Resolve the SNI for TLS handshakes. Falls back to host header. */
    String effectiveSni() {
        if (!sni.isEmpty()) return sni;
        if (!host.isEmpty()) return host;
        return "";
    }

    /** Names actually used for hostname verification; if list is empty we OR SNI + Host. */
    List<String> effectiveVerifyNames() {
        if (!verifyNames.isEmpty()) return verifyNames;
        List<String> derived = new ArrayList<>(2);
        if (!sni.isEmpty()) derived.add(sni);
        if (!host.isEmpty() && !host.equalsIgnoreCase(sni)) derived.add(host);
        if (derived.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(derived);
    }

    String effectiveSpeedHost() {
        if (!speedHost.isEmpty()) return speedHost;
        if (!host.isEmpty()) return host;
        return sni;
    }

    /** Convenience: ALPN order to advertise. */
    String[] alpnOrder() {
        return alpnPreferH2 ? new String[]{"h2", "http/1.1"} : new String[]{"http/1.1", "h2"};
    }

    /** Default DoH endpoints used by {@link DohResolver}. */
    static final List<String> DEFAULT_DOH_PROVIDERS = Collections.unmodifiableList(Arrays.asList(
            "https://cloudflare-dns.com/dns-query",
            "https://dns.google/resolve",
            "https://dns.quad9.net:5053/dns-query"
    ));
}
