package com.mvrk.vrka

internal fun requestSummary(request: DownloadRequest): String = SnapVrkaArabic.text(
    when (request.mode) {
        MediaMode.VIDEO -> "Video • ${request.quality.label}"
        MediaMode.AUDIO -> when (request.audioFormat) {
            AudioFormat.MP3 -> "MP3 • ${request.mp3Bitrate} kbps"
            AudioFormat.OPUS -> "Opus • Best Native Opus"
            AudioFormat.WAV -> "WAV • Uncompressed PCM"
        }
    },
)

internal fun jobMediaSummary(job: DownloadJob): String = SnapVrkaArabic.text(
    when (job.request.mode) {
        MediaMode.VIDEO -> {
            val actual = job.actualResolution
            if (!actual.isNullOrBlank()) {
                if (job.request.quality == VideoQuality.BEST) "Video • $actual (Best available)"
                else if (job.request.quality.label.startsWith(actual)) "Video • $actual"
                else "Video • $actual (requested: ${job.request.quality.label})"
            } else {
                "Video • ${job.request.quality.label}"
            }
        }
        MediaMode.AUDIO -> when (job.request.audioFormat) {
            AudioFormat.MP3 -> "Audio • MP3 (${job.request.mp3Bitrate} kbps)"
            AudioFormat.OPUS -> "Audio • Opus (Native)"
            AudioFormat.WAV -> "Audio • WAV (PCM)"
        }
    },
)

internal fun jobStatusLabel(job: DownloadJob): String = SnapVrkaArabic.text(
    when {
        job.state == JobState.PREPARING && job.detail.contains("runtime", true) -> "Analysing"
        job.state == JobState.POSTPROCESSING && job.detail.contains("Publishing", true) -> "Publishing"
        job.state == JobState.POSTPROCESSING &&
            (job.request.trimStart.isNotBlank() || job.request.trimEnd.isNotBlank()) -> "Trimming"
        job.state == JobState.POSTPROCESSING && job.request.mode == MediaMode.AUDIO -> "Converting"
        job.state == JobState.POSTPROCESSING -> "Merging"
        else -> job.state.label
    },
)

internal fun jobProgressSummary(job: DownloadJob): String = SnapVrkaArabic.text(
    buildList {
        if (job.state == JobState.DOWNLOADING || job.state == JobState.POSTPROCESSING) {
            add("${job.progress.toInt()}%")
        }
        job.speed.takeIf(String::isNotBlank)?.let(::add)
        job.etaSeconds?.let { add(formatEtaCompact(it)) }
    }.joinToString(" • ").ifBlank { job.detail },
)

internal fun friendlyFailureTitle(job: DownloadJob): String {
    val text = "${job.detail} ${job.error}".lowercase()
    val english = when {
        "publish" in text || "mediastore" in text || "output file" in text ->
            "Download failed during publishing"
        "timed out" in text && ("browser" in text || "webview" in text) ->
            "Browser session timed out"
        "resolve host" in text || "name_not_resolved" in text || "dns" in text ||
            "network is unreachable" in text || "connection timed out" in text ->
            "Couldn’t reach this site"
        "browser fallback could not find" in text || "could not find a media stream" in text ||
            "requested format" in text || "no video formats" in text ||
            "no downloadable" in text -> "Couldn’t find a downloadable format"
        "sign in to confirm" in text || "login required" in text || "account required" in text ||
            "authentication required" in text || "private video" in text || "video is private" in text ->
            "Sign-in may be required"
        "browser fallback closed" in text -> "Browser session closed"
        else -> "Download failed"
    }
    return SnapVrkaArabic.text(english)
}

internal fun friendlyFailureDetail(job: DownloadJob): String {
    val text = "${job.detail} ${job.error}".lowercase()
    val english = when {
        "publish" in text || "mediastore" in text || "output file" in text ->
            "The media was processed, but Android could not save the final file."
        "timed out" in text && ("browser" in text || "webview" in text) ->
            "Retry the browser session or close it safely."
        "resolve host" in text || "name_not_resolved" in text || "dns" in text ||
            "network is unreachable" in text || "connection timed out" in text ->
            "Check the connection, then try again."
        "browser fallback could not find" in text || "could not find a media stream" in text ||
            "requested format" in text || "no video formats" in text ||
            "no downloadable" in text ->
            "The page did not expose a supported non-DRM media format."
        "sign in to confirm" in text || "login required" in text || "account required" in text ||
            "authentication required" in text || "private video" in text || "video is private" in text ->
            "The source may require an authenticated browser session."
        "browser fallback closed" in text -> "No downloadable non-DRM media was handed off."
        else -> "Try again. Failure details saved to Diagnostics in Settings."
    }
    return SnapVrkaArabic.text(english)
}

internal fun formatEtaCompact(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) {
        SnapVrkaArabic.text("%d:%02d:%02d left".format(hours, minutes, remainder))
    } else {
        SnapVrkaArabic.text("%d:%02d left".format(minutes, remainder))
    }
}
