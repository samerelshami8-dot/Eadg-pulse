# EdgePulse v1.4 - Simple / Advanced Mode

## Simple Mode

حالت ساده برای استفاده سریع طراحی شده و فقط موارد ضروری را نشان می‌دهد:

```text
IP List
Front SNI
HTTP Host
Test Path
Expected Status
Start / Stop
Results
Copy IPs
CSV
```

در Simple Mode اپ به‌صورت پیش‌فرض Pair Test را فعال نگه می‌دارد، چون هدف اصلی پیدا کردن ترکیب معتبر است، نه IP خام.

## Advanced Mode

با فعال کردن `Advanced Mode` تنظیمات کامل نمایش داده می‌شود:

```text
Port
Timeout
Concurrency
Attempts
Min Success
Scan Mode
Generic SNI
Download Speed Test
Upload Test
Expected Marker
Test KB
Rounds
CIDR/Range Generator
```

## دلیل این تغییر

در نسخه‌های قبلی UI بیش از حد شلوغ شده بود و کاربر ممکن بود به جای Pair Test اصلی، فقط TCP یا speed خام را تست کند. در نسخه 1.4 مسیر درست‌تر است:

```text
Simple = IP + SNI + Host + Path
Advanced = tuning و ابزارهای تکمیلی
```

## نکته کپی

دکمه Copy در هر دو حالت فقط IP خام کپی می‌کند.
