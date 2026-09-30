# SnapVRKA

تطبيق Android لتنزيل الفيديو والصوت باستخدام yt-dlp وFFmpeg، مع مشاركة سريعة واختيار الجودة وتنزيل الصوت فقط، بالإضافة إلى Browser Fallback للمحتوى الذي يحتاج إلى تحليل عبر المتصفح.

> SnapVRKA مبني على محرك VRKA الأصلي (GPL-3.0) مع الحفاظ على كل أنظمة التنزيل القوية الموجودة، وإضافة واجهة عربية كاملة وRTL ومشاركة سريعة.

---

## المزايا

- **واجهة عربية بالكامل** مع دعم RTL وتخطيط mirrored صحيح.
- **المشاركة السريعة** من أي تطبيق عبر Android Share Sheet (`ACTION_SEND`) أو فتح الروابط (`ACTION_VIEW`).
- **Quick Download Popup**: نافذة سفلية سريعة تُظهر العنوان والصورة المصغرة والجودات بدون فتح الشاشة الرئيسية.
- **اختيار الجودة**: تُعرض الجودات الموجودة فعليًا فقط (2160p / 1440p / 1080p / 720p / 480p …).
- **عرض الحجم الحقيقي/التقريبي**: يُستخدم `filesize` ثم `filesize_approx` ثم تقدير من bitrate × المدة، ويظهر التقدير دائمًا بصيغة «حوالي … ميجابايت».
- **تنزيل الصوت فقط**: MP3 320 / MP3 192 / MP3 128 / Opus مع تفضيل نسخ بث Opus الأصلي عند توفره.
- **yt-dlp** كمحرك التنزيل الأساسي مع تحديث ذاتي وتحقق من السلامة.
- **FFmpeg** للدمج والتحويل واستخراج الصوت.
- **GeckoView** كمحرك متصفح احتياطي (Browser Fallback) عند فشل الاستخراج المباشر.
- **uBlock Origin** و**Puemos** لتقليل الإعلانات واكتشاف بث HLS/DASH.
- **دعم HLS و DASH** عبر Gecko transport مع التنزيل المتوازي للمقاطع.
- **قائمة انتظار (Queue)** بمهمة نشطة واحدة في كل مرة.
- **Background Downloads** عبر Foreground Service وإشعارات الحالة.
- **History** كامل مع إعادة المحاولة والفتح والمشاركة والحذف.
- **Auto Updates**: تحديثات التطبيق من إصدارات GitHub الرسمية لـ SnapVRKA مع تحقق HTTPS ومطابقة اسم حزمة APK.

---

## البنية

| المسار | الوصف |
| --- | --- |
| `app/src/main/java/com/mvrk/vrka/VrkaDownloadManager.kt` | مدير التنزيل (قائمة الانتظار، المحاولات، التصنيف، fallback) |
| `app/src/main/java/com/mvrk/vrka/DownloadRequestFactory.kt` | بناء أوامر yt-dlp |
| `app/src/main/java/com/mvrk/vrka/DownloadService.kt` | Foreground Service والإشعارات |
| `app/src/main/java/com/mvrk/vrka/JobStore.kt` | تخزين المهام ذريًا |
| `app/src/main/java/com/mvrk/vrka/OutputPublisher.kt` | نشر الملفات إلى مجلد الحفظ |
| `app/src/main/java/com/mvrk/vrka/engine/` | محرك fallback، ترتيب المرشحين، تجميع الوسائط، نقل Gecko |
| `app/src/main/java/com/mvrk/vrka/share/` | Share Sheet وQuick Download وMediaFormatProbe |
| `app/src/main/java/com/mvrk/vrka/update/` | تحديث التطبيق من GitHub Releases |
| `app/src/main/java/com/mvrk/vrka/ComponentUpdateManager.kt` | تحديث yt-dlp وuBlock Origin وPuemos |

### تدفق Quick Download

1. يشارك المستخدم رابطًا أو يفتحه → يستقبله `ShareActivity`.
2. يستخرج `ShareUrlParser` رابط `http`/`https` فقط (تُرفض أي scheme أخرى).
3. يقوم `MediaFormatProbe` بتشغيل yt-dlp لقراءة البيانات ويحوّلها إلى `MediaInfo` مُصنَّف.
4. يقوم `QuickDownloadPlanner` ببناء صفوف الجودات والأحجام.
5. عند الضغط على «تنزيل» يُنشأ `DownloadRequest` ويُمرَّر إلى `VrkaDownloadManager.enqueue()` (نفس المسار الموجود، بدون أي Downloader جديد).
6. يستمر التنزيل في الخلفية بعد إغلاق النافذة.

---

## البناء

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintVitalRelease
./gradlew :app:assembleRelease
```

### التوقيع

لا يُخزَّن أي keystore داخل المستودع. يستخدم البناء ملفًا خارجيًا عبر:

- متغير البيئة `VRKA_SIGNING_PROPERTIES`، أو
- `signing.properties` في جذر المشروع، أو
- `~/.vrka-android-signing/signing.properties`.

في GitHub Actions تُقرأ المفاتيح من الأسرار التالية:

- `SNAPVRKA_RELEASE_KEYSTORE_B64`
- `SNAPVRKA_RELEASE_STORE_PASSWORD`
- `SNAPVRKA_RELEASE_KEY_ALIAS`
- `SNAPVRKA_RELEASE_KEY_PASSWORD`

اسم الناتج ثابت: `SnapVRKA-v1.0.0.apk` (ولاحقًا `SnapVRKA-v1.0.1.apk`، `SnapVRKA-v1.1.0.apk` …) مع ملف `SnapVRKA-v1.0.0.apk.sha256`.

---

## الهوية

| الحقل | القيمة |
| --- | --- |
| اسم التطبيق | SnapVRKA |
| applicationId | `com.hoodmoshla.snapvrka` |
| namespace الداخلي | `com.mvrk.vrka` (لم يُغيَّر لتقليل مخاطر كسر المشروع) |
| versionName | `1.0.0` |
| versionCode | `10000` |
| minSdk | 26 |
| targetSdk | 36 |
| ABI | `arm64-v8a` |

---

## الخصوصية والأمان

- لا تُسجَّل ملفات تعريف الارتباط أو تراخيص الدخول أو الروابط الموقّعة في تقارير التشخيص.
- لا تُستخدم صلاحية `SYSTEM_ALERT_WINDOW` ولا تُطلب صلاحية overlay.
- `usesCleartextTraffic="false"` دائمًا.
- تحديثات التطبيق تتحقق من HTTPS، ونطاقات GitHub المعتمدة، وإعادة التوجيه، وحجم الملف، واسم حزمة APK.
- نظام تحديث yt-dlp يحافظ على التحقق من SHA-256 وتوقيع OpenPGP والاستبدال الذري.

---

## المواقع المدعومة

التطبيق يعتمد على yt-dlp، ولذلك يعمل مع عدد كبير من المواقع مثل:

YouTube · X / Twitter · Facebook · TikTok · Instagram · وغيرها من المواقع التي يدعمها yt-dlp.

> **ملاحظة مهمة:** لا يمكن ضمان عمل كل المواقع دائمًا. تعتمد النتيجة على نوع الرابط وتغيّرات الموقع وسياسات المحتوى. بعض المواقع قد تتطلب تسجيل دخول أو تمنع التنزيل أصلًا، وقد يحتاج المحتوى إلى Browser Fallback.

---

## الترخيص والحقوق

هذا المشروع مشتق من **VRKA-Android** الأصلي ويعيد استخدام محرك التنزيل الخاص به.

- الترخيص: **GPL-3.0** (انظر `LICENSE`).
- إشعارات الأطراف الثالثة: انظر `THIRD_PARTY_NOTICES.md`.
- حقوق المشروع الأصلي محفوظة لأصحابها.
