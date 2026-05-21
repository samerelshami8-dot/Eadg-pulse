# EdgePulse اندروید — نسخه 2.0.0 Pipeline Core

## v2.0 (versionCode 20)

- **هسته اسکنر pipeline چندمرحله‌ای**: Bogon → TCP fast screen → TLS+ALPN+cert → HTTP Host/Path → Two-stage speed → Scoring → Subnet diversity.
- **رابط ۴ صفحه‌ای با bottom navigation**: 📡 اسکن، 🗂️ پروفایل، 📊 نتایج، ⚙️ پیشرفته.
- **شناسایی CDN**: Cloudflare، Fastly، Akamai، CloudFront، Google، BunnyCDN، ArvanCloud از روی header های پاسخ + PoP + cache status.
- **DNS-over-HTTPS** برای تبدیل دامنه به IP (Cloudflare/Google/Quad9).
- **پروفایل JSON** با ذخیره SharedPreferences (`edgepulse_profiles_v2`) و export/import.
- **خروجی TXT / CSV / JSON** با file picker سیستم.
- **فرمول امتیازدهی جدید** با successRate، throughput، penalty، bonus ALPN h2 و CDN شناخته‌شده.

برای جزئیات و معماری: [`V2_UPGRADE_FA.md`](V2_UPGRADE_FA.md).

---

# EdgePulse اندروید - نسخه 1.6.0 Intelligence Core

این نسخه علاوه بر اسکن TCP/TLS و تست سرعت واقعی، با حالت Deep Analysis و تحلیل پایداری هم ارتقا داده شده تا برای تحلیل کیفیت شبکه حرفه‌ای‌تر باشد.

## ارتقاهای ظاهری نسخه 1.3.0

- هدر حرفه‌ای با گرادیان، عنوان، نسخه و چیپ‌های قابلیت‌ها
- طراحی کارت‌های شیشه‌ای/تیره با گوشه‌های گرد، stroke و elevation
- دکمه‌های گرادیانی با ripple و ارتفاع استاندارد
- ورودی‌های مدرن‌تر با border و padding بهتر
- آیکون اصلی اختصاصی با مفهوم Shield + Lightning + Speed Bars
- Adaptive Icon برای اندرویدهای جدید
- رنگ‌بندی یکپارچه Dark Pro
- نام اپ: `EdgePulse`
- نسخه نمایشی داخل UI: `v1.2 Pro`

## امکانات فنی

- اسکن واقعی TCP با `Socket.connect`
- حالت TLS Handshake
- حالت سخت‌گیرانه TCP + TLS
- انتخاب پورت دلخواه؛ پیش‌فرض 443
- SNI اختیاری
- تست سرعت دانلود روی IPهای باز
- تست سرعت آپلود اختیاری روی endpoint مجاز برای POST
- مرتب‌سازی نتایج بر اساس کیفیت: وضعیت، سرعت دانلود، سرعت آپلود، RTT و Stability
- حالت Deep Analysis روی Candidateها
- خروجی CSV با Mbps، TTFB، Jitter و Loss
- ورود IP تکی، CIDR و بازه
- Import فایل txt
- تولید IP از CIDR/Range و ارسال مستقیم به اسکنر
- تنظیم timeout، concurrency، attempts و min-success
- حذف اختیاری IPهای خصوصی، لوکال و رزرو شده
- نمایش پیشرفت، سرعت اسکن و زمان باقی‌مانده
- کپی، Share و ذخیره خروجی
- ذخیره خودکار تنظیمات آخرین اجرا
- تحلیل پایداری اتصال روی IPهای باز
- امتیازدهی هوشمند با Stability Score
- داشبورد خلاصه تحلیل در UI

## فایل‌های آیکون

```text
app/src/main/res/drawable/ic_launcher.xml
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/mipmap-anydpi/ic_launcher.xml
app/src/main/res/mipmap-anydpi/ic_launcher_round.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
```

## نکته تست سرعت

برای تست سرعت واقعی، بهتر است روی دامنه‌ای که مالک آن هستید یک فایل تست قرار دهید:

```text
https://example.com/speedtest.bin
```

تنظیم پیشنهادی داخل اپ:

```text
Host/SNI: example.com
Download Path: /speedtest.bin
Download Size: 1024 تا 4096 KB
Upload Test: فقط اگر endpoint مجاز دارید
```

## Build APK

1. پوشه `Port443ScannerAndroid` را در Android Studio باز کنید.
2. Gradle Sync را انجام دهید.
3. از مسیر زیر APK بسازید:

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

برای انتشار عمومی:

```text
Build > Generate Signed Bundle / APK
```

## هشدار استفاده مجاز

فقط روی IPها، دامنه‌ها و شبکه‌هایی استفاده کنید که مالک آن‌ها هستید یا اجازه تست دارید. تست سرعت روی حجم بالا می‌تواند مصرف پهنای باند ایجاد کند.

## نسخه 1.3.0 - حالت Pair / Fronting Test

در این نسخه حالت جدیدی اضافه شده که به‌جای فقط بررسی IP، ترکیب کامل زیر را تست می‌کند:

```text
IP + Front SNI + HTTP Host + Test Path
```

فیلدهای جدید:

```text
Front SNI
HTTP Host
Test Path
Expected Status
Expected Marker
Test Size KB
Rounds
```

در این حالت اپ به IP وصل می‌شود، TLS را با `Front SNI` انجام می‌دهد، سپس درخواست HTTP/1.1 را با `Host` جداگانه می‌فرستد و وضعیت HTTP، marker، TTFB، Mbps و Score را ثبت می‌کند.

خروجی CSV جدید شامل این ستون‌هاست:

```text
http_status, fronting_ok, score, sni, host, path
```

نکته: این قابلیت برای تست دامنه‌ها و زیرساخت‌هایی است که مالک یا مجاز به تست آن‌ها هستید.


## اصلاح مهم نسخه 1.3.2

دو اصلاح مهم انجام شد:

1. دکمه کپی دوباره فقط IP خام را کپی می‌کند، حتی اگر Pair Test روشن باشد.
2. در TLS با SNI دامنه، hostname verification فعال شد. این مهم است چون `SSLSocket` به‌صورت پیش‌فرض فقط handshake را انجام می‌دهد و ممکن است certificate از نظر نام دامنه معتبر نباشد ولی scanner اشتباهاً آن را موفق حساب کند. با این اصلاح نتیجه به رفتار کلاینت‌های واقعی HTTPS نزدیک‌تر می‌شود.


## اصلاحات Deep Audit نسخه 1.3.3

در بازبینی عمیق، باگ اصلی این بود که Pair Mode هنوز قبل از تست اصلی از مسیر عمومی TCP/TLS عبور می‌کرد. این باعث می‌شد اگر SNI عمومی خالی یا متفاوت باشد، ترکیب معتبر IP+FrontSNI+Host اصلاً تست نشود. اکنون وقتی Pair Mode روشن است، تست مستقیم از مسیر Pair انجام می‌شود.

همچنین:

- hostname verification به روش سازگارتر با اندروید انجام می‌شود.
- تست آپلود دیگر فقط write/flush را معیار نمی‌گیرد و منتظر HTTP response می‌ماند.
- دکمه Copy فقط IP خام کپی می‌کند.


## نسخه 1.4.0 - Simple / Advanced Mode

در این نسخه UI به دو حالت تقسیم شد:

### Simple Mode

فقط موارد اصلی برای Pair Test نمایش داده می‌شود:

```text
IP List
Front SNI
HTTP Host
Test Path
Expected Status
Start / Stop
Results
Copy IPs
```

### Advanced Mode

تنظیمات کامل فعال می‌شود:

```text
Port, Timeout, Concurrency, Attempts, Min Success
Generic TCP/TLS mode
Download/Upload speed test
Expected Marker
Test KB / Rounds
CIDR Generator
```

هدف این تغییر این است که مسیر اصلی از شلوغی دور نشود: در حالت ساده، تمرکز روی ترکیب معتبر `IP + SNI + Host + Path` است.

پیش‌نمایش جدید در فایل زیر قرار دارد:

```text
UI_PREVIEW.html
```


## نسخه 1.5.0 - Strategic Core Upgrade

در این نسخه هسته Pair Test ارتقا داده شد:

- `Verify Names` اضافه شد تا verification فقط محدود به SNI نباشد و با مدل‌های Edge/Fronting واقعی‌تر هماهنگ شود.
- `Copy Top N` اضافه شد تا بتوان فقط بهترین N آی‌پی را کپی کرد؛ مقدار 0 یعنی همه.
- مسیر اصلی در Simple Mode همچنان `IP + Front SNI + HTTP Host + Path` است.
- ابزارهای کم‌اهمیت مثل Upload/CIDR/Raw TCP tuning فقط در Advanced نمایش داده می‌شوند.

فایل توضیح فنی:

```text
STRATEGIC_CORE_UPGRADE_FA.md
```
