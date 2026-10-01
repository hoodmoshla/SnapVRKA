package com.mvrk.vrka.share

import com.mvrk.vrka.DownloadRequest
import com.mvrk.vrka.MediaMode

/**
 * Pure state transitions shared by the Quick Download sheet **and** the Home screen.
 *
 * Keeping this logic out of the composables means both entry points can never disagree about
 * which formats exist: options always come from a real yt-dlp probe, and a request can only be
 * built from an option the user actually saw.
 */
object QuickDownloadAnalyzer {

    /** Builds the ready state for a freshly probed media document. */
    fun ready(media: MediaInfo, url: String): QuickDownloadState {
        val videos = QuickDownloadPlanner.videoOptions(media)
        val audios = QuickDownloadPlanner.audioOptions(media)
        return QuickDownloadState(
            phase = ProbePhase.READY,
            url = url,
            media = media,
            videoOptions = videos,
            audioOptions = audios,
            selectedVideo = videos.firstOrNull(),
            // Audio-only sources (or sources with no usable video stream) start on the audio tab.
            selectedAudio = if (videos.isEmpty()) audios.firstOrNull() else null,
        )
    }

    /** A failed probe keeps the URL so retry does not need the user to paste again. */
    fun failed(url: String): QuickDownloadState = QuickDownloadState(
        phase = ProbePhase.FAILED,
        url = url,
        errorMessage = null,
    )

    fun selectMode(state: QuickDownloadState, mode: MediaMode): QuickDownloadState = when (mode) {
        MediaMode.VIDEO -> state.copy(
            selectedVideo = state.videoOptions.firstOrNull(),
            selectedAudio = null,
        )
        MediaMode.AUDIO -> state.copy(
            selectedVideo = null,
            selectedAudio = state.audioOptions.firstOrNull(),
        )
    }

    fun selectVideo(state: QuickDownloadState, option: VideoQualityOption): QuickDownloadState =
        state.copy(selectedVideo = option, selectedAudio = null)

    fun selectAudio(state: QuickDownloadState, option: AudioQualityOption): QuickDownloadState =
        state.copy(selectedAudio = option, selectedVideo = null)

    /**
     * Builds the request for the Quick Download sheet, where the section the user last tapped
     * decides the media mode.
     */
    fun requestForSelection(state: QuickDownloadState): DownloadRequest? {
        val audio = state.selectedAudio
        val video = state.selectedVideo
        return when {
            audio != null -> QuickDownloadPlanner.audioRequest(state.url, audio)
            video != null -> QuickDownloadPlanner.videoRequest(state.url, video)
            else -> null
        }
    }

    /**
     * Builds the request for the Home screen, where the Video/Audio switch decides the media mode.
     * Returns null while the user has not analysed the link (no quality selection yet).
     */
    fun requestForMode(state: QuickDownloadState, mode: MediaMode): DownloadRequest? {
        val audio = state.selectedAudio.takeIf { mode == MediaMode.AUDIO }
        val video = state.selectedVideo.takeIf { mode == MediaMode.VIDEO }
        return when {
            audio != null -> QuickDownloadPlanner.audioRequest(state.url, audio)
            video != null -> QuickDownloadPlanner.videoRequest(state.url, video)
            else -> null
        }
    }
}
