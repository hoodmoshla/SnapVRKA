# Changelog — SnapVRKA

## [1.0.0] - أول إصدار

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
