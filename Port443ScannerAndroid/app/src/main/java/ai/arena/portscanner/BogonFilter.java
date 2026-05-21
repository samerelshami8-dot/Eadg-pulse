package ai.arena.portscanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Filters out bogon / private / reserved / multicast IPv4 ranges and normalises a
 * deduplicated, numerically sorted survivor list.
 */
final class BogonFilter {

    /** Each entry is {network32, prefixLen} packed as a long array of size 2. */
    private static final long[][] RANGES = new long[][]{
            cidr("0.0.0.0/8"),
            cidr("10.0.0.0/8"),
            cidr("100.64.0.0/10"),
            cidr("127.0.0.0/8"),
            cidr("169.254.0.0/16"),
            cidr("172.16.0.0/12"),
            cidr("192.0.0.0/24"),
            cidr("192.0.2.0/24"),
            cidr("192.168.0.0/16"),
            cidr("198.18.0.0/15"),
            cidr("198.51.100.0/24"),
            cidr("203.0.113.0/24"),
            cidr("224.0.0.0/4"),
            cidr("240.0.0.0/4"),
            cidr("255.255.255.255/32"),
    };

    private BogonFilter() {}

    /**
     * @param input list of IPv4 literals (already parsed from CIDR/range upstream)
     * @param skipPrivate when true, bogon/private ranges are dropped
     * @return sorted, deduplicated survivors
     */
    static List<String> filter(List<String> input, boolean skipPrivate) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        Set<String> uniq = new LinkedHashSet<>(input.size());
        for (String raw : input) {
            if (raw == null) continue;
            String ip = raw.trim();
            if (ip.isEmpty()) continue;
            long v = ipv4ToLong(ip);
            if (v < 0) continue; // not valid IPv4
            if (skipPrivate && isBogon(v)) continue;
            uniq.add(ip);
        }
        List<String> out = new ArrayList<>(uniq);
        Collections.sort(out, (a, b) -> Long.compare(ipv4ToLong(a), ipv4ToLong(b)));
        return out;
    }

    /** @return -1 if not a valid dotted-quad IPv4. */
    static long ipv4ToLong(String ip) {
        if (ip == null) return -1L;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return -1L;
        long v = 0L;
        for (int i = 0; i < 4; i++) {
            try {
                int oct = Integer.parseInt(parts[i]);
                if (oct < 0 || oct > 255) return -1L;
                v = (v << 8) | oct;
            } catch (NumberFormatException e) {
                return -1L;
            }
        }
        return v;
    }

    static String longToIpv4(long v) {
        return ((v >> 24) & 0xff) + "." + ((v >> 16) & 0xff) + "." + ((v >> 8) & 0xff) + "." + (v & 0xff);
    }

    static boolean isBogon(long ipv4) {
        for (long[] r : RANGES) {
            if (matches(ipv4, r[0], (int) r[1])) return true;
        }
        return false;
    }

    private static boolean matches(long ip, long network, int prefixLen) {
        if (prefixLen <= 0) return true;
        if (prefixLen >= 32) return ip == network;
        long mask = (0xFFFFFFFFL << (32 - prefixLen)) & 0xFFFFFFFFL;
        return (ip & mask) == (network & mask);
    }

    private static long[] cidr(String s) {
        int slash = s.indexOf('/');
        String ip = slash < 0 ? s : s.substring(0, slash);
        int len = slash < 0 ? 32 : Integer.parseInt(s.substring(slash + 1));
        long base = ipv4ToLong(ip);
        return new long[]{base, len};
    }
}
