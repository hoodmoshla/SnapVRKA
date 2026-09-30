package com.mvrk.vrka

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/**
 * Presentation helpers for SnapVRKA.
 *
 * Internal pipeline tokens (job.detail / job.error) intentionally stay in English because the
 * download engine matches against them. Everything the user actually reads is translated here.
 */

internal fun requestSummary(context: Context, request: DownloadRequest): String = when (request.mode) {
    MediaMode.VIDEO -> context.getString(
        R.string.config_video_line,
        context.getString(request.quality.labelRes),
        context.getString(if (request.prefer60Fps) R.string.config_on else R.string.config_off),
    )
    MediaMode.AUDIO -> when (request.audioFormat) {
        AudioFormat.MP3 -> context.getString(R.string.config_mp3_line, request.mp3Bitrate)
        AudioFormat.OPUS -> context.getString(R.string.config_opus_line)
        AudioFormat.WAV -> context.getString(R.string.config_wav_line)
    }
}

@Composable
internal fun requestSummary(request: DownloadRequest): String {
    val context = LocalContext.current
    return requestSummary(context, request)
}

@Composable
internal fun qualityLabel(quality: VideoQuality): String = stringResource(quality.labelRes)

@Composable
internal fun modeLabel(mode: MediaMode): String = stringResource(mode.labelRes)

@Composable
internal fun audioFormatLabel(format: AudioFormat): String = stringResource(format.labelRes)

@Composable
internal fun preferenceLabel(preference: UpdatePreference): String = stringResource(preference.labelRes)

@Composable
internal fun saveModeLabel(mode: SaveLocationMode): String = stringResource(mode.labelRes)

@Composable
internal fun themeModeLabel(mode: ThemeMode): String = stringResource(mode.labelRes)

@Composable
internal fun fontPreferenceLabel(preference: FontPreference): String = stringResource(preference.labelRes)

internal fun jobStateLabelRes(state: JobState): Int = state.labelRes

internal fun jobStatusLabel(context: Context, job: DownloadJob): String {
    val res = when {
        job.state == JobState.PREPARING && job.detail.contains("runtime", true) -> R.string.state_analysing
        job.state == JobState.POSTPROCESSING && job.detail.contains("Publishing", true) -> R.string.state_publishing
        job.state == JobState.POSTPROCESSING &&
            (job.request.trimStart.isNotBlank() || job.request.trimEnd.isNotBlank()) -> R.string.state_trimming
        job.state == JobState.POSTPROCESSING && job.request.mode == MediaMode.AUDIO -> R.string.state_converting
        job.state == JobState.POSTPROCESSING -> R.string.state_merging
        else -> job.state.labelRes
    }
    return context.getString(res)
}

@Composable
internal fun jobStatusLabel(job: DownloadJob): String {
    val context = LocalContext.current
    return jobStatusLabel(context, job)
}

/** Maps internal English pipeline tokens to natural Arabic text for display. */
internal fun localizedJobDetail(context: Context, job: DownloadJob): String {
    val detail = job.detail.trim()
    if (detail.isEmpty()) return ""
    val direct = when {
        detail.equals("Waiting", true) -> R.string.detail_waiting
        detail.equals("Interrupted", true) -> R.string.detail_choose_location
        detail.startsWith("Queued retry", true) -> R.string.detail_queued_retry
        detail.equals("Cancelled", true) -> R.string.detail_cancelled
        detail.startsWith("Folder selected", true) -> R.string.detail_folder_selected
        detail.equals("Choose download location to start", true) -> R.string.detail_choose_location
        detail.startsWith("Download location unavailable", true) -> R.string.detail_location_unavailable
        detail.equals("Starting runtime", true) -> R.string.detail_starting_runtime
        detail.equals("Source ready", true) -> R.string.detail_source_ready
        detail.equals("Retrying direct extraction", true) -> R.string.detail_retrying_direct
        detail.startsWith("Direct extraction failed", true) -> R.string.detail_starting_fallback
        detail.endsWith("candidate selected; downloading", true) -> R.string.detail_candidate_selected
        detail.equals("Downloading", true) -> R.string.detail_downloading
        detail.equals("Post-processing", true) -> R.string.detail_post_processing
        detail.startsWith("Publishing to Downloads", true) -> R.string.detail_publishing
        detail.startsWith("Downloading via browser network", true) -> R.string.detail_downloading_browser
        detail.startsWith("Saved (transcoded to Opus)", true) -> R.string.detail_saved_opus_transcoded
        detail.startsWith("Saved (native Opus stream copy)", true) -> R.string.detail_saved_opus_native
        detail.startsWith("Saved to Downloads/SnapVRKA", true) -> R.string.detail_saved_single
        detail.equals("Download failed", true) -> R.string.detail_failed
        detail.startsWith("Browser fallback could not find", true) -> R.string.detail_fallback_failed
        else -> null
    }
    if (direct != null) return context.getString(direct)

    // Dynamic internal tokens: "Downloading segment 4/12" and "Saved 3 files".
    val segment = SEGMENT_PATTERN.find(detail)
    if (segment != null) {
        val completed = segment.groupValues[1].toIntOrNull() ?: 0
        val total = segment.groupValues[2].toIntOrNull() ?: 0
        return context.getString(R.string.detail_segment, completed, total)
    }
    val savedFiles = SAVED_FILES_PATTERN.find(detail)
    if (savedFiles != null) {
        val count = savedFiles.groupValues[1].toIntOrNull() ?: 0
        return context.getString(R.string.detail_saved_multi, count)
    }
    return detail
}

@Composable
internal fun localizedJobDetail(job: DownloadJob): String {
    val context = LocalContext.current
    return localizedJobDetail(context, job)
}

@Composable
internal fun localizedRuntimeMessage(runtime: RuntimeStatus): String {
    val context = LocalContext.current
    return localizedRuntimeMessage(context, runtime)
}

internal fun localizedRuntimeMessage(context: Context, runtime: RuntimeStatus): String {
    val message = runtime.message.trim()
    return when {
        message.isEmpty() -> ""
        message.equals("Runtime loads only when needed", true) ->
            context.getString(R.string.detail_preparing_runtime)
        message.equals("Preparing yt-dlp and FFmpeg", true) ->
            context.getString(R.string.detail_preparing_runtime)
        message.equals("Updating yt-dlp", true) ->
            context.getString(R.string.detail_updating_ytdlp)
        message.startsWith("yt-dlp", true) && message.endsWith("ready", true) ->
            context.getString(R.string.detail_ytdlp_ready, runtime.version)
        message.startsWith("Update failed", true) ->
            context.getString(R.string.detail_update_failed_retained, message.substringAfter(':').trim())
        else -> message
    }
}

internal fun jobProgressSummary(context: Context, job: DownloadJob): String = buildList {
    if (job.state == JobState.DOWNLOADING || job.state == JobState.POSTPROCESSING) {
        add("${job.progress.toInt()}%")
    }
    job.speed.takeIf(String::isNotBlank)?.let(::add)
    job.etaSeconds?.let { add(formatEtaCompact(context, it)) }
}.joinToString(" • ").ifBlank { localizedJobDetail(context, job) }

@Composable
internal fun jobProgressSummaryLabel(job: DownloadJob): String {
    val context = LocalContext.current
    return jobProgressSummary(context, job)
}

private enum class FailureKind {
    PUBLISHING,
    BROWSER_TIMEOUT,
    UNREACHABLE,
    NO_FORMAT,
    SIGN_IN,
    BROWSER_CLOSED,
    GENERIC,
}

private fun failureKind(job: DownloadJob): FailureKind {
    val text = "${job.detail} ${job.error}".lowercase()
    return when {
        "publish" in text || "mediastore" in text || "output file" in text -> FailureKind.PUBLISHING
        "timed out" in text && ("browser" in text || "webview" in text) -> FailureKind.BROWSER_TIMEOUT
        "resolve host" in text || "name_not_resolved" in text || "dns" in text ||
            "network is unreachable" in text || "connection timed out" in text -> FailureKind.UNREACHABLE
        "browser fallback could not find" in text || "could not find a media stream" in text ||
            "requested format" in text || "no video formats" in text ||
            "no downloadable" in text -> FailureKind.NO_FORMAT
        "sign in to confirm" in text || "login required" in text || "account required" in text ||
            "authentication required" in text || "private video" in text || "video is private" in text ->
            FailureKind.SIGN_IN
        "browser fallback closed" in text -> FailureKind.BROWSER_CLOSED
        else -> FailureKind.GENERIC
    }
}

internal fun friendlyFailureTitle(context: Context, job: DownloadJob): String =
    context.getString(
        when (failureKind(job)) {
            FailureKind.PUBLISHING -> R.string.fail_publishing_title
            FailureKind.BROWSER_TIMEOUT -> R.string.fail_browser_timeout_title
            FailureKind.UNREACHABLE -> R.string.fail_unreachable_title
            FailureKind.NO_FORMAT -> R.string.fail_no_format_title
            FailureKind.SIGN_IN -> R.string.fail_signin_title
            FailureKind.BROWSER_CLOSED -> R.string.fail_browser_closed_title
            FailureKind.GENERIC -> R.string.fail_generic_title
        },
    )

@Composable
internal fun friendlyFailureTitle(job: DownloadJob): String {
    val context = LocalContext.current
    return friendlyFailureTitle(context, job)
}

internal fun friendlyFailureDetail(context: Context, job: DownloadJob): String =
    context.getString(
        when (failureKind(job)) {
            FailureKind.PUBLISHING -> R.string.fail_publishing_detail
            FailureKind.BROWSER_TIMEOUT -> R.string.fail_browser_timeout_detail
            FailureKind.UNREACHABLE -> R.string.fail_unreachable_detail
            FailureKind.NO_FORMAT -> R.string.fail_no_format_detail
            FailureKind.SIGN_IN -> R.string.fail_signin_detail
            FailureKind.BROWSER_CLOSED -> R.string.fail_browser_closed_detail
            FailureKind.GENERIC -> R.string.fail_generic_detail
        },
    )

@Composable
internal fun friendlyFailureDetail(job: DownloadJob): String {
    val context = LocalContext.current
    return friendlyFailureDetail(context, job)
}

internal fun formatEtaCompact(context: Context, seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) {
        context.getString(R.string.eta_hours, hours, minutes, remainder)
    } else {
        context.getString(R.string.eta_minutes, minutes, remainder)
    }
}

@Composable
internal fun formatEtaCompactLabel(seconds: Long): String {
    val context = LocalContext.current
    return formatEtaCompact(context, seconds)
}

internal val SEGMENT_PATTERN = Regex("""segment\s+(\d+)\s*/\s*(\d+)""", RegexOption.IGNORE_CASE)
internal val SAVED_FILES_PATTERN = Regex("""saved\s+(\d+)\s+files""", RegexOption.IGNORE_CASE)
