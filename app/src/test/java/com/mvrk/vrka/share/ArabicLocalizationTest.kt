package com.mvrk.vrka.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the Arabic-first requirement: the default resource set must be Arabic and
 * must not leak English user-facing labels.
 */
class ArabicLocalizationTest {

    private val stringsFile = File("src/main/res/values/strings.xml")

    private val strings: Map<String, String> by lazy {
        assertTrue("Missing ${stringsFile.absolutePath}", stringsFile.isFile)
        val text = stringsFile.readText(Charsets.UTF_8)
        val regex = Regex("""<string\s+name="([^"]+)"\s*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        regex.findAll(text).associate { match ->
            match.groupValues[1] to match.groupValues[2].trim()
        }
    }

    private val arabicRegex = Regex("[\\u0600-\\u06FF]")

    /** Values that are legitimately not Arabic (brands, codecs, units, product names). */
    private val technicalValues = setOf(
        "app_name", "app_full_name", "nav_settings_font_placeholder",
        "quick_title", "quick_size_exact",
        "quality_2160", "quality_1440", "quality_1080", "quality_720", "quality_480", "quality_360",
        "config_sponsorblock", "settings_browser_engine_subtitle", "settings_media_detection_subtitle",
        "quick_audio_opus", "quick_audio_opus_native", "quick_audio_mp3", "size_value_gb", "size_value_mb", "size_value_kb",
        "quick_share_label",
    )

    @Test
    fun applicationNameIsSnapVrka() {
        assertEquals("SnapVRKA", strings["app_name"])
    }

    @Test
    fun everyUserFacingStringIsArabic() {
        val offenders = strings.filterKeys { it !in technicalValues }
            .filterValues { value -> !arabicRegex.containsMatchIn(value) }
            .map { (key, value) -> "$key = $value" }
        assertTrue("Non-Arabic user facing strings: $offenders", offenders.isEmpty())
    }

    @Test
    fun forbiddenEnglishLabelsAreNotPresentAsWholeValues() {
        val forbidden = listOf(
            "Download", "Cancel", "Settings", "History", "Queue", "Retry", "Delete", "Open",
            "Share", "Failed", "Success", "Loading", "Error", "Audio", "Video", "Quality",
            "Format", "Size", "Best available", "Downloading", "Complete", "Queued",
        )
        val offenders = strings.filterValues { it.trim() in forbidden }.map { it.key }
        assertTrue("Untranslated values: $offenders", offenders.isEmpty())
    }

    @Test
    fun requiredLocalizedKeysExist() {
        val required = listOf(
            "app_name", "nav_download", "nav_queue", "nav_history", "nav_settings",
            "action_download", "action_cancel", "action_delete", "action_open", "action_share",
            "action_retry", "action_copy_link",
            "state_queued", "state_preparing", "state_downloading", "state_done", "state_failed",
            "state_cancelled", "state_waiting_for_user", "state_paused",
            "notification_action_pause", "notification_action_resume", "notification_completed_title",
            "quick_title", "quick_analysing", "quick_fetching", "quick_reading_formats",
            "quick_section_video", "quick_section_audio", "quick_download", "quick_no_formats",
            "quick_size_unknown", "quick_size_estimated", "quick_best_quality",
            "quick_video_quality", "quick_audio_quality", "quick_format", "quick_size",
            "settings_general", "settings_download", "settings_save_folder", "settings_video",
            "settings_audio", "settings_notifications", "settings_updates", "settings_browser",
            "settings_privacy", "settings_about",
            "about_title", "about_description", "about_version", "about_license",
            "update_title", "update_download", "update_install", "update_later",
            "notification_channel_name",
        )
        val missing = required.filterNot { strings.containsKey(it) }
        assertTrue("Missing keys: $missing", missing.isEmpty())
    }

    @Test
    fun technicalBrandNamesAreKeptVerbatim() {
        val joined = strings.values.joinToString("\n")
        listOf("yt-dlp", "FFmpeg", "GeckoView", "uBlock Origin", "Puemos", "HLS", "DASH", "GitHub", "MP3", "Opus")
            .forEach { brand -> assertTrue("Missing brand $brand", joined.contains(brand)) }
    }

    @Test
    fun noAccidentalEnglishPlaceholdersRemain() {
        val suspicious = listOf(
            "TODO", "FIXME", "Lorem ipsum", "placeholder", "Translate",
        )
        strings.forEach { (key, value) ->
            suspicious.forEach { token ->
                assertFalse("$key contains $token", value.contains(token, ignoreCase = true))
            }
        }
    }
}
