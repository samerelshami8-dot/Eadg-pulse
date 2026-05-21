package ai.arena.portscanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds plain-text / CSV / JSON dumps from a list of {@link ScanResult}s for
 * sharing, copying, and saving. Pure / stateless.
 */
final class ResultExporter {

    private ResultExporter() {}

    /**
     * One IP per line. Only OK and MAYBE entries are emitted. {@code topN==0}
     * means no cap.
     */
    static String toTxt(List<ScanResult> all, int topN) {
        if (all == null || all.isEmpty()) return "";
        List<ScanResult> ranked = new ArrayList<>();
        for (ScanResult r : all) {
            if (r == null) continue;
            if (r.level == ScanResult.Level.OK || r.level == ScanResult.Level.MAYBE) ranked.add(r);
        }
        Scorer.sortByScore(ranked);
        StringBuilder sb = new StringBuilder(ranked.size() * 16);
        int limit = topN > 0 ? Math.min(topN, ranked.size()) : ranked.size();
        for (int i = 0; i < limit; i++) sb.append(ranked.get(i).ip).append('\n');
        return sb.toString();
    }

    /**
     * RFC4180 CSV. Header line first, then one row per result. All results
     * regardless of level.
     */
    static String toCsv(List<ScanResult> all) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("ip,level,status,rtt_ms,download_mbps,upload_mbps,ttfb_ms,http_status,cdn_vendor,pop,score\r\n");
        if (all == null) return sb.toString();
        for (ScanResult r : all) {
            if (r == null) continue;
            sb.append(csv(r.ip)).append(',');
            sb.append(csv(r.level.name())).append(',');
            sb.append(csv(r.reason)).append(',');
            sb.append(r.tcpRttMs > 0 ? r.tcpRttMs : (r.tlsRttMs > 0 ? r.tlsRttMs : 0)).append(',');
            sb.append(fmtMbps(r.downloadMbps)).append(',');
            sb.append(fmtMbps(r.uploadMbps)).append(',');
            sb.append(r.ttfbMs > 0 ? r.ttfbMs : 0).append(',');
            sb.append(r.httpStatus).append(',');
            sb.append(csv(r.cdnVendor)).append(',');
            sb.append(csv(r.popId)).append(',');
            sb.append(fmtScore(r.score)).append("\r\n");
        }
        return sb.toString();
    }

    /** Full JSON array, suitable for backup / programmatic consumption. */
    static String toJson(List<ScanResult> all) {
        JSONArray arr = new JSONArray();
        if (all == null) return arr.toString();
        for (ScanResult r : all) {
            if (r == null) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("ip", r.ip);
                o.put("port", r.port);
                o.put("level", r.level.name());
                o.put("reason", r.reason);
                o.put("tcp_ok", r.tcpOk);
                o.put("tcp_rtt_ms", r.tcpRttMs);
                o.put("tls_ok", r.tlsOk);
                o.put("tls_rtt_ms", r.tlsRttMs);
                o.put("alpn", r.alpn);
                o.put("cert_cn", r.certCN);
                o.put("san_count", r.sanCount);
                o.put("hostname_verified", r.hostnameVerified);
                o.put("http_ok", r.httpOk);
                o.put("http_status", r.httpStatus);
                o.put("ttfb_ms", r.ttfbMs);
                o.put("http_total_ms", r.httpTotalMs);
                o.put("body_bytes", r.bodyBytes);
                o.put("cdn_vendor", r.cdnVendor);
                o.put("pop_id", r.popId);
                o.put("cache_status", r.cacheStatus);
                o.put("marker_found", r.markerFound);
                o.put("download_mbps", Math.round(r.downloadMbps * 100.0) / 100.0);
                o.put("upload_mbps", Math.round(r.uploadMbps * 100.0) / 100.0);
                o.put("jitter_ms", Math.round(r.jitterMs * 100.0) / 100.0);
                o.put("ok_rounds", r.okRounds);
                o.put("total_rounds", r.totalRounds);
                o.put("error_penalty", r.errorPenalty);
                o.put("score", r.score);
                JSONArray w = new JSONArray();
                for (String t : r.warningsView()) w.put(t);
                o.put("warnings", w);
                arr.put(o);
            } catch (JSONException ignored) {}
        }
        try { return arr.toString(2); } catch (Exception e) { return arr.toString(); }
    }

    // ---------- helpers ----------

    static String csv(String v) {
        if (v == null) return "";
        boolean mustQuote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        if (!mustQuote) return v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    static String fmtMbps(double v) {
        if (v <= 0.0) return "0";
        return String.format(Locale.US, "%.2f", v);
    }

    static String fmtScore(double v) {
        return String.format(Locale.US, "%.1f", v);
    }
}
