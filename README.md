# EdgePulse — Port443ScannerAndroid

EdgePulse is a lightweight native Android tool for scanning TCP/TLS (port 443) across IP ranges, performing deep analysis, and exporting CSV results.

**Highlights**
- Native TCP & TLS probes with optional SNI and fronting tests
- Download/upload speed checks and stability/jitter analysis
- CSV export and shareable results
- Small, focused UI for quick scans on-device

## Quick Start

1. Open the Android project in Android Studio (folder: `Port443ScannerAndroid`).
2. Build a debug APK:

```bash
cd Port443ScannerAndroid
./gradlew assembleDebug
```

3. The debug APK is saved to `Port443ScannerAndroid/app/build/outputs/apk/debug/app-debug.apk`.

## Release
If you want a signed release APK, configure signing in `app/build.gradle` or `gradle.properties`, then:

```bash
./gradlew assembleRelease
```

Note: In this environment lint checks may fail; you can reproduce locally in Android Studio.

## Contributing
- Fork, create a feature branch, and open a PR.
- Keep changes focused and add tests where applicable.

## License
This project is licensed under the MIT License — see `LICENSE`.

Enjoy — use responsibly and only scan networks you own or are authorized to test.
