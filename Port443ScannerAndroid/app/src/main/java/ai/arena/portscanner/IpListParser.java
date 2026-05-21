package ai.arena.portscanner;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses a free-form newline / comma / space separated list into IPv4 literals.
 * Accepts:
 * <ul>
 *   <li>{@code 1.2.3.4}</li>
 *   <li>{@code 1.2.3.0/24}</li>
 *   <li>{@code 1.2.3.4-1.2.3.20}</li>
 *   <li>{@code 1.2.3.4-20} short range</li>
 * </ul>
 *
 * <p>Domains pass through unchanged and are resolved separately by
 * {@link DohResolver}; everything else is silently dropped.
 */
final class IpListParser {

    private IpListParser() {}

    /** Hard cap for any single CIDR or range expansion. */
    static final int MAX_EXPAND = 65536;

    static List<String> parse(String text) {
        Set<String> uniq = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) return new ArrayList<>(uniq);
        String[] tokens = text.split("[\\s,;]+");
        for (String raw : tokens) {
            if (raw == null) continue;
            String t = raw.trim();
            if (t.isEmpty()) continue;
            int slash = t.indexOf('/');
            int dash = t.indexOf('-');
            if (slash > 0) {
                addAll(uniq, expandCidr(t));
            } else if (dash > 0 && DohResolver.looksLikeIpv4(t.substring(0, dash))) {
                addAll(uniq, expandRange(t));
            } else if (DohResolver.looksLikeIpv4(t)) {
                uniq.add(t);
            }
            // domains are not expanded here; caller resolves them via DohResolver
        }
        return new ArrayList<>(uniq);
    }

    /** Variant that also returns domain hostnames so the UI can resolve them. */
    static List<String> extractHostnames(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        String[] tokens = text.split("[\\s,;]+");
        for (String raw : tokens) {
            if (raw == null) continue;
            String t = raw.trim();
            if (t.isEmpty()) continue;
            if (t.contains("/") || t.contains("-")) continue;
            if (DohResolver.looksLikeIpv4(t)) continue;
            if (looksLikeDomain(t)) out.add(t);
        }
        return out;
    }

    static boolean looksLikeDomain(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.length() > 253) return false;
        if (!s.contains(".")) return false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (!(Character.isLetterOrDigit(ch) || ch == '-' || ch == '.' || ch == '_')) return false;
        }
        return true;
    }

    static List<String> expandCidr(String cidr) {
        List<String> out = new ArrayList<>();
        int slash = cidr.indexOf('/');
        if (slash <= 0) return out;
        String ip = cidr.substring(0, slash);
        long base = BogonFilter.ipv4ToLong(ip);
        if (base < 0) return out;
        int pfx;
        try { pfx = Integer.parseInt(cidr.substring(slash + 1)); }
        catch (NumberFormatException e) { return out; }
        if (pfx < 0 || pfx > 32) return out;
        long size = (pfx == 0) ? (1L << 32) : (1L << (32 - pfx));
        long start = base & ((0xFFFFFFFFL << (32 - pfx)) & 0xFFFFFFFFL);
        long count = Math.min(size, MAX_EXPAND);
        for (long i = 0; i < count; i++) out.add(BogonFilter.longToIpv4(start + i));
        return out;
    }

    static List<String> expandRange(String range) {
        List<String> out = new ArrayList<>();
        int dash = range.indexOf('-');
        if (dash <= 0) return out;
        String a = range.substring(0, dash).trim();
        String b = range.substring(dash + 1).trim();
        long start = BogonFilter.ipv4ToLong(a);
        long end;
        if (DohResolver.looksLikeIpv4(b)) {
            end = BogonFilter.ipv4ToLong(b);
        } else {
            // short form: a.b.c.x-y
            int lastDot = a.lastIndexOf('.');
            if (lastDot < 0) return out;
            try {
                int last = Integer.parseInt(b);
                if (last < 0 || last > 255) return out;
                end = BogonFilter.ipv4ToLong(a.substring(0, lastDot + 1) + last);
            } catch (NumberFormatException e) { return out; }
        }
        if (start < 0 || end < 0) return out;
        if (end < start) { long t = start; start = end; end = t; }
        long count = Math.min(end - start + 1, MAX_EXPAND);
        for (long i = 0; i < count; i++) out.add(BogonFilter.longToIpv4(start + i));
        return out;
    }

    private static void addAll(Set<String> sink, List<String> items) {
        for (String s : items) sink.add(s);
    }
}
