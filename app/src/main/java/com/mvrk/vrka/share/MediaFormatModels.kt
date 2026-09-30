package com.mvrk.vrka.share

import androidx.compose.runtime.Immutable

/** A single yt-dlp format entry. */
@Immutable
data class MediaFormat(
    val formatId: String,
    val extension: String = "mp4",
    val container: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val abr: Double? = null,
    val vbr: Double? = null,
    val tbr: Double? = null,
    val filesize: Long? = null,
    val filesizeApprox: Long? = null,
    val protocol: String? = null,
) {
    val hasVideo: Boolean
        get() = !vcodec.isNullOrBlank() && !vcodec.equals("none", true)

    val hasAudio: Boolean
        get() = !acodec.isNullOrBlank() && !acodec.equals("none", true)

    val bestKnownSize: Long?
        get() = filesize ?: filesizeApprox
}

/** Parsed media metadata for a single video. */
@Immutable
data class MediaInfo(
    val id: String? = null,
    val title: String = "",
    val durationSeconds: Double? = null,
    val thumbnailUrl: String? = null,
    val webpageUrl: String? = null,
    val uploader: String? = null,
    val formats: List<MediaFormat> = emptyList(),
)

/** A downloadable video quality row (video stream + best matching audio stream). */
@Immutable
data class VideoQualityOption(
    val height: Int,
    val label: String,
    val videoFormatId: String,
    val audioFormatId: String?,
    val fps: Double?,
    val vcodec: String?,
    val extension: String,
    val sizeBytes: Long?,
    val sizeIsApproximate: Boolean,
) {
    val formatSelector: String
        get() {
            val tiers = mutableListOf<String>()
            if (audioFormatId != null) {
                tiers.add("$videoFormatId+$audioFormatId")
            }
            tiers.add("$videoFormatId+bestaudio")
            tiers.add("$videoFormatId")
            tiers.add("bestvideo[height<=$height]+bestaudio")
            tiers.add("best[height<=$height]")
            tiers.add("best")
            return tiers.joinToString("/")
        }
}

/** A selectable audio-only option. */
@Immutable
data class AudioQualityOption(
    val codec: String,
    val label: String,
    val bitrateKbps: Int?,
    val sourceFormatId: String?,
    val sizeBytes: Long?,
    val sizeIsApproximate: Boolean,
    val isNativeCopy: Boolean,
) {
    val formatSelector: String?
        get() = sourceFormatId?.let { "$it/bestaudio/best" }
}

/** Progress phases surfaced by the Quick Download sheet. */
enum class ProbePhase {
    IDLE,
    ANALYSING,
    FETCHING_INFO,
    READING_FORMATS,
    READY,
    FAILED,
}

@Immutable
data class QuickDownloadState(
    val phase: ProbePhase = ProbePhase.IDLE,
    val url: String = "",
    val media: MediaInfo? = null,
    val videoOptions: List<VideoQualityOption> = emptyList(),
    val audioOptions: List<AudioQualityOption> = emptyList(),
    val selectedVideo: VideoQualityOption? = null,
    val selectedAudio: AudioQualityOption? = null,
    val errorMessage: String? = null,
    val enqueued: Boolean = false,
)
