# برنامه منطقی ارتقا برای انتخاب Edge/IP سریع‌تر و معتبرتر

> این برنامه برای دامنه/CDNهایی است که مالک یا مجاز به تست آن‌ها هستید. استفاده از دامنه‌ها یا زیرساخت دیگران برای Domain Fronting یا دور زدن محدودیت‌ها می‌تواند خلاف قانون/شرایط سرویس باشد.

## مشکل فعلی

باز بودن TCP:443 به تنهایی کافی نیست. حتی سرعت دانلود ساده هم اگر با Host/SNI درست انجام نشود، برای CDN قابل اعتماد نیست.

## Pipeline پیشنهادی حرفه‌ای

### 1) تولید کاندیدا

- ورودی دستی IP/CIDR/Range
- Resolve دامنه خودتان از چند DNS/DoH مختلف
- لیست رسمی IP Range همان CDN
- حذف private/reserved/bogon
- dedupe و مرتب‌سازی عددی

### 2) فیلتر اولیه سریع

- TCP Connect روی پورت انتخابی
- ثبت connect time
- چند تلاش با فاصله کوتاه
- حذف timeout/refused

### 3) اعتبارسنجی TLS/CDN

- اتصال به IP ولی با SNI دامنه خودتان
- بررسی TLS handshake
- بررسی ALPN: http/1.1، h2، در آینده h3
- استخراج/بررسی certificate subject/SAN اختیاری
- تشخیص خطاهای مهم مثل 421 Misdirected، 525، 526، reset، timeout

### 4) اعتبارسنجی HTTP واقعی

- ارسال GET با Host دامنه خودتان
- مسیر تست مشخص مثل `/speedtest.bin` یا `/health.txt`
- بررسی status code قابل قبول: 200، 206، 301/302 کنترل‌شده، 403 قابل انتظار
- بررسی headerهای CDN مثل server/via/cache/pop/ray بسته به CDN
- بررسی fingerprint پاسخ: content-length، hash کوتاه، marker داخل فایل

### 5) تست سرعت چندمرحله‌ای

- مرحله سریع: دانلود 256KB یا 512KB برای همه IPهای معتبر
- مرحله دقیق: فقط Top N را با 2MB تا 10MB تست کن
- محاسبه:
  - TTFB
  - throughput Mbps
  - پایداری چند تلاش
  - خطا/قطع وسط دانلود
- جلوگیری از bias:
  - اگر هدف edge cache است، فایل باید cacheable باشد
  - اگر هدف مسیر origin است، query cache-busting اختیاری اضافه شود

### 6) تست آپلود

- فقط با endpoint مجاز خودتان
- برای CDNها آپلود معمولاً به origin می‌رود و همیشه معیار خوبی برای Edge نیست
- آپلود باید اختیاری و محدود باشد

### 7) امتیازدهی

نمونه مدل امتیاز:

```text
score =
  success_rate * 1000
  + download_mbps * 20
  + upload_mbps * 5
  - ttfb_ms * 0.8
  - connect_ms * 0.3
  - error_penalty
  - instability_penalty
```

### 8) پایداری و تکرار

- تست در چند round
- ذخیره history
- تشخیص IPهایی که یک‌بار سریع و چند بار کند هستند
- median/p90 به جای average ساده

### 9) قابلیت‌های پیشرفته آینده

- DoH داخلی برای resolve چند DNS
- تست HTTP/2 واقعی
- تست HTTP/3/QUIC با Cronet
- تشخیص ASN/ISP/region
- export کامل JSON/CSV
- پروفایل تست: سریع، متعادل، دقیق
- تست top-N دوباره با حجم بزرگ‌تر
- Pause/Resume واقعی با ذخیره queue

## جمع‌بندی

برای انتخاب IP بهتر CDN باید از «باز بودن پورت» عبور کرد و به «اعتبارسنجی SNI/Host + پاسخ HTTP + سرعت واقعی + پایداری» رسید. نسخه فعلی پایه این مسیر را دارد، اما برای سطح حرفه‌ای باید pipeline چندمرحله‌ای و scoring اضافه شود.
