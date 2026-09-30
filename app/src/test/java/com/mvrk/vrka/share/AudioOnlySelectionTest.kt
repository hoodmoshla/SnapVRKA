package com.mvrk.vrka.share

import com.mvrk.vrka.AudioFormat
import com.mvrk.vrka.MediaMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOnlySelectionTest {

    private val opusAudio = MediaFormat(
        formatId = "251",
        extension = "webm",
        vcodec = "none",
        acodec = "opus",
        abr = 130.0,
        tbr = 130.0,
        filesize = 3_500_000L,
    )

    private val aacAudio = MediaFormat(
        formatId = "140",
        extension = "m4a",
        vcodec = "none",
        acodec = "mp4a.40.2",
        abr = 128.0,
        tbr = 128.0,
    )

    private fun info(vararg formats: MediaFormat) = MediaInfo(
        title = "Sample",
        durationSeconds = 200.0,
        formats = formats.toList(),
    )

    @Test
    fun offersMp3BitratesAndOpus() {
        val options = QuickDownloadPlanner.audioOptions(info(opusAudio))
        assertEquals(listOf("MP3 320", "MP3 192", "MP3 128", "Opus"), options.map { it.label })
        assertEquals(listOf(320, 192, 128), options.take(3).map { it.bitrateKbps })
    }

    @Test
    fun mp3SizesAreEstimatedFromBitrateAndDuration() {
        val options = QuickDownloadPlanner.audioOptions(info(opusAudio))
        // 320 kbps * 200 s / 8 => 8_000_000 bytes
        assertEquals(8_000_000L, options.first { it.label == "MP3 320" }.sizeBytes)
        // 192 kbps * 200 s / 8 => 4_800_000 bytes
        assertEquals(4_800_000L, options.first { it.label == "MP3 192" }.sizeBytes)
        // 128 kbps * 200 s / 8 => 3_200_000 bytes
        assertEquals(3_200_000L, options.first { it.label == "MP3 128" }.sizeBytes)
        assertTrue(options.take(3).all { it.sizeIsApproximate })
    }

    @Test
    fun nativeOpusIsPreferredAndReusesSourceStream() {
        val options = QuickDownloadPlanner.audioOptions(info(opusAudio, aacAudio))
        val opus = options.first { it.label == "Opus" }
        assertEquals("251", opus.sourceFormatId)
        assertEquals(130, opus.bitrateKbps)
        assertTrue(opus.isNativeCopy)
        assertEquals(3_500_000L, opus.sizeBytes)
        assertTrue(!opus.sizeIsApproximate)
        assertEquals("251/bestaudio/best", opus.formatSelector)
    }

    @Test
    fun opusFallsBackToTranscodeWhenSourceHasNoOpus() {
        val options = QuickDownloadPlanner.audioOptions(info(aacAudio))
        val opus = options.first { it.label == "Opus" }
        assertNull(opus.sourceFormatId)
        assertTrue(!opus.isNativeCopy)
        // 96 kbps * 200 s / 8 => 2_400_000 bytes
        assertEquals(2_400_000L, opus.sizeBytes)
        assertTrue(opus.sizeIsApproximate)
        assertNull(opus.formatSelector)
    }

    @Test
    fun audioRequestsUseAudioModeOnly() {
        val options = QuickDownloadPlanner.audioOptions(info(opusAudio))
        val mp3Request = QuickDownloadPlanner.audioRequest("https://youtube.com/watch?v=x", options.first { it.label == "MP3 192" })
        assertEquals(MediaMode.AUDIO, mp3Request.mode)
        assertEquals(AudioFormat.MP3, mp3Request.audioFormat)
        assertEquals(192, mp3Request.mp3Bitrate)

        val opusRequest = QuickDownloadPlanner.audioRequest("https://youtube.com/watch?v=x", options.first { it.label == "Opus" })
        assertEquals(MediaMode.AUDIO, opusRequest.mode)
        assertEquals(AudioFormat.OPUS, opusRequest.audioFormat)
        assertNotNull(opusRequest.formatSelector)
    }
}
