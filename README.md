# اسکنر سند — Android Document Scanner

[![Build APK](https://github.com/adad-bot/MyScan/actions/workflows/build.yml/badge.svg)](https://github.com/adad-bot/MyScan/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**APK:** از بخش [Actions](https://github.com/adad-bot/MyScan/actions) → آخرین بیلد موفق → Artifacts →
`app-debug-apk` را دانلود کنید، یا با `./gradlew :app:assembleDebug` خودتان بسازید.

اپلیکیشن اسکن اسناد برای اندروید با پردازش کاملاً محلی: ثبت با دوربین یا ورود از گالری و PDF،
تشخیص خودکار قاب و اصلاح پرسپکتیو، فیلتر تصویر، مدیریت چندصفحه‌ای، متن‌خوانی فارسی روی دستگاه
و خروجی PDF (تصویری و قابل جست‌وجو)، Word، JPG و TXT با کنترل سقف حجم.

ساخته‌شده با Kotlin و Jetpack Compose؛ بدون نیاز به حساب کاربری و بدون ارسال سند به سرور.

## امکانات

- **ثبت**: دوربین CameraX با کادر راهنما، فلش، شمارندهٔ صفحه و اسکن پیوسته؛ ورود چند عکس از گالری؛
  ورود PDF چندصفحه‌ای با رندر هر صفحه.
- **اصلاح قاب**: تشخیص خودکار کاغذ (آستانه‌گذاری Otsu، بزرگ‌ترین مؤلفهٔ متصل و برازش چهار خط)،
  اصلاح پرسپکتیو با نگاشت هشت‌پارامتری، و جابه‌جایی دستی چهار گوشه.
- **فیلترها**: بهبود (حذف سایه + کشش کنتراست + شارپ)، حذف سایه، خاکستری و سیاه‌وسفید با آستانهٔ محلی.
- **مدیریت سند**: انتخاب گروهی، چرخش، فیلتر گروهی، حذف، تغییر نام، آرشیو محلی.
- **الگوهای مدارک**: الگوی عمومی، مدارک ثبت‌نام و پروندهٔ حقوقی با کنترل کامل بودن مدارک.
- **OCR فارسی**: Tesseract4Android با مدل `fas` داخل APK، اجرای محلی، یکسان‌سازی «ی/ک» و ارقام،
  ویرایش متن در کنار تصویر.
- **خروجی**: PDF تصویری با سقف حجم، PDF قابل جست‌وجو، DOCX راست‌به‌چپ، JPG و TXT؛ ارسال با منوی
  اندروید و ذخیره با انتخاب‌گر فایل.

## گزارش محصول

- [گزارش کامل (DOCX)](docs/Android-Scanner-Report.docx)
- [گزارش کامل (PDF)](docs/Android-Scanner-Report.pdf)

## ساخت و نصب

پیش‌نیاز: JDK 17 و Android SDK با `platform 34` و `build-tools 34.0.0`.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

در ویندوز:

```bat
gradlew.bat :app:assembleDebug
```

یا پوشه را در Android Studio باز کنید و Run بزنید.

> وابستگی OCR از JitPack تأمین می‌شود:
> `cz.adaptech.tesseract4android:tesseract4android:4.9.0`

## ساختار پروژه

```
app/src/main/java/ir/scanfa/app/
├── data/      مدل سند، ذخیره‌سازی JSON، الگوها، تنظیمات
├── image/     تشخیص قاب، وارپ، فیلترها، ورود PDF، ابزار تصویر
├── ocr/       موتور Tesseract و یکسان‌سازی متن فارسی
├── export/    PDF تصویری، PDF جست‌وجوپذیر، DOCX، JPG/TXT، اشتراک‌گذاری
├── vm/        ViewModel و هماهنگی پردازش
├── ui/        صفحه‌های Compose (خانه، دوربین، ویرایش، سند، متن، خروجی، تنظیمات، الگو)
└── util/      تاریخ شمسی
```

## آزمون

```bash
./gradlew :app:testDebugUnitTest
```

آزمون‌ها ساخت DOCX (ساختار بسته، متن فارسی، تصویر) و PDF (هدر، جدول xref، جاسازی JPEG) را بررسی
می‌کنند. آزمون دوربین، دقت OCR و ارسال فایل نیازمند اجرای روی دستگاه است.

هر push به شاخهٔ `main` به‌صورت خودکار APK را با GitHub Actions می‌سازد
(workflow: [Build APK](.github/workflows/build.yml)).

## محدودیت‌ها

- OCR چیدمان چندستونه، جدول و فرمول را بازسازی نمی‌کند؛ کیفیت به نور و زاویه وابسته است.
- لایهٔ متن PDF قابل جست‌وجو مکان تقریبی سطرها را دارد و برای جست‌وجو ساخته شده، نه انتخاب دقیق کلمه.
- پوشاندن/حذف داده‌های حساس، ویرایش PDF و جست‌وجوی متن در آرشیو در نقشهٔ راه است.

## مجوز

[MIT](LICENSE)
