# EdgePulse — Port443ScannerAndroid

EdgePulse is a native Android tool for scanning TCP / TLS (port 443) across IP ranges, validating CDN edge nodes, measuring real download/upload throughput, running stability/jitter/loss analysis, and exporting CSV reports.

The full project lives in [`Port443ScannerAndroid/`](Port443ScannerAndroid). The latest debug APK is at [`releases/EdgePulse-debug.apk`](releases/EdgePulse-debug.apk).

## Highlights

- Real TCP connect probe, optional TLS handshake with SNI, and combined TCP+TLS mode
- Pair / Fronting test: validates IP + SNI + Host + Path together (not just raw port)
- Real download/upload throughput probe with TTFB measurement
- Deep Analysis: stability score from multi-round RTT / jitter / loss on top candidates
- Configurable concurrency, attempts, min-success, timeout
- CIDR + range parsing (`1.2.3.0/24`, `1.2.3.4-1.2.3.20`) with private/reserved filtering
- One-click presets for Akamai, Cloudflare, and Fastly IP space
- CSV export with RFC 4180 escaping; copy / share top results
- WakeLock during scans so the OS doesn't kill long runs
- Smart ranking score combining level, RTT, TTFB, throughput, and stability
- Persian-first UI with full RTL support

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
