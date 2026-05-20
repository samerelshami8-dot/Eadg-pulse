# EdgePulse v1.5 - Strategic Core Upgrade

این نسخه بعد از بررسی عمیق ساختار فعلی و الگوی پروژه Shir o Khorshid/Psiphon انجام شد.

## نکته‌های الهام‌گرفته از Shir o Khorshid

در پروژه Shir o Khorshid، منطق CDN fronting فقط یک IP scanner نیست؛ چند نکته کلیدی دارد:

- Protocol selection جداگانه اضافه شده است: auto / conduit / cdn_fronting / direct
- CDN fronting فقط با IP خام تعریف نشده، بلکه با overrideهای Dial/SNI/Verify/ALPN کار می‌کند
- برای custom IPها محدودیت تعداد در نظر گرفته شده است
- SNI و IP ورودی validate می‌شوند
- برای Fronted Meek، ALPN و TLS profile هم بخشی از منطق هستند

## تغییرات استراتژیک EdgePulse v1.5

### 1) Verify Names

تا قبل از این، verification فقط روی SNI انجام می‌شد. اما در مدل‌های fronting/edge ممکن است verification واقعی با یک لیست نام معتبر انجام شود.

اضافه شد:

```text
Verify Names
```

اگر خالی باشد، اپ به‌صورت پیش‌فرض این‌ها را برای verification استفاده می‌کند:

```text
Front SNI
HTTP Host
```

اگر مقدار بدهی، با کاما/فاصله/خط جدید جدا می‌شود.

### 2) Copy Top N

اضافه شد:

```text
Copy Top N
```

اگر 0 باشد همه IPهای موفق کپی می‌شوند. اگر مثلاً 32 باشد فقط 32 نتیجه برتر کپی می‌شود.

این برای سناریوهایی که کلاینت مقصد تعداد محدودی IP custom قبول می‌کند مهم است.

### 3) Pair Mode همچنان مسیر اصلی است

در Simple Mode، مسیر اصلی همان است:

```text
IP + SNI + Host + Path
```

### 4) حذف/مخفی‌سازی چیزهای کم‌اهمیت

- Upload Test فقط در Advanced باقی ماند.
- CIDR Generator فقط در Advanced باقی ماند.
- تنظیمات خام TCP/TLS فقط در Advanced باقی ماند.

## نسخه

```text
EdgePulse v1.5.0
```
