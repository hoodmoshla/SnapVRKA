package com.mvrk.vrka.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural checks over the manifest/build configuration: Share Sheet wiring, RTL,
 * application identity and the security posture required for SnapVRKA.
 */
class ShareIntentTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
    private val gradle = File("build.gradle.kts").readText(Charsets.UTF_8)

    @Test
    fun shareSheetDeclaresActionSendWithPlainText() {
        assertTrue(manifest.contains("android.intent.action.SEND"))
        assertTrue(manifest.contains("""android:mimeType="text/plain""""))
        assertTrue(manifest.contains("android.intent.category.DEFAULT"))
    }

    @Test
    fun shareSheetDeclaresActionViewForHttpAndHttps() {
        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("""android:scheme="http""""))
        assertTrue(manifest.contains("""android:scheme="https""""))
        assertTrue(manifest.contains("android:name=\".share.ShareActivity\""))
    }

    @Test
    fun shareActivityBypassesOverlayAndCleartextPermissions() {
        assertFalse(manifest.contains("SYSTEM_ALERT_WINDOW"))
        assertFalse(manifest.contains("""android:usesCleartextTraffic="true""""))
        assertTrue(manifest.contains("""android:usesCleartextTraffic="false""""))
        assertFalse(manifest.contains("android.permission.WRITE_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
    }

    @Test
    fun rtlIsEnabled() {
        assertTrue(manifest.contains("""android:supportsRtl="true""""))
    }

    @Test
    fun applicationIdentityMatchesSnapVrka() {
        assertTrue(gradle.contains("""applicationId = "com.hoodmoshla.snapvrka""""))
        assertTrue(gradle.contains("""versionName = "1.0.0""""))
        assertTrue(gradle.contains("versionCode = 10000"))
    }

    @Test
    fun launcherAndServiceAreDeclared() {
        assertTrue(manifest.contains("android.intent.category.LAUNCHER"))
        assertTrue(manifest.contains("android:name=\".DownloadService\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
        assertTrue(manifest.contains("android:name=\".MainActivity\""))
    }

    @Test
    fun notificationPermissionDoesNotGateDownloads() {
        // POST_NOTIFICATIONS is declared but the share activity never blocks on it.
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
        val shareActivity = File("src/main/java/com/mvrk/vrka/share/ShareActivity.kt")
            .readText(Charsets.UTF_8)
        assertFalse(shareActivity.contains("POST_NOTIFICATIONS"))
        assertFalse(shareActivity.contains("requestPermission"))
    }

    @Test
    fun notificationChannelUsesSnapVrkaIdentifier() {
        val service = File("src/main/java/com/mvrk/vrka/DownloadService.kt").readText(Charsets.UTF_8)
        assertTrue(service.contains("snapvrka_downloads"))
    }
}
