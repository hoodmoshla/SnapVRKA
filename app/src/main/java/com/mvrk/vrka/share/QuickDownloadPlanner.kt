package com.mvrk.vrka.share

import com.mvrk.vrka.AudioFormat
import com.mvrk.vrka.DownloadRequest
import com.mvrk.vrka.MediaMode
import com.mvrk.vrka.VideoQuality

/**
 * Turns a [MediaInfo] into the selectable Quick Download rows and the corresponding
 * [DownloadRequest]s. Pure logic only, so it is fully unit-testable.
 */
object QuickDownloadPlanner {

    val MP3_BITRATES = listOf(320, 192, 128)
    const val NATIVE_OPUS_LABEL = "Opus"
    private const val DEFAULT_OPUS_BITRATE_KBPS = 96

    // ---------------------------------------------------------------- video

    fun videoOptions(info: MediaInfo): List<VideoQualityOption> {
        val videoFormats = info.formats.filter { it.hasVideo && (it.height ?: 0) > 0 }
        if (videoFormats.isEmpty()) return emptyList()

        val bestAudio = bestAudioForVideo(info.formats)

        return videoFormats
            .groupBy { it.height!! }
            .map { (height, candidates) -> buildVideoOption(height, candidates, bestAudio, info.durationSeconds) }
            .sortedByDescending { it.height }
    }

    private fun buildVideoOption(
        height: Int,
        candidates: List<MediaFormat>,
        bestAudio: MediaFormat?,
        duration: Double?,
    ): VideoQualityOption {
        val video = candidates.sortedWith(videoFormatComparator).first()
        val needsSeparateAudio = !video.hasAudio
        val audio = if (needsSeparateAudio) bestAudio else null

        val videoSize = knownSize(video) ?: estimatedSize(video.tbr, duration) ?: estimatedSize(video.vbr, duration)
        val audioSize = audio?.let { knownSize(it) ?: estimatedSize(it.tbr, duration) ?: estimatedSize(it.abr, duration) }

        val sizeIsApproximate = video.filesize == null || (audio != null && audio.filesize == null)
        val totalSize = when {
            videoSize == null -> null
            needsSeparateAudio && audioSize == null -> null
            else -> videoSize + (audioSize ?: 0L)
        }

        return VideoQualityOption(
            height = height,
            label = "${height}p",
            videoFormatId = video.formatId,
            audioFormatId = audio?.formatId,
            fps = video.fps,
            vcodec = video.vcodec,
            extension = video.extension.ifBlank { "mp4" },
            sizeBytes = totalSize,
            sizeIsApproximate = sizeIsApproximate,
        )
    }

    /** yt-dlp default-like ranking: fps, then bitrate, then codec compatibility, then size. */
    private val videoFormatComparator = Comparator<MediaFormat> { a, b ->
        var result = compareDouble(a.fps, b.fps)
        if (result != 0) return@Comparator -result
        result = compareDouble(a.tbr, b.tbr)
        if (result != 0) return@Comparator -result
        result = codecRank(a.vcodec).compareTo(codecRank(b.vcodec))
        if (result != 0) return@Comparator -result
        result = (a.bestKnownSize ?: 0L).compareTo(b.bestKnownSize ?: 0L)
        if (result != 0) return@Comparator -result
        a.formatId.compareTo(b.formatId)
    }

    /** Higher rank = preferred container/codec for the mp4 merge pipeline. */
    fun codecRank(codec: String?): Int {
        val value = codec?.lowercase().orEmpty()
        return when {
            value.startsWith("avc") || value.startsWith("h264") -> 3
            value.startsWith("vp9") || value.startsWith("vp09") -> 2
            value.startsWith("av01") -> 1
            value.isBlank() || value == "none" -> 0
            else -> 1
        }
    }

    fun bestAudioForVideo(formats: List<MediaFormat>): MediaFormat? {
        val audioOnly = formats.filter { it.hasAudio && !it.hasVideo && it.formatId.isNotBlank() }
        return audioOnly.sortedWith(audioForVideoComparator).firstOrNull()
    }

    private val audioForVideoComparator = Comparator<MediaFormat> { a, b ->
        var result = aacRank(a.acodec).compareTo(aacRank(b.acodec))
        if (result != 0) return@Comparator -result
        result = compareDouble(a.abr ?: a.tbr, b.abr ?: b.tbr)
        if (result != 0) return@Comparator -result
        a.formatId.compareTo(b.formatId)
    }

    private fun aacRank(acodec: String?): Int = when {
        acodec == null -> 0
        acodec.lowercase().startsWith("mp4a") -> 2
        acodec.lowercase().contains("opus") -> 1
        else -> 1
    }

    // ---------------------------------------------------------------- audio

    fun audioOptions(info: MediaInfo): List<AudioQualityOption> {
        val duration = info.durationSeconds
        val options = mutableListOf<AudioQualityOption>()

        MP3_BITRATES.forEach { bitrate ->
            options.add(
                AudioQualityOption(
                    codec = AudioFormat.MP3.codec,
                    label = "MP3 $bitrate",
                    bitrateKbps = bitrate,
                    sourceFormatId = null,
                    sizeBytes = estimateBytes(bitrate, duration),
                    sizeIsApproximate = true,
                    isNativeCopy = false,
                ),
            )
        }

        val nativeOpus = info.formats
            .filter { it.hasAudio && !it.hasVideo && it.acodec?.lowercase()?.contains("opus") == true }
            .maxByOrNull { it.abr ?: it.tbr ?: 0.0 }

        val opusBitrate = nativeOpus?.abr?.toInt() ?: DEFAULT_OPUS_BITRATE_KBPS
        options.add(
            AudioQualityOption(
                codec = AudioFormat.OPUS.codec,
                label = NATIVE_OPUS_LABEL,
                bitrateKbps = opusBitrate,
                sourceFormatId = nativeOpus?.formatId,
                sizeBytes = nativeOpus?.let(::knownSize) ?: estimateBytes(opusBitrate, duration),
                sizeIsApproximate = nativeOpus?.filesize == null,
                isNativeCopy = nativeOpus != null,
            ),
        )
        return options
    }

    fun bestAudioOnlyFormat(info: MediaInfo): MediaFormat? =
        info.formats
            .filter { it.hasAudio && !it.hasVideo }
            .maxByOrNull { it.abr ?: it.tbr ?: 0.0 }

    // ---------------------------------------------------------------- requests

    fun videoRequest(url: String, option: VideoQualityOption): DownloadRequest = DownloadRequest(
        url = url,
        mode = MediaMode.VIDEO,
        quality = qualityForHeight(option.height),
        prefer60Fps = (option.fps ?: 0.0) >= 50.0,
        embedMetadata = true,
        embedThumbnail = true,
        formatSelector = option.formatSelector,
    )

    fun audioRequest(url: String, option: AudioQualityOption): DownloadRequest = DownloadRequest(
        url = url,
        mode = MediaMode.AUDIO,
        audioFormat = if (option.codec == AudioFormat.OPUS.codec) AudioFormat.OPUS else AudioFormat.MP3,
        mp3Bitrate = option.bitrateKbps?.coerceIn(128, 320) ?: 320,
        embedMetadata = true,
        embedThumbnail = true,
        formatSelector = option.formatSelector,
    )

    /** Largest quality cap that does not exceed the requested height. */
    fun qualityForHeight(height: Int): VideoQuality =
        VideoQuality.entries
            .filter { it.height != null && it.height >= height }
            .minByOrNull { it.height!! }
            ?: VideoQuality.BEST

    // ---------------------------------------------------------------- sizes

    fun knownSize(format: MediaFormat): Long? = format.filesize ?: format.filesizeApprox

    fun estimatedSize(tbrKbps: Double?, durationSeconds: Double?): Long? {
        if (tbrKbps == null || tbrKbps <= 0.0 || durationSeconds == null || durationSeconds <= 0.0) return null
        return ((tbrKbps * 1000.0 / 8.0) * durationSeconds).toLong()
    }

    fun estimateBytes(bitrateKbps: Int, durationSeconds: Double?): Long? {
        if (durationSeconds == null || durationSeconds <= 0.0) return null
        return ((bitrateKbps * 1000.0 / 8.0) * durationSeconds).toLong()
    }

    private fun compareDouble(a: Double?, b: Double?): Int =
        (a ?: 0.0).compareTo(b ?: 0.0)
}
