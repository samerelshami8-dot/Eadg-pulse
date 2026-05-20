package ai.arena.portscanner;

/**
 * Curated CIDR presets for major CDNs.
 *
 * These are intentionally short, top-of-range subnets meant for fast surveys.
 * For a full per-PoP list, fetch the official feeds:
 *   - Akamai:     https://techdocs.akamai.com/origin-ip-acl/reference/ip-addresses
 *   - Cloudflare: https://www.cloudflare.com/ips-v4
 *   - Fastly:     https://api.fastly.com/public-ip-list
 *
 * Inspired by the curated-list approach used in mirarr-app/network-checker.
 */
final class CdnPresets {
    private CdnPresets() {}

    /** A compact slice of Akamai IP space useful for quick edge surveys. */
    static final String AKAMAI =
            "23.0.0.0/12\n" +
            "23.32.0.0/11\n" +
            "23.48.0.0/14\n" +
            "23.64.0.0/14\n" +
            "23.72.0.0/13\n" +
            "23.192.0.0/11\n" +
            "104.64.0.0/10\n" +
            "184.24.0.0/13\n" +
            "184.50.0.0/15\n" +
            "184.84.0.0/14\n";

    /** Cloudflare's public IPv4 ranges. */
    static final String CLOUDFLARE =
            "103.21.244.0/22\n" +
            "103.22.200.0/22\n" +
            "103.31.4.0/22\n" +
            "104.16.0.0/13\n" +
            "104.24.0.0/14\n" +
            "108.162.192.0/18\n" +
            "131.0.72.0/22\n" +
            "141.101.64.0/18\n" +
            "162.158.0.0/15\n" +
            "172.64.0.0/13\n" +
            "173.245.48.0/20\n" +
            "188.114.96.0/20\n" +
            "190.93.240.0/20\n" +
            "197.234.240.0/22\n" +
            "198.41.128.0/17\n";

    /** Fastly's public IPv4 ranges. */
    static final String FASTLY =
            "23.235.32.0/20\n" +
            "43.249.72.0/22\n" +
            "103.244.50.0/24\n" +
            "103.245.222.0/23\n" +
            "103.245.224.0/24\n" +
            "104.156.80.0/20\n" +
            "140.248.64.0/18\n" +
            "140.248.128.0/17\n" +
            "146.75.0.0/17\n" +
            "151.101.0.0/16\n" +
            "157.52.64.0/18\n" +
            "167.82.0.0/17\n" +
            "167.82.128.0/20\n" +
            "167.82.160.0/20\n" +
            "167.82.224.0/20\n" +
            "172.111.64.0/18\n" +
            "185.31.16.0/22\n" +
            "199.27.72.0/21\n" +
            "199.232.0.0/16\n";
}
