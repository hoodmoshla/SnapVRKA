package com.mvrk.vrka.share

import com.mvrk.vrka.AudioFormat
import com.mvrk.vrka.DownloadRequestFactory
import com.mvrk.vrka.DownloadJob
import com.mvrk.vrka.MediaMode
import com.mvrk.vrka.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickDownloadRequestTest {

    private val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    private val video = MediaFormat(
        formatId = "137",
        extension = "mp4",
        height = 1080,
        width = 1920,
        fps = 30.0,
        vcodec = "avc1.640028",
        acodec = "none",
        tbr = 4200.0,
        filesize = 121_000_000L,
    )

    private val audio = MediaFormat(
        formatId = "140",
        extension = "m4a",
        vcodec = "none",
        acodec = "mp4a.40.2",
        abr = 128.0,
        tbr = 128.0,
        filesize = 2_000_000L,
    )

    @Test
    fun videoRequestCarriesPinnedFormatSelector() {
        val option = QuickDownloadPlanner.videoOptions(
            MediaInfo(title = "t", durationSeconds = 100.0, formats = listOf(video, audio)),
        ).single()
        val request = QuickDownloadPlanner.videoRequest(url, option)
        assertEquals(url, request.url)
        assertEquals(MediaMode.VIDEO, request.mode)
        assertEquals(VideoQuality.P1080, request.quality)
        assertEquals("137+140/137+bestaudio/137/bestvideo[height<=1080]+bestaudio/best[height<=1080]/best", request.formatSelector)
    }

    @Test
    fun factoryUsesFormatSelectorForVideoDownloads() {
        val option = QuickDownloadPlanner.videoOptions(
            MediaInfo(title = "t", durationSeconds = 100.0, formats = listOf(video, audio)),
        ).single()
        val request = QuickDownloadPlanner.videoRequest(url, option)
        val ytdlp = DownloadRequestFactory.download(
            DownloadJob(id = "job", request = request),
            stagingDirectory = java.io.File("/tmp/snapvrka-test"),
        )
        val args = ytdlp.buildCommand()
        val formatIndex = args.indexOf("-f")
        assertTrue(formatIndex >= 0)
        assertEquals("137+140/137+bestaudio/137/bestvideo[height<=1080]+bestaudio/best[height<=1080]/best", args[formatIndex + 1])
    }

    @Test
    fun factoryFallsBackToQualityCapWithoutSelector() {
        val request = com.mvrk.vrka.DownloadRequest(url = url, quality = VideoQuality.P720)
        val ytdlp = DownloadRequestFactory.download(
            DownloadJob(id = "job", request = request),
            stagingDirectory = java.io.File("/tmp/snapvrka-test"),
        )
        val args = ytdlp.buildCommand()
        val formatIndex = args.indexOf("-f")
        assertEquals(
            "bestvideo[height<=720]+bestaudio/best[height<=720]/best",
            args[formatIndex + 1],
        )
    }

    @Test
    fun jobStorePersistsAndRestoresFormatSelector() {
        val directory = java.io.File("build/tmp/snapvrka-format-selector-store").apply { mkdirs() }
        val file = java.io.File(directory, "jobs.json")
        if (file.exists()) file.delete()
        val store = com.mvrk.vrka.JobStore(file)

        val request = com.mvrk.vrka.DownloadRequest(url = url, formatSelector = "137+140/137/best")
        store.save(listOf(com.mvrk.vrka.DownloadJob(id = "job-1", request = request)))

        val restored = store.load().single()
        assertEquals("137+140/137/best", restored.request.formatSelector)
    }

    @Test
    fun jobStoreLeavesFormatSelectorNullWhenAbsent() {
        val directory = java.io.File("build/tmp/snapvrka-format-selector-store").apply { mkdirs() }
        val file = java.io.File(directory, "jobs-empty.json")
        if (file.exists()) file.delete()
        val store = com.mvrk.vrka.JobStore(file)

        store.save(listOf(com.mvrk.vrka.DownloadJob(id = "job-2", request = com.mvrk.vrka.DownloadRequest(url = url))))

        val restored = store.load().single()
        assertNull(restored.request.formatSelector)
    }

    @Test
    fun audioRequestUsesMp3BitrateAndSelector() {
        val option = AudioQualityOption(
            codec = AudioFormat.MP3.codec,
            label = "MP3 192",
            bitrateKbps = 192,
            sourceFormatId = null,
            sizeBytes = 4_800_000L,
            sizeIsApproximate = true,
            isNativeCopy = false,
        )
        val request = QuickDownloadPlanner.audioRequest(url, option)
        assertEquals(MediaMode.AUDIO, request.mode)
        assertEquals(AudioFormat.MP3, request.audioFormat)
        assertEquals(192, request.mp3Bitrate)

        val ytdlp = DownloadRequestFactory.download(
            DownloadJob(id = "job", request = request),
            stagingDirectory = java.io.File("/tmp/snapvrka-test"),
        )
        val args = ytdlp.buildCommand()
        assertTrue(args.contains("--extract-audio"))
        assertTrue(args.contains("--audio-format"))
        val qualityIndex = args.indexOf("--audio-quality")
        assertEquals("192K", args[qualityIndex + 1])
    }
}
