package com.mvrk.vrka.share

import com.mvrk.vrka.AudioFormat
import com.mvrk.vrka.MediaMode
import com.mvrk.vrka.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home screen flow: link -> probe -> **real** qualities with sizes -> download.
 *
 * A resolution that the source does not provide can never be offered or requested.
 */
class HomeQualityFlowTest {

    private fun video(id: String, height: Int, fps: Double = 30.0, size: Long? = 10_000_000L) =
        MediaFormat(
            formatId = id,
            extension = "mp4",
            width = (height * 16) / 9,
            height = height,
            fps = fps,
            vcodec = "avc1.640028",
            acodec = "none",
            tbr = height.toDouble(),
            filesize = size,
        )

    private val audioOnly = MediaFormat(
        formatId = "140",
        extension = "m4a",
        vcodec = "none",
        acodec = "mp4a.40.2",
        abr = 128.0,
        tbr = 128.0,
        filesize = 2_000_000L,
    )

    private val opusAudio = MediaFormat(
        formatId = "251",
        extension = "webm",
        vcodec = "none",
        acodec = "opus",
        abr = 130.0,
        tbr = 130.0,
        filesize = 3_000_000L,
    )

    private fun media(vararg formats: MediaFormat) = MediaInfo(
        title = "Sample",
        durationSeconds = 120.0,
        formats = formats.toList(),
    )

    @Test
    fun readyStateOnlyContainsResolutionsThatExist() {
        val state = QuickDownloadAnalyzer.ready(
            media(video("137", 1080), video("136", 720), video("135", 480), audioOnly),
            "https://example.test/v",
        )
        assertEquals(ProbePhase.READY, state.phase)
        assertEquals(listOf(1080, 720, 480), state.videoOptions.map { it.height })
        assertTrue(state.videoOptions.none { it.height == 2160 || it.height == 1440 })
    }

    @Test
    fun readyStateSelectsTheBestAvailableQualityByDefault() {
        val state = QuickDownloadAnalyzer.ready(
            media(video("137", 1080), video("136", 720), audioOnly),
            "https://example.test/v",
        )
        assertEquals(1080, state.selectedVideo?.height)
    }

    @Test
    fun readyStateCarriesRealSizes() {
        val state = QuickDownloadAnalyzer.ready(
            media(video("137", 1080, size = 121_000_000L), audioOnly),
            "https://example.test/v",
        )
        val option = state.selectedVideo!!
        assertEquals(121_000_000L + 2_000_000L, option.sizeBytes)
        assertFalse(option.sizeIsApproximate)
    }

    @Test
    fun noQualityCanBeRequestedBeforeAnalysis() {
        val idle = QuickDownloadState()
        assertNull(QuickDownloadAnalyzer.requestForMode(idle, MediaMode.VIDEO))
        assertNull(QuickDownloadAnalyzer.requestForMode(idle, MediaMode.AUDIO))
        assertNull(QuickDownloadAnalyzer.requestForSelection(idle))
    }

    @Test
    fun unavailableQualityCannotBeRequested() {
        // Source only has 720p: the video request must cap at 720p, never 1080p/2160p.
        val state = QuickDownloadAnalyzer.ready(
            media(video("136", 720), audioOnly),
            "https://example.test/v",
        )
        val request = QuickDownloadAnalyzer.requestForMode(state, MediaMode.VIDEO)!!
        assertEquals(VideoQuality.P720, request.quality)
        val selector = request.formatSelector!!
        assertTrue(selector.startsWith("136+140"))
        assertFalse(selector.contains("height<=1080"))
        assertFalse(selector.contains("height<=2160"))
    }

    @Test
    fun videoModeNeverProducesAudioOnlyRequest() {
        val state = QuickDownloadAnalyzer.ready(
            media(video("137", 1080), audioOnly, opusAudio),
            "https://example.test/v",
        )
        val request = QuickDownloadAnalyzer.requestForMode(state, MediaMode.VIDEO)!!
        assertEquals(MediaMode.VIDEO, request.mode)
        assertTrue(request.formatSelector!!.contains("137+"))
    }

    @Test
    fun switchingToAudioModeSelectsAudioOnlyOptions() {
        val state = QuickDownloadAnalyzer.ready(
            media(video("137", 1080), audioOnly, opusAudio),
            "https://example.test/v",
        )
        val audioState = QuickDownloadAnalyzer.selectMode(state, MediaMode.AUDIO)
        assertNull(audioState.selectedVideo)
        assertNotNull(audioState.selectedAudio)
        assertEquals(4, audioState.audioOptions.size)

        val request = QuickDownloadAnalyzer.requestForMode(audioState, MediaMode.AUDIO)!!
        assertEquals(MediaMode.AUDIO, request.mode)
    }

    @Test
    fun switchingBackToVideoModeRestoresAVideoSelection() {
        val state = QuickDownloadAnalyzer.ready(
            media(video("137", 1080), audioOnly),
            "https://example.test/v",
        )
        val roundTrip = QuickDownloadAnalyzer.selectMode(
            QuickDownloadAnalyzer.selectMode(state, MediaMode.AUDIO),
            MediaMode.VIDEO,
        )
        assertEquals(1080, roundTrip.selectedVideo?.height)
        assertNull(roundTrip.selectedAudio)
    }

    @Test
    fun audioOnlySourceOffersNoVideoQuality() {
        val state = QuickDownloadAnalyzer.ready(media(audioOnly), "https://example.test/a")
        assertTrue(state.videoOptions.isEmpty())
        assertNull(state.selectedVideo)
        assertNull(QuickDownloadAnalyzer.requestForMode(state, MediaMode.VIDEO))
        assertNotNull(QuickDownloadAnalyzer.requestForMode(state, MediaMode.AUDIO))
    }

    @Test
    fun selectingAnAudioOptionKeepsMp3AndOpusHonest() {
        val state = QuickDownloadAnalyzer.ready(
            media(video("137", 1080), audioOnly, opusAudio),
            "https://example.test/v",
        )
        val mp3 = state.audioOptions.first { it.codec == AudioFormat.MP3.codec && it.bitrateKbps == 192 }
        val mp3Request = QuickDownloadAnalyzer.requestForMode(
            QuickDownloadAnalyzer.selectAudio(state, mp3),
            MediaMode.AUDIO,
        )!!
        assertEquals(AudioFormat.MP3, mp3Request.audioFormat)
        assertEquals(192, mp3Request.mp3Bitrate)

        val opus = state.audioOptions.first { it.codec == AudioFormat.OPUS.codec }
        val opusRequest = QuickDownloadAnalyzer.requestForMode(
            QuickDownloadAnalyzer.selectAudio(state, opus),
            MediaMode.AUDIO,
        )!!
        assertEquals(AudioFormat.OPUS, opusRequest.audioFormat)
        assertEquals("251/bestaudio/best", opusRequest.formatSelector)
    }

    @Test
    fun failedProbeKeepsUrlForRetryButOffersNothing() {
        val failed = QuickDownloadAnalyzer.failed("https://example.test/v")
        assertEquals(ProbePhase.FAILED, failed.phase)
        assertEquals("https://example.test/v", failed.url)
        assertTrue(failed.videoOptions.isEmpty())
        assertNull(QuickDownloadAnalyzer.requestForMode(failed, MediaMode.VIDEO))
    }

    @Test
    fun shareSheetSelectionUsesTheSamePipelineAsHome() {
        val state = QuickDownloadAnalyzer.ready(
            media(video("137", 1080), audioOnly),
            "https://example.test/v",
        )
        val sheetRequest = QuickDownloadAnalyzer.requestForSelection(state)!!
        val homeRequest = QuickDownloadAnalyzer.requestForMode(state, MediaMode.VIDEO)!!
        assertEquals(homeRequest.formatSelector, sheetRequest.formatSelector)
        assertEquals(homeRequest.mode, sheetRequest.mode)
    }
}
