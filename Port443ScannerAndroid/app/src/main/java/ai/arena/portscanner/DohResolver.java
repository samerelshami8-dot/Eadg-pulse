package ai.arena.portscanner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Resolves hostnames to A records via DNS-over-HTTPS so the user can paste
 * domain names into the Scan target box and we still get IP literals.
 *
 * <p>Queries all configured providers and unions their answers. Failures are
 * swallowed; the worst case is an empty list which the engine treats as "no
 * resolution".
 */
final class DohResolver {

    private DohResolver() {}

    /** Default endpoints used when caller passes {@code null}. */
    static List<String> defaultProviders() {
        return ScanConfig.DEFAULT_DOH_PROVIDERS;
    }

    /**
     * @param hostname FQDN to resolve (no scheme, no port)
     * @param providers DoH JSON endpoints; pass null for defaults
     * @return deduplicated, insertion-ordered list of A records
     */
    static List<String> resolve(String hostname, List<String> providers) {
        Set<String> uniq = new LinkedHashSet<>();
        if (hostname == null) return new ArrayList<>(uniq);
        String host = hostname.trim();
        if (host.isEmpty()) return new ArrayList<>(uniq);
        if (looksLikeIpv4(host)) {
            uniq.add(host);
            return new ArrayList<>(uniq);
        }
        List<String> useProviders = (providers == null || providers.isEmpty()) ? defaultProviders() : providers;
        for (String endpoint : useProviders) {
            try {
                List<String> ips = queryOne(endpoint, host);
                uniq.addAll(ips);
            } catch (Exception ignored) {
                // silent failure per spec
            }
        }
        return new ArrayList<>(uniq);
    }

    private static List<String> queryOne(String endpoint, String host) throws Exception {
        String sep = endpoint.contains("?") ? "&" : "?";
        URL url = new URL(endpoint + sep + "name=" + urlEncode(host) + "&type=A");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("accept", "application/dns-json");
            conn.setRequestProperty("user-agent", "EdgePulse/2.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn instanceof HttpsURLConnection) {
                // Use default verifier; nothing custom to set.
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return new ArrayList<>();
            InputStream in = conn.getInputStream();
            StringBuilder sb = new StringBuilder(256);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            char[] buf = new char[1024];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            return parseAnswer(sb.toString());
        } finally {
            try { conn.disconnect(); } catch (Exception ignored) {}
        }
    }

    static List<String> parseAnswer(String body) {
        List<String> ips = new ArrayList<>();
        if (body == null || body.isEmpty()) return ips;
        try {
            JSONObject root = new JSONObject(body);
            JSONArray answers = root.optJSONArray("Answer");
            if (answers == null) return ips;
            for (int i = 0; i < answers.length(); i++) {
                JSONObject obj = answers.optJSONObject(i);
                if (obj == null) continue;
                int type = obj.optInt("type", -1);
                if (type != 1) continue; // A only
                String data = obj.optString("data", "");
                if (looksLikeIpv4(data)) ips.add(data);
            }
        } catch (Exception ignored) {}
        return ips;
    }

    private static String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    static boolean looksLikeIpv4(String s) {
        if (s == null) return false;
        String[] parts = s.split("\\.");
        if (parts.length != 4) return false;
        for (String p : parts) {
            try {
                int v = Integer.parseInt(p);
                if (v < 0 || v > 255) return false;
            } catch (NumberFormatException e) { return false; }
        }
        return true;
    }
}
