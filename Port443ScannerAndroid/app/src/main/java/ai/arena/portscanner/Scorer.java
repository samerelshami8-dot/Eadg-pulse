package ai.arena.portscanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Final scoring + Top-N selection + /N subnet diversity. Stateless.
 *
 * <p>Formula (per spec):<br>
 * {@code score = successRate*1000 + downloadMbps*25 + uploadMbps*5
 *               - ttfbMs*0.8 - connectMs*0.3 - errorPenalty - jitterMs*5
 *               + (cdnVendorKnown ? 50 : 0) + (alpnH2 ? 30 : 0)}
 */
final class Scorer {

    private Scorer() {}

    /** Pulled out so callers can tweak penalty weights without touching the formula. */
    static final int PENALTY_CERT_MISMATCH = 200;
    static final int PENALTY_HTTP_4XX      = 150;
    static final int PENALTY_TIMEOUT       = 100;

    /** Compute and assign the score on the result; returns the same instance. */
    static ScanResult score(ScanResult r) {
        if (r == null) return null;
        double sr = r.successRate();
        int connectMs = r.tcpRttMs > 0 ? r.tcpRttMs : (r.tlsRttMs > 0 ? r.tlsRttMs : 0);
        int ttfb = r.ttfbMs > 0 ? r.ttfbMs : 0;
        int penalty = computePenalty(r);
        r.errorPenalty = penalty;
        boolean vendorKnown = r.hasKnownVendor();
        boolean alpnH2 = "h2".equalsIgnoreCase(r.alpn);

        double s =
                sr * 1000.0
                + r.downloadMbps * 25.0
                + r.uploadMbps * 5.0
                - ttfb * 0.8
                - connectMs * 0.3
                - penalty
                - r.jitterMs * 5.0
                + (vendorKnown ? 50.0 : 0.0)
                + (alpnH2 ? 30.0 : 0.0);

        r.score = Math.round(s * 10.0) / 10.0;
        return r;
    }

    static int computePenalty(ScanResult r) {
        if (r == null) return 0;
        int p = 0;
        for (String w : r.warningsView()) {
            if ("cert-mismatch".equals(w)) p += PENALTY_CERT_MISMATCH;
            else if ("misdirected".equals(w) || "http-4xx".equals(w) || "http-unexpected".equals(w)) p += PENALTY_HTTP_4XX;
            else if ("timeout".equals(w)) p += PENALTY_TIMEOUT;
            else if ("cert-error".equals(w)) p += PENALTY_CERT_MISMATCH;
        }
        return p;
    }

    /** Sort highest-score first. Ties broken by lower TTFB. */
    static void sortByScore(List<ScanResult> list) {
        if (list == null) return;
        Collections.sort(list, new Comparator<ScanResult>() {
            @Override public int compare(ScanResult a, ScanResult b) {
                int c = Double.compare(b.score, a.score);
                if (c != 0) return c;
                int at = a.ttfbMs <= 0 ? Integer.MAX_VALUE : a.ttfbMs;
                int bt = b.ttfbMs <= 0 ? Integer.MAX_VALUE : b.ttfbMs;
                return Integer.compare(at, bt);
            }
        });
    }

    /**
     * Keep only the best IP from each /{prefixLen} block, then take the first
     * {@code n} (0 = all). Input list is expected sorted by score already; if
     * not, the function sorts a copy first.
     */
    static List<ScanResult> topNDistinct(List<ScanResult> input, int n, int prefixLen) {
        if (input == null || input.isEmpty()) return new ArrayList<>();
        List<ScanResult> sorted = new ArrayList<>(input);
        sortByScore(sorted);
        int pfx = Math.max(0, Math.min(32, prefixLen));
        Map<Long, ScanResult> best = new LinkedHashMap<>();
        for (ScanResult r : sorted) {
            long key = subnetKey(r.ip, pfx);
            if (!best.containsKey(key)) best.put(key, r);
        }
        List<ScanResult> out = new ArrayList<>(best.values());
        if (n > 0 && out.size() > n) return new ArrayList<>(out.subList(0, n));
        return out;
    }

    /** Visible for tests. Returns Long.MIN_VALUE for non-IPv4 input. */
    static long subnetKey(String ip, int prefixLen) {
        long v = BogonFilter.ipv4ToLong(ip);
        if (v < 0) return Long.MIN_VALUE;
        if (prefixLen <= 0) return 0L;
        if (prefixLen >= 32) return v;
        long mask = (0xFFFFFFFFL << (32 - prefixLen)) & 0xFFFFFFFFL;
        return v & mask;
    }
}
