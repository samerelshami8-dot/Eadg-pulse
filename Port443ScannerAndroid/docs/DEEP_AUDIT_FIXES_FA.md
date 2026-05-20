# Deep Audit Fixes - EdgePulse v1.3.3

در بازبینی عمیق چند ایراد مهم پیدا و اصلاح شد.

## 1) باگ بزرگ منطقی Pair Mode

قبل از اصلاح، وقتی Pair/Fronting Test روشن بود، اپ اول از مسیر عمومی TCP/TLS عبور می‌کرد و فقط اگر آن مرحله موفق بود، Pair Test را انجام می‌داد.

مشکل:

```text
اگر Mode روی TLS بود و SNI عمومی خالی/اشتباه بود، یک ترکیب معتبر IP+FrontSNI+Host اصلاً تست نمی‌شد.
```

اصلاح:

```text
وقتی Pair Mode روشن است، مسیر عمومی bypass می‌شود و مستقیماً scanPairIp اجرا می‌شود.
```

یعنی معیار اصلی در این حالت فقط این است:

```text
IP + Front SNI + HTTP Host + Path
```

## 2) باگ سازگاری TLS Verification

در نسخه قبلی برای hostname verification از `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` استفاده شد. این روش روی بعضی نسخه‌های قدیمی‌تر اندروید می‌تواند مشکل سازگاری ایجاد کند.

اصلاح:

- SNI همچنان با `SSLParameters.setServerNames` تنظیم می‌شود.
- بعد از handshake، hostname با `HttpsURLConnection.getDefaultHostnameVerifier()` دستی verify می‌شود.
- اگر certificate برای SNI معتبر نباشد، خطای `hostname-verification-failed` برمی‌گردد.

## 3) باگ تست آپلود

قبل از اصلاح، تست آپلود فقط زمان `write/flush` را اندازه می‌گرفت. این می‌تواند سرعت غیرواقعی بدهد چون ممکن است فقط buffer محلی/سیستم‌عامل اندازه‌گیری شود، نه دریافت واقعی سرور.

اصلاح:

بعد از ارسال POST، اپ منتظر HTTP response header می‌ماند و فقط اگر status 2xx/3xx باشد upload را موفق حساب می‌کند.

## 4) Copy IP

دکمه Copy فقط IP خام را کپی می‌کند، حتی اگر Pair Mode روشن باشد.

## نسخه

```text
EdgePulse v1.3.3
```
