package com.mvrk.vrka

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

enum class JobState {
    QUEUED,
    PREPARING,
    WAITING_FOR_USER,
    BROWSER_FALLBACK,
    DOWNLOADING,
    POSTPROCESSING,
    DONE,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == DONE || this == FAILED || this == CANCELLED

    val isForegroundWork: Boolean
        get() = this == QUEUED || this == PREPARING || this == DOWNLOADING || this == POSTPROCESSING || this == BROWSER_FALLBACK

    @get:StringRes
    val labelRes: Int
        get() = when (this) {
            QUEUED -> R.string.state_queued
            PREPARING -> R.string.state_preparing
            WAITING_FOR_USER -> R.string.state_waiting_for_user
            BROWSER_FALLBACK -> R.string.state_browser_fallback
            DOWNLOADING -> R.string.state_downloading
            POSTPROCESSING -> R.string.state_postprocessing
            DONE -> R.string.state_done
            FAILED -> R.string.state_failed
            CANCELLED -> R.string.state_cancelled
        }
}

enum class MediaMode(@get:StringRes val labelRes: Int) {
    VIDEO(R.string.mode_video),
    AUDIO(R.string.mode_audio),
}

enum class VideoQuality(@get:StringRes val labelRes: Int, val height: Int?) {
    BEST(R.string.quality_best_available, null),
    P2160(R.string.quality_2160, 2160),
    P1440(R.string.quality_1440, 1440),
    P1080(R.string.quality_1080, 1080),
    P720(R.string.quality_720, 720),
    P480(R.string.quality_480, 480),
    P360(R.string.quality_360, 360),
}

enum class AudioFormat(@get:StringRes val labelRes: Int, val codec: String) {
    MP3(R.string.audio_format_mp3, "mp3"),
    OPUS(R.string.audio_format_opus, "opus"),
    WAV(R.string.audio_format_wav, "wav"),
}

enum class SaveLocationMode(@get:StringRes val labelRes: Int) {
    REMEMBER_LOCATION(R.string.settings_save_use_selected),
    ASK_EVERY_TIME(R.string.settings_save_ask),
}

enum class FontPreference(@get:StringRes val labelRes: Int) {
    VRKA_FONT(R.string.settings_font_vrka),
    SYSTEM_FONT(R.string.settings_font_system),
}

enum class ThemeMode(@get:StringRes val labelRes: Int) {
    LIGHT(R.string.settings_theme_light),
    DARK(R.string.settings_theme_dark),
}

enum class UpdatePreference(@get:StringRes val labelRes: Int) {
    STABLE(R.string.settings_channel_stable),
    NIGHTLY(R.string.settings_channel_nightly),
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
    val referer: String = "",
    val origin: String = "",
    val customHeaders: Map<String, String> = emptyMap(),
    val destinationTreeUri: String? = null,
    val resolvedMediaUrl: String? = null,
    val resolvedHeaders: Map<String, String> = emptyMap(),
    /**
     * Optional explicit yt-dlp format selector produced by the Quick Download probe.
     * When null the existing quality/format heuristics are used unchanged.
     */
    val formatSelector: String? = null,
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
)

@Immutable
data class RuntimeStatus(
    val initialized: Boolean = false,
    val busy: Boolean = false,
    val version: String = "",
    val message: String = "Runtime loads only when needed",
)
