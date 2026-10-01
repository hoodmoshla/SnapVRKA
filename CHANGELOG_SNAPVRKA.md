# Changelog — SnapVRKA

## [1.0.0] — إصلاحات ما بعد الاختبار الفعلي

### أُصلح
- **نافذة Quick Download**: أصبحت نافذة شفافة بكامل الشاشة (`windowIsFloating=false`) بدل نافذة
  عائمة؛ فالنافذة العائمة كانت تضع المحتوى في منتصف الشاشة ويظهر مؤشر السحب في غير مكانه.
  أُزيل شريط السحب الزخرفي نهائيًا، ويظهر التطبيق السابق خلف النافذة بشكل طبيعي.
- **العودة للتطبيق السابق**: `ShareActivity` الآن في مهمة مستقلة (`taskAffinity=""` +
  `launchMode="standard"` + `noHistory`) وتُغلق فورًا بعد إنشاء المهمة، بدون أي تأخير مصطنع
  أو فتح يدوي للتطبيق المستضيف. لم يعد `MainActivity` يُفتح ولا يُجلب SnapVRKA للواجهة.
- **إشعار التنزيل**: طلب صلاحية `POST_NOTIFICATIONS` (غير حاجب) من `MainActivity` و
  `ShareActivity`، وتشغيل `DownloadService` من `ShareActivity` أثناء وجودها في المقدمة
  لضمان بدء Foreground Service وظهور إشعار التقدّم مع استمرار التنزيل بعد الإغلاق.
- **اختيار الجودات في الشاشة الرئيسية**: أصبح التدفق
  لصق الرابط → تحليل الرابط → الجودات الفعلية والأحجام → اختيار → تنزيل.
  أُلغيت قائمة الدقات الثابتة؛ لا تُعرض أي دقة غير موجودة فعليًا.

### أُضيف
- **نظام مظهر كامل**: تلقائي (افتراضي) / فاتح / داكن، محفوظ بشكل دائم، ويُطبَّق على كل الواجهات
  بما فيها Quick Download وشاشة المشاركة، مع `resolveDarkMode` قابل للاختبار.
- `QuickDownloadAnalyzer` لتوحيد منطق حالات التحليل بين Quick Share والشاشة الرئيسية.
- `QualityChoiceList` كمكوّن مشترك لعرض الجودات والأحجام في المكانين.
- خلفية نافذة تتبع النظام (`values-night`) لمنع الوميض الأسود في الوضع الفاتح.

### اختبارات
- أُضيفت: `ThemeModeTest`, `ShareLifecycleTest`, `HomeQualityFlowTest` (331 اختبارًا إجمالًا).

## [1.0.0] — أول إصدار

### أُضيف
- هوية تطبيق جديدة: **SnapVRKA** مع `applicationId = com.hoodmoshla.snapvrka`.
- واجهة عربية بالكامل مع دعم RTL في كل الشاشات والحوارات.
- `ShareActivity` تظهر في Android Share Sheet لدعم `ACTION_SEND` و`ACTION_VIEW`.
- نافذة **Quick Download** سفلية (ثيم شفاف، بدون صلاحية overlay) تعرض العنوان والصورة المصغرة.
- `MediaFormatProbe` لقراءة بيانات yt-dlp وتحويلها إلى `MediaInfo` / `MediaFormat`.
- اختيار جودة الفيديو مع عرض الجودات الموجودة فقط وأفضل صيغة لكل دقة.
- عرض الحجم الحقيقي أو التقريبي («حوالي … ميجابايت») بدون أرقام وهمية.
- تنزيل الصوت فقط: MP3 320 / 192 / 128 وOpus مع تفضيل البث الأصلي.
- اختبارات وحدة للمشاركة، التحليل، الأحجام، اختيار الجودة، التحديث، والتعريب.
- سير عمل GitHub Actions للاختبارات وlint وبناء وتوقيع ورفع APK.

### تغيّر
- `AppUpdateManager` يشير الآن إلى `https://api.github.com/repos/hoodmoshla/SnapVRKA/releases/latest`
  ويقبل فقط ملفات `SnapVRKA-vX.Y.Z.apk` (يُرفض `VRKA-Android-v*.apk`).
- اسم مجلد الحفظ الافتراضي أصبح `Downloads/SnapVRKA`.
- اسم قناة الإشعارات أصبح `snapvrka_downloads`.

### حُوفظ عليه
- محرك VRKA الحالي بالكامل: `VrkaDownloadManager`، `DownloadRequestFactory`، `DownloadService`،
  `JobStore`، `OutputPublisher`، `FallbackEngine`، `CandidateStore`، `EngineCandidateRanker`،
  `GeckoMediaBridge`، `GeckoWebExecutorTransport`، دعم HLS/DASH وقائمة الانتظار والسجل.
- أنظمة تحديث yt-dlp وuBlock Origin وPuemos مع التحقق من السلامة.
