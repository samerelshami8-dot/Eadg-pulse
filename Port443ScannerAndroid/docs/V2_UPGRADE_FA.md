# EdgePulse v2.0 — ارتقای معماری و رابط کاربری

این سند تغییرات نسخه 2.0.0 (versionCode `20`) را توضیح می‌دهد. هدف این انتشار، بازنویسی هسته اسکنر به یک pipeline چندمرحله‌ای و تبدیل UI تک‌صفحه‌ای v1.6 به چهار صفحه با bottom navigation است.

> توجه: `applicationId` همچنان `ai.arena.portscanner` است؛ پکیج عوض نشده. نصب نسخه قبلی بدون نیاز به uninstall از روی v2.0 ارتقا می‌یابد.

## 1. معماری Java (16 کلاس جدید + 5 پشتیبان)

پکیج: `ai.arena.portscanner`

| فایل | نقش |
|---|---|
| `MainActivity` (public) | فقط lifecycle و سیم‌کشی صفحه‌ها. ۴۳۲ خط. |
| `PageHost` | FrameLayout container + bottom navigation (۴ تب با ایکن emoji و label فارسی). |
| `ScanPage` | صفحه 📡 اسکن: input هدف، toolbar (paste / file / DoH / clear)، تولید CIDR، CDN presets، انتخاب engine (Quick / Pair)، Start/Stop، progress + mini-stats. |
| `ProfilesPage` | صفحه 🗂️ پروفایل‌ها: spinner انتخاب، ذخیره IPهای آخرین اسکن، export/import JSON، CRUD پروفایل، لیست IPها با دکمه حذف. |
| `ResultsPage` | صفحه 📊 نتایج: filter chips (همه / OK / Cloudflare / Fastly / Akamai / دیگران)، sort spinner، Top-N picker، کارت‌های نتیجه با متن قابل انتخاب، toolbar خروجی (Copy / Share / TXT / CSV / JSON). |
| `AdvancedPage` | صفحه ⚙️ پیشرفته: section های Connection / TLS / Speed / Scoring / Filter / Theme؛ collapsible؛ Save و Reset. |
| `UiKit` | drawable, color, dp, ripple, gradient، widget factory ها (card, row, button, editText, chip, pill, collapsible). |
| `ScanConfig` | snapshot immutable تنظیمات یک اسکن (Builder pattern). |
| `ScanResult` | مدل نتیجه per-IP با سطح OK/MAYBE/FAIL، metricها، warning ها، score. |
| `ScannerEngine` | orchestrator مراحل A تا G؛ Listener-based؛ همه callback ها روی main thread. |
| `BogonFilter` | فیلتر private/reserved/multicast/broadcast + dedupe + sort عددی. |
| `TcpProbe` | TCP connect probe با تشخیص refused/reset/unreach/timeout. |
| `TlsProbe` | TLS handshake + `SNIHostName` + `setApplicationProtocols(["h2","http/1.1"])` (API 29+) + `getApplicationProtocol()` + hostname verify (OR روی verifyNames) + استخراج CN و SAN. |
| `HttpProbe` | GET HTTP/1.1 با Host + Path سفارشی، استخراج status/TTFB/body bytes/ALPN، تشخیص CDN vendor و PoP از header ها، marker search. |
| `SpeedProbe` | download (Range header) + upload (POST) با محاسبه Mbps؛ توابع median و stddev (به‌عنوان jitter proxy). |
| `DohResolver` | DNS-over-HTTPS با cloudflare/google/quad9؛ union جواب‌ها؛ silent failure. |
| `Scorer` | فرمول جدید + sortByScore + topNDistinct با /24 (یا هر prefix). |
| `ProfileStore` | SharedPreferences key `edgepulse_profiles_v2`؛ Profile JSON، CRUD، export/import، addIpsToProfile، removeIp. |
| `ResultExporter` | TXT (top-N از OK+MAYBE)، CSV (RFC 4180)، JSON کامل. |
| `IpListParser` | parser برای IP/CIDR/range/short-range + extractHostnames. |
| `CdnPresets` (موجود) | حفظ شده، Akamai/Cloudflare/Fastly. |

## 2. Pipeline اسکنر (`ScannerEngine.scan`)

```
A. Bogon Filter          [sync]
B. Fast TCP Screen       [concurrent، timeout 800ms پیش‌فرض] → فقط survivors
C. TLS + ALPN + Cert     [concurrent، attempts × IP، min-success]
D. HTTP Host/Path        [concurrent، فقط Engine = PAIR]
E. Two-Stage Speed       [E1: همه TLS-ok با 256KB، E2: Top-N با 2MB × 2 round، upload اختیاری]
F. Scoring               [sync، new formula]
G. (در ResultExporter)   [topNDistinct اختیاری برای /24]
```

callback های `Listener.onPrepared`, `onProgress(phase, done, total, ok, maybe, fail)`, `onResult`, `onLog`, `onFinished(all, stopped)` همگی روی main thread اجرا می‌شوند. کلاس MainActivity فقط Listener را پیاده می‌کند.

## 3. CDN Fingerprint Detection (`HttpProbe.detectVendor`)

| Vendor | کلیدهای header |
|---|---|
| Cloudflare | `cf-ray`، `cf-cache-status`، `server: cloudflare` |
| Fastly | `x-served-by`، `via: ... varnish`، `x-cache: ...fastly...` |
| Akamai | `x-akamai-*`، `server: AkamaiGHost`، `via: ... akamai`، `x-cache: TCP_*` |
| CloudFront | `x-amz-cf-id`، `x-amz-cf-pop`، `server: ... CloudFront` |
| Google | `server: gws|gvs`، `via: ... google` |
| BunnyCDN | `server: BunnyCDN` |
| ArvanCloud | `server: ArvanCloud`، `ar-poweredby` |

PoP و cache status هم استخراج می‌شوند و در ResultsPage به‌صورت chip روی هر کارت نمایش داده می‌شوند.

## 4. فرمول امتیازدهی (Scorer)

```text
score = successRate * 1000
      + downloadMbps * 25
      + uploadMbps * 5
      - ttfbMs * 0.8
      - connectMs * 0.3
      - errorPenalty
      - jitterMs * 5
      + (cdnVendorKnown ? 50 : 0)
      + (alpnH2 ? 30 : 0)
```

`errorPenalty`:
- cert-mismatch یا cert-error: **200**
- misdirected (421) / http-4xx غیرمنتظره: **150**
- timeout: **100**

نتیجه به یک رقم اعشار rounding می‌شود. مرتب‌سازی نتایج: امتیاز بالاتر اول؛ tiebreaker = TTFB کمتر.

`topNDistinct(results, n, prefixLen=24)` فقط بهترین IP هر /24 را نگه می‌دارد و سپس n اول را برمی‌گرداند.

## 5. UI

- **bottom navigation** ۴ تابی با emoji + label فارسی (📡 اسکن، 🗂️ پروفایل، 📊 نتایج، ⚙️ پیشرفته)؛ تب فعال با background روشن (`CARD_HI`) و حاشیه `PRIMARY`.
- همه drawableها programmatic با `GradientDrawable` و `RippleDrawable`؛ هیچ XML layout اضافه نشده.
- `targetInput` با ارتفاع ثابت `200dp` + scroll عمودی؛ paste فهرست بزرگ صفحه را بلند نمی‌کند.
- Pair fields (SNI/Host/Path/Expected) فقط وقتی engine روی `Pair Test` است visible می‌شود.
- Results redraw سبک: هر ۲۰ نتیجه ورودی یک‌بار rebuild؛ در finalize حتماً rebuild.
- متن نتایج `setTextIsSelectable(true)`؛ دکمه‌های Copy/Share/Save در حین اسکن یا وقتی نتیجه‌ای نیست disabled.
- RTL: `LAYOUT_DIRECTION_RTL` در `MainActivity.onCreate` وقتی `Locale.getDefault().getLanguage().equals("fa")` فعال می‌شود.

## 6. Stability fixes حفظ‌شده از v1.6 و بعدش

1. paste / parsing ورودی هدف فقط داخلی است و خود EditText را بازنویسی نمی‌کند.
2. `setTextIsSelectable(true)` روی متن‌های نتیجه.
3. Copy/Share/Save بر اساس وجود خروجی و وضعیت اسکن enable/disable می‌شوند.
4. در worker `onFinished` اگر `stopRequested` فعال است، شمارش OK/Maybe/Fail در toast درست انجام می‌شود ولی Scan جدید آغاز نمی‌شود.
5. بعد از ذخیره IPهای اسکن در پروفایل، همان پروفایل دوباره `select` می‌شود و `ProfilesPage.refresh()` صدا زده می‌شود.
6. `PowerManager.WakeLock` با timeout یک ساعته در `acquireWakeLock` و آزادسازی در `releaseWakeLock`/`onFinished`/`onDestroy`.

## 7. Migration از v1.6

- پروفایل‌های v1.x کلید دیگری داشتند؛ v2 با کلید `edgepulse_profiles_v2` کاملاً تازه شروع می‌کند و یک Profile «Default» می‌سازد. تنظیمات قدیمی پاک نمی‌شوند ولی نادیده گرفته می‌شوند.
- تنظیمات Advanced با کلید `edgepulse_advanced_v2` نگه‌داری می‌شوند.
- خروجی TXT همچنان همان فرمت «IP در هر خط» است؛ CSV/JSON ستون‌های بیشتری دارند.

## 8. ساخت و انتشار

```bash
cd Port443ScannerAndroid
chmod +x gradlew
./gradlew --no-daemon assembleDebug
```

APK خروجی: `app/build/outputs/apk/debug/app-debug.apk` (در releases هم به‌نام `EdgePulse-debug.apk` کپی می‌شود). minSdk = 23، targetSdk = 35، compileSdk = 35.

## 9. چیزهایی که عمداً تغییر نکرده

- پکیج `ai.arena.portscanner` و applicationId
- مسیر پروژه `Port443ScannerAndroid/`
- محتوای `CdnPresets.java`
- `.github/workflows/build.yml`
- LICENSE
- بدون dependency جدید (material / appcompat / okhttp / retrofit / gson ممنوع‌اند)
- بدون permission جدید (INTERNET / ACCESS_NETWORK_STATE / WAKE_LOCK)
- بدون resource layout XML، بدون ProGuard rule، بدون Kotlin
