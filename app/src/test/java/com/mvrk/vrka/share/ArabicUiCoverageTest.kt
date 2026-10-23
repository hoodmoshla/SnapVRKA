package com.mvrk.vrka.share

import com.mvrk.vrka.SnapVrkaArabic
import com.mvrk.vrka.update.SemanticVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Proves the rewritten VRKA 5 interface is actually translated, not merely that
 * an unused Arabic dictionary exists.
 */
class ArabicUiCoverageTest {
    private val arabic = Regex("[\\u0600-\\u06FF]")
    private val moduleRoot = File("src/main")
    private val visibleUiFiles = listOf(
        "java/com/mvrk/vrka/HomeScreen.kt",
        "java/com/mvrk/vrka/JobsScreen.kt",
        "java/com/mvrk/vrka/SettingsScreen.kt",
        "java/com/mvrk/vrka/VrkaRoot.kt",
        "java/com/mvrk/vrka/ui/AppUpdateDialog.kt",
        "java/com/mvrk/vrka/share/QuickDownloadScreen.kt",
    )

    @Test
    fun newEngineLabelsAreTranslatedNotLeftEnglish() {
        val samples = listOf(
            "Add to Queue",
            "Download Location",
            "Concurrent Downloads",
            "Settings",
            "Queue is empty",
            "Check for Updates",
            "Update Available",
            "Download Update",
            "Clear Browser Data?",
            "Couldn’t reach this site",
            "Enter a complete http or https URL.",
            "Best available",
            "Browser Fallback",
            "Minimize",
            "VIDEO RESOLUTION",
        )
        samples.forEach { english ->
            val translated = SnapVrkaArabic.text(english)
            assertTrue("'$english' was not translated: '$translated'", arabic.containsMatchIn(translated))
            assertFalse(translated.contains(english))
        }
    }

    @Test
    fun templatedDialogsKeepNumbersWhileTranslatingTheSentence() {
        val translated = SnapVrkaArabic.text(
            "Remove 3 records from download history? Downloaded files on storage will remain untouched.",
        )
        assertTrue(translated.contains("3"))
        assertTrue(arabic.containsMatchIn(translated))
        assertFalse(translated.contains("download history"))
    }

    @Test
    fun technicalCommandsAndUrlsAreNotRewritten() {
        val command = "--no-playlist --embed-metadata"
        assertEquals(command, SnapVrkaArabic.text(command))
        val url = "https://example.com/Video"
        assertEquals(url, SnapVrkaArabic.text(url))
        assertTrue(SnapVrkaArabic.text("yt-dlp arguments").contains("yt-dlp"))
    }

    @Test
    fun arabicResourcesAndRtlArePresentInTheReleaseSource() {
        val strings = File(moduleRoot, "res/values/strings.xml")
        val arabicStrings = File(moduleRoot, "res/values-ar/strings.xml")
        val arabicUi = File(moduleRoot, "res/values-ar/ui_phrases.xml")
        val manifest = File(moduleRoot, "AndroidManifest.xml")
        val theme = File(moduleRoot, "java/com/mvrk/vrka/Theme.kt")
        val font = File(moduleRoot, "res/font/noto_naskh_arabic_regular.ttf")
        assertTrue(strings.isFile)
        assertTrue(arabic.containsMatchIn(strings.readText()))
        assertTrue(arabicStrings.isFile && arabic.containsMatchIn(arabicStrings.readText()))
        assertTrue(arabicUi.isFile && arabic.containsMatchIn(arabicUi.readText()))
        assertTrue(arabicUi.readText().contains("إضافة إلى الطابور") || arabicUi.readText().contains("إضافة إلى قائمة الانتظار"))
        assertTrue(strings.readText().contains("تنزيل باستخدام SnapVRKA"))
        assertTrue(manifest.readText().contains("android:supportsRtl=\"true\""))
        assertTrue(theme.readText().contains("LayoutDirection.Rtl"))
        assertTrue(theme.readText().contains("ArabicUi"))
        assertTrue("Arabic font missing from release resources", font.isFile && font.length() > 10_000)
    }

    @Test
    fun visibleScreensCannotBypassTheArabicTextWrapper() {
        visibleUiFiles.forEach { relative ->
            val source = File(moduleRoot, relative).readText()
            assertFalse(
                "$relative imports Material Text and can bypass translation",
                Regex("""import\s+androidx\.compose\.material3\.Text\b""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun fourPartUpdateVersionIsNewerThanPublishedRelease() {
        val published = SemanticVersion.parseOrNull("5.0.2.1")
        val fixed = SemanticVersion.parseOrNull("5.0.2.2")
        assertEquals(SemanticVersion(5, 0, 2, 1), published)
        assertEquals(SemanticVersion(5, 0, 2, 2), fixed)
        assertTrue(fixed!!.isNewerThan(published!!))
    }
}
