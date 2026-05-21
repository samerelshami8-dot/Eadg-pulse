package ai.arena.portscanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-IP outcome of a scan. The engine builds one of these per target and the UI
 * renders / sorts / exports from this single source of truth.
 *
 * <p>Instances are mutated only inside the engine on its own threads; once handed
 * to the UI through {@link ScannerEngine.Listener} they should be treated as
 * read-only.
 */
final class ScanResult {

    /** Coarse health classification used for UI badges and TXT export filtering. */
    enum Level {
        /** TCP + TLS + HTTP (when requested) all OK with marker/status check. */
        OK,
        /** Some signal (TCP+TLS) but HTTP failed or partial. */
        MAYBE,
        /** TCP/TLS dropped or unrecoverable error. */
        FAIL
    }

    final String ip;
    int port;
    Level level = Level.FAIL;
    String reason = "";

    // Phase B
    boolean tcpOk = false;
    int tcpRttMs = -1;

    // Phase C
    boolean tlsOk = false;
    int tlsRttMs = -1;
    String alpn = "";
    String certCN = "";
    int sanCount = 0;
    boolean hostnameVerified = false;

    // Phase D
    boolean httpOk = false;
    int httpStatus = 0;
    int ttfbMs = -1;
    int httpTotalMs = -1;
    int bodyBytes = 0;
    String cdnVendor = "";
    String popId = "";
    String cacheStatus = "";
    boolean markerFound = false;

    // Phase E
    double downloadMbps = 0.0;
    double uploadMbps = 0.0;
    /** Optional jitter sampled across speed rounds, used in scoring. */
    double jitterMs = 0.0;

    // Aggregates / Phase F
    int okRounds = 0;
    int totalRounds = 0;
    int errorPenalty = 0;
    double score = 0.0;

    /** Free-form list of warnings encountered during the pipeline (TLS errors etc). */
    final List<String> warnings = new ArrayList<>();

    ScanResult(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    double successRate() {
        if (totalRounds <= 0) return 0.0;
        return Math.min(1.0, (double) okRounds / (double) totalRounds);
    }

    /** Lowercase vendor tag used by Results filter chips. */
    String vendorKey() {
        if (cdnVendor == null) return "";
        return cdnVendor.toLowerCase(java.util.Locale.US);
    }

    boolean hasKnownVendor() {
        return cdnVendor != null && !cdnVendor.isEmpty() && !"unknown".equalsIgnoreCase(cdnVendor);
    }

    /** Best-effort numeric IPv4 for sorting; returns Long.MAX_VALUE for non-IPv4. */
    long ipNumeric() {
        return ipv4ToLong(ip);
    }

    static long ipv4ToLong(String ip) {
        if (ip == null) return Long.MAX_VALUE;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return Long.MAX_VALUE;
        try {
            long v = 0L;
            for (int i = 0; i < 4; i++) {
                int oct = Integer.parseInt(parts[i]);
                if (oct < 0 || oct > 255) return Long.MAX_VALUE;
                v = (v << 8) | oct;
            }
            return v;
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    /** Append a unique warning tag (e.g. tls-fail, cert-mismatch). */
    void addWarning(String tag) {
        if (tag == null || tag.isEmpty()) return;
        if (!warnings.contains(tag)) warnings.add(tag);
    }

    List<String> warningsView() { return Collections.unmodifiableList(warnings); }

    /** Display-friendly level label in Persian. */
    String levelLabelFa() {
        switch (level) {
            case OK: return "باز";
            case MAYBE: return "نامطمئن";
            default: return "بسته";
        }
    }
}
