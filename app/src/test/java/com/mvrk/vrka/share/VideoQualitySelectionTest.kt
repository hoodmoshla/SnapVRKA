package com.mvrk.vrka.share

import com.mvrk.vrka.MediaMode
import com.mvrk.vrka.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoQualitySelectionTest {

    private fun video(id: String, height: Int, fps: Double? = 30.0, codec: String = "avc1.640028", size: Long? = null) =
        MediaFormat(
            formatId = id,
            extension = "mp4",
            width = (height * 16) / 9,
            height = height,
            fps = fps,
            vcodec = codec,
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

    private fun info(vararg formats: MediaFormat) = MediaInfo(
        title = "Sample",
        durationSeconds = 100.0,
        formats = formats.toList(),
    )

    @Test
    fun onlyExistingResolutionsAreOffered() {
        val media = info(
            video("313", 2160), video("271", 1440), video("137", 1080),
            video("136", 720), video("135", 480), audioOnly,
        )
        val heights = QuickDownloadPlanner.videoOptions(media).map { it.height }
        assertEquals(listOf(2160, 1440, 1080, 720, 480), heights)
    }

    @Test
    fun missingResolutionsAreNeverInvented() {
        val media = info(video("137", 1080), video("136", 720), audioOnly)
        val heights = QuickDownloadPlanner.videoOptions(media).map { it.height }
        assertEquals(listOf(1080, 720), heights)
        assertTrue(heights.none { it == 2160 || it == 1440 || it == 480 })
    }

    @Test
    fun bestFormatWinsForDuplicateResolution() {
        val media = info(
            video("vp9", 1080, codec = "vp9", fps = 30.0),
            video("avc", 1080, codec = "avc1.640028", fps = 30.0),
            audioOnly,
        )
        val option = QuickDownloadPlanner.videoOptions(media).single()
        assertEquals("avc", option.videoFormatId)
    }

    @Test
    fun higherFpsWinsForDuplicateResolution() {
        val media = info(
            video("30fps", 1080, fps = 30.0),
            video("60fps", 1080, fps = 60.0),
            audioOnly,
        )
        val option = QuickDownloadPlanner.videoOptions(media).single()
        assertEquals("60fps", option.videoFormatId)
        assertEquals(60.0, option.fps!!, 0.001)
    }

    @Test
    fun videoOnlyStreamIsPairedWithBestAudioOnlyStream() {
        val media = info(video("137", 1080), audioOnly)
        val option = QuickDownloadPlanner.videoOptions(media).single()
        assertEquals("137", option.videoFormatId)
        assertEquals("140", option.audioFormatId)
        assertEquals("137+140/137+bestaudio/137/bestvideo[height<=1080]+bestaudio/best[height<=1080]/best", option.formatSelector)
    }

    @Test
    fun muxedFormatDoesNotRequireSeparateAudio() {
        val muxed = MediaFormat(
            formatId = "22",
            extension = "mp4",
            height = 720,
            width = 1280,
            fps = 30.0,
            vcodec = "avc1.64001F",
            acodec = "mp4a.40.2",
            tbr = 1500.0,
            filesize = 67_000_000L,
        )
        val option = QuickDownloadPlanner.videoOptions(info(muxed)).single()
        assertNull(option.audioFormatId)
        assertEquals(67_000_000L, option.sizeBytes)
        assertTrue(!option.sizeIsApproximate)
    }

    @Test
    fun audioOnlyFormatsAreNeverOfferedAsVideoQuality() {
        val media = info(audioOnly)
        assertTrue(QuickDownloadPlanner.videoOptions(media).isEmpty())
    }

    @Test
    fun videoRequestNeverSelectsAudioOnly() {
        val media = info(video("137", 1080), audioOnly)
        val option = QuickDownloadPlanner.videoOptions(media).single()
        val request = QuickDownloadPlanner.videoRequest("https://youtube.com/watch?v=x", option)
        assertEquals(MediaMode.VIDEO, request.mode)
        assertEquals(VideoQuality.P1080, request.quality)
        val selector = request.formatSelector
        assertNotNull(selector)
        assertTrue(selector!!.startsWith("137+140"))
        assertTrue(!selector.contains("bestaudio/best]"))
    }

    @Test
    fun estimatedSizeIsSumOfVideoAndAudio() {
        val media = info(
            video("137", 1080, size = null),
            audioOnly,
        )
        val option = QuickDownloadPlanner.videoOptions(media).single()
        // video tbr = 1080 kbps over 100s => 13_500_000 ; audio filesize 2_000_000
        assertEquals(15_500_000L, option.sizeBytes)
        assertTrue(option.sizeIsApproximate)
    }

    @Test
    fun qualityForHeightPicksNearestUpperCap() {
        assertEquals(VideoQuality.P1080, QuickDownloadPlanner.qualityForHeight(1080))
        assertEquals(VideoQuality.P720, QuickDownloadPlanner.qualityForHeight(600))
        assertEquals(VideoQuality.P360, QuickDownloadPlanner.qualityForHeight(144))
        assertEquals(VideoQuality.BEST, QuickDownloadPlanner.qualityForHeight(4320))
    }
}
