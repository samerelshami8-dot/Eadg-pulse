# EdgePulse — Port443ScannerAndroid

EdgePulse is a native Android tool for scanning TCP / TLS (port 443) across IP ranges, validating CDN edge nodes, fingerprinting them, measuring real download/upload throughput, and exporting results in TXT / CSV / JSON.

The full project lives in [`Port443ScannerAndroid/`](Port443ScannerAndroid). The latest debug APK is at [`releases/EdgePulse-debug.apk`](releases/EdgePulse-debug.apk).

## What's new in v2.0

- **Multi-phase pipeline scanner** — Bogon filter → fast TCP screen → TLS+ALPN+cert verify → HTTP Host/Path probe → two-stage speed test → scoring. Each phase has clear inputs / outputs and lives in its own class.
- **CDN vendor + PoP fingerprinting** — Cloudflare, Fastly, Akamai, CloudFront, Google, BunnyCDN, ArvanCloud detected from response headers (cf-ray, x-served-by, x-amz-cf-pop, etc.).
- **DNS-over-HTTPS resolver** — paste a domain like `example.com` into the target box, hit `🌐 DoH دامنه`, and we resolve A records via Cloudflare / Google / Quad9.
- **Four-page UI with bottom navigation** — Scan / Profiles / Results / Advanced. No more single endless scroll page.
- **Profiles** — named SharedPreferences profiles (`Default` seeded on first launch) that bundle target IPs + SNI/Host/Path; full JSON export/import.
- **New scoring** — `successRate*1000 + downloadMbps*25 + uploadMbps*5 − ttfb*0.8 − connect*0.3 − errorPenalty − jitter*5 + vendorKnown*50 + alpnH2*30`. Penalties for cert-mismatch (200), unexpected 4xx (150), and timeouts (100).
- **Subnet diversity** — `Scorer.topNDistinct(results, n, /24)` to avoid Top-N collapsing into one block.
- **Two-stage speed test** — small 256 KB sample over all candidates, then 2 MB × 2 rounds for the Top-N (median + jitter).
- **Results page** — filter chips (همه / OK / Cloudflare / Fastly / Akamai / دیگران), sort by Score/Down/TTFB/RTT, Top-N picker (همه/10/20/50/100), per-IP cards with selectable text, throttled redraws (rebuild every 20 incoming results).
- **Stability fixes preserved** — paste doesn't mutate target input, result text is selectable, Copy/Share/Save disabled during scan and when empty, `finishScan()` is suppressed when stop was requested, profile re-selected after save, WakeLock retained.

See [`Port443ScannerAndroid/docs/V2_UPGRADE_FA.md`](Port443ScannerAndroid/docs/V2_UPGRADE_FA.md) for the full Persian changelog and architecture map.

## Highlights

- Real TCP connect probe with sub-second fast screen + TLS handshake with SNI / ALPN / cert hostname verify
- HTTP/1.1 Host + Path validation (Pair Test) with marker matching and CDN fingerprint detection
- Real download / upload throughput probe with TTFB measurement and median-of-N stability
- Configurable concurrency, attempts, min-success, and per-stage timeouts
- CIDR + range parsing (`1.2.3.0/24`, `1.2.3.4-1.2.3.20`, short `1.2.3.4-20`) with private/reserved/bogon filtering
- One-click presets for Akamai, Cloudflare, Fastly + Custom slot
- TXT / CSV (RFC 4180) / JSON export; copy / share / save with system file picker
- WakeLock during scans so the OS doesn't kill long runs
- Smart ranking combining successRate, throughput, latency, jitter, and bonuses for known CDN + ALPN h2
- Persian-first UI with full RTL support, dark palette, programmatic drawables (no XML layouts, no `material`, no `appcompat`)

## Build

Requirements:

- JDK 17 or 21
- Android SDK with Platform 35 and Build-Tools 35.0.0
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` pointing to your SDK

```bash
cd Port443ScannerAndroid
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

For a signed release build, configure signing in `app/build.gradle` or `~/.gradle/gradle.properties`, then:

```bash
./gradlew assembleRelease
```

## CI

Every push runs `./gradlew assembleDebug` via GitHub Actions; see [`.github/workflows/build.yml`](.github/workflows/build.yml). The debug APK is uploaded as a workflow artifact.

## Design notes

Persian-language design documents live in [`Port443ScannerAndroid/docs/`](Port443ScannerAndroid/docs/):

- `V2_UPGRADE_FA.md` — v2.0 architecture, file map, scoring formula, migration notes (start here)
- `README_FA.md` — full Persian README
- `STRATEGIC_CORE_UPGRADE_FA.md` — v1.5 strategic upgrades
- `CDN_EDGE_LOGIC_PLAN_FA.md` — edge selection pipeline plan
- `DEEP_AUDIT_FIXES_FA.md` — earlier deep-audit notes
- `SIMPLE_ADVANCED_MODE_FA.md` — simple/advanced mode design
- `SPEEDTEST_ENDPOINT_EXAMPLE.md` — speed test endpoint notes
- `UI_PREVIEW.html` — UI mockup preview

## Contributing

- Fork, create a feature branch, open a PR.
- Keep changes focused; CI must stay green.

## License

MIT — see [`LICENSE`](LICENSE).

> Use responsibly and only scan networks you own or are authorized to test.
