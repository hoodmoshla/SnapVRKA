package com.mvrk.vrka

import androidx.compose.runtime.Immutable

enum class JobState {
    QUEUED,
    PREPARING,
    WAITING_FOR_USER,
    BROWSER_FALLBACK,
    DOWNLOADING,
    PAUSED,
    POSTPROCESSING,
    DONE,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == DONE || this == FAILED || this == CANCELLED

    val isActive: Boolean
        get() = !isTerminal

    val isForegroundWork: Boolean
        get() = this == QUEUED || this == PREPARING || this == DOWNLOADING || this == PAUSED || this == POSTPROCESSING || this == BROWSER_FALLBACK

    val label: String
        get() = when (this) {
            QUEUED -> SnapVrkaArabic.text("Queued")
            PREPARING -> SnapVrkaArabic.text("Preparing")
            WAITING_FOR_USER -> SnapVrkaArabic.text("Waiting for you")
            BROWSER_FALLBACK -> SnapVrkaArabic.text("Browser fallback")
            DOWNLOADING -> SnapVrkaArabic.text("Downloading")
            PAUSED -> SnapVrkaArabic.text("Paused")
            POSTPROCESSING -> SnapVrkaArabic.text("Finishing")
            DONE -> SnapVrkaArabic.text("Complete")
            FAILED -> SnapVrkaArabic.text("Failed")
            CANCELLED -> SnapVrkaArabic.text("Cancelled")
        }

    val labelRes: Int
        get() = when (this) {
            QUEUED -> R.string.state_queued
            PREPARING -> R.string.state_preparing
            WAITING_FOR_USER -> R.string.state_waiting_for_user
            BROWSER_FALLBACK -> R.string.state_browser_fallback
            DOWNLOADING -> R.string.state_downloading
            PAUSED -> R.string.state_paused
            POSTPROCESSING -> R.string.state_postprocessing
            DONE -> R.string.state_done
            FAILED -> R.string.state_failed
            CANCELLED -> R.string.state_cancelled
        }
}

enum class MediaMode(val label: String) {
    VIDEO(SnapVrkaArabic.text("Video")),
    AUDIO(SnapVrkaArabic.text("Audio")),
}

enum class VideoQuality(val label: String, val height: Int?) {
    BEST(SnapVrkaArabic.text("Best available"), null),
    P2160("2160p", 2160),
    P1440("1440p", 1440),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360),
}

enum class AudioFormat(val label: String, val codec: String) {
    MP3(SnapVrkaArabic.text("MP3 (compressed)"), "mp3"),
    OPUS(SnapVrkaArabic.text("Opus (prefer native)"), "opus"),
    WAV(SnapVrkaArabic.text("WAV (uncompressed)"), "wav"),
}

enum class SaveLocationMode(val label: String) {
    REMEMBER_LOCATION(SnapVrkaArabic.text("Use selected")),
    ASK_EVERY_TIME(SnapVrkaArabic.text("Ask every time")),
}

enum class FontPreference(val label: String) {
    VRKA_FONT(SnapVrkaArabic.text("VRKA Font")),
    SYSTEM_FONT(SnapVrkaArabic.text("System Font")),
}

enum class ThemeMode(val label: String) {
    AUTO(SnapVrkaArabic.text("Auto")),
    LIGHT(SnapVrkaArabic.text("Light")),
    DARK(SnapVrkaArabic.text("Dark")),
    ;

    val labelRes: Int
        get() = when (this) {
            AUTO -> R.string.theme_auto
            LIGHT -> R.string.theme_light
            DARK -> R.string.theme_dark
        }

    companion object {
        fun fromStorage(value: String?): ThemeMode = when (value?.trim()?.uppercase()) {
            "LIGHT" -> LIGHT
            "DARK", "AMOLED" -> DARK
            "AUTO", null, "" -> AUTO
            else -> AUTO
        }
    }
}

fun resolveDarkMode(mode: ThemeMode, systemInDarkTheme: Boolean): Boolean = when (mode) {
    ThemeMode.AUTO -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

enum class UpdatePreference(val label: String) {
    STABLE(SnapVrkaArabic.text("Stable")),
    NIGHTLY(SnapVrkaArabic.text("Nightly")),
}

@Immutable
data class DownloadRequest(
    val url: String,
    val mode: MediaMode = MediaMode.VIDEO,
    val quality: VideoQuality = VideoQuality.BEST,
    val prefer60Fps: Boolean = false,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    val mp3Bitrate: Int = 320,
    val isPlaylist: Boolean = false,
    val playlistStart: Int? = null,
    val playlistEnd: Int? = null,
    val downloadSubtitles: Boolean = false,
    val automaticCaptions: Boolean = true,
    val embedSubtitles: Boolean = true,
    val subtitleLanguages: String = "en.*",
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = true,
    val sponsorBlock: Boolean = false,
    val sponsorCategories: String = "sponsor,selfpromo,interaction",
    val trimStart: String = "",
    val trimEnd: String = "",
    val customArguments: List<String> = emptyList(),
    val formatSelector: String? = null,
    val commandTemplateId: String? = null,
    val commandTemplateName: String = "",
    val commandTemplateArguments: List<String> = emptyList(),
    val h264CompatibilityMode: Boolean = false,
    val referer: String = "",
    val origin: String = "",
    val customHeaders: Map<String, String> = emptyMap(),
    val destinationTreeUri: String? = null,
    val resolvedMediaUrl: String? = null,
    val resolvedHeaders: Map<String, String> = emptyMap(),
    val resolvedHeadersOrigin: String = "",
)

@Immutable
data class DownloadJob(
    val id: String,
    val request: DownloadRequest,
    val state: JobState = JobState.QUEUED,
    val title: String = "",
    val detail: String = "Waiting",
    val progress: Float = 0f,
    val speed: String = "",
    val etaSeconds: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val outputUris: List<String> = emptyList(),
    val error: String = "",
    val attempt: Int = 1,
    val actualResolution: String? = null,
    val subtitleStatus: com.mvrk.vrka.subtitle.SubtitleStatus = com.mvrk.vrka.subtitle.SubtitleStatus.NONE,
)

enum class StreamSelectionState {
    IDLE,
    RESOLVING,
    RESOLVED,
    AWAITING_CONFIRMATION,
    QUEUED,
    STARTING,
    DOWNLOADING,
    MERGING,
    COMPLETED,
    FAILED,
    CANCELLED;
}

@Immutable
data class RuntimeStatus(
    val initialized: Boolean = false,
    val busy: Boolean = false,
    val version: String = "",
    val message: String = "Runtime loads only when needed",
)
