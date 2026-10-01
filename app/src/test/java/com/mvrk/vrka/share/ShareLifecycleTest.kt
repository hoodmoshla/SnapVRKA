package com.mvrk.vrka.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression guards for the Quick Download window / lifecycle fixes:
 *
 *  - the sheet is a full-screen translucent window (a floating window laid the content out in the
 *    middle of the screen),
 *  - ShareActivity lives in its own task so finishing returns to the sharing app,
 *  - the download is handed to the existing manager + foreground service and keeps running.
 */
class ShareLifecycleTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
    private val shareActivity =
        File("src/main/java/com/mvrk/vrka/share/ShareActivity.kt").readText(Charsets.UTF_8)
    private val themes = File("src/main/res/values/themes.xml").readText(Charsets.UTF_8)
    private val manager =
        File("src/main/java/com/mvrk/vrka/VrkaDownloadManager.kt").readText(Charsets.UTF_8)
    private val service =
        File("src/main/java/com/mvrk/vrka/DownloadService.kt").readText(Charsets.UTF_8)

    // ---------------------------------------------------------------- window / theme

    @Test
    fun shareSheetThemeIsTranslucentAndNotFloating() {
        assertTrue(themes.contains("Theme.SnapVRKA.ShareSheet"))
        assertTrue(themes.contains("android:windowIsTranslucent\">true"))
        // A floating window is what pushed the sheet into the middle of the screen.
        assertTrue(themes.contains("android:windowIsFloating\">false"))
    }

    @Test
    fun shareSheetHasNoDragHandle() {
        val screen = File("src/main/java/com/mvrk/vrka/share/QuickDownloadScreen.kt")
            .readText(Charsets.UTF_8)
        // The decorative grabber bar must not come back.
        assertFalse(screen.contains("42.dp"))
        assertFalse(screen.contains("\"drag handle\""))
    }

    @Test
    fun shareActivityUsesTheNonFloatingTheme() {
        assertTrue(manifest.contains("android:theme=\"@style/Theme.SnapVRKA.ShareSheet\""))
    }

    // ---------------------------------------------------------------- task isolation

    @Test
    fun shareActivityHasItsOwnTaskSoFinishReturnsToTheHostApp() {
        assertTrue(manifest.contains("android:taskAffinity=\"\""))
        assertTrue(manifest.contains("android:launchMode=\"standard\""))
        assertTrue(manifest.contains("android:noHistory=\"true\""))
        assertTrue(manifest.contains("android:excludeFromRecents=\"true\""))
    }

    @Test
    fun shareActivityNeverOpensSnapVrkaHome() {
        assertFalse(shareActivity.contains("MainActivity"))
        assertFalse(shareActivity.contains("startActivity"))
        assertFalse(shareActivity.contains("Intent(context, "))
    }

    @Test
    fun shareActivityClosesWithoutArtificialDelay() {
        // No "wait then finish" workaround: enqueue is synchronous and the sheet closes at once.
        assertFalse(shareActivity.contains("postDelayed"))
        assertFalse(shareActivity.contains("Handler"))
        assertTrue(shareActivity.contains("finish()"))
    }

    // ---------------------------------------------------------------- download task

    @Test
    fun quickShareHandsTheJobToTheExistingDownloadPipeline() {
        // ShareActivity must use the shared manager and start the existing foreground service.
        assertTrue(shareActivity.contains("manager.enqueue(request)"))
        assertTrue(shareActivity.contains("DownloadService.start(this)"))
        // ... and it must not build a downloader of its own.
        assertFalse(shareActivity.contains("YoutubeDL"))
        assertFalse(shareActivity.contains("DownloadExecution"))
    }

    @Test
    fun downloadServiceKeepsRunningIndependentlyOfTheSheet() {
        // The service is declared stopWithTask=false and the queue lives in the application scope,
        // so a finished ShareActivity cannot stop an in-flight download.
        assertTrue(manifest.contains("android:stopWithTask=\"false\""))
        assertTrue(manager.contains("DownloadService.start(context)"))
        assertTrue(manager.contains("DownloadService.stopIfIdle(context)"))
        assertTrue(service.contains("startForeground("))
        assertTrue(service.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC"))
    }

    @Test
    fun notificationChannelAndProgressNotificationExist() {
        assertTrue(service.contains("snapvrka_downloads"))
        assertTrue(service.contains("createNotificationChannel"))
        assertTrue(service.contains("NotificationCompat.Builder"))
        assertTrue(service.contains("setProgress("))
    }

    @Test
    fun notificationPermissionIsOptional() {
        assertTrue(NotificationPermission.shouldRequest(33, false))
        assertFalse(NotificationPermission.shouldRequest(33, true))
        assertFalse(NotificationPermission.shouldRequest(32, false))
    }
}
