package com.mvrk.vrka.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatProbeTest {

    private val singleJson = """
        {
          "id": "dQw4w9WgXcQ",
          "title": "Sample Video",
          "duration": 213.0,
          "thumbnail": "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
          "webpage_url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
          "uploader": "Sample Channel",
          "formats": [
            {"format_id": "137", "ext": "mp4", "width": 1920, "height": 1080, "fps": 30, "vcodec": "avc1.640028", "acodec": "none", "tbr": 4200.0, "filesize": 121000000},
            {"format_id": "248", "ext": "webm", "width": 1920, "height": 1080, "fps": 30, "vcodec": "vp9", "acodec": "none", "tbr": 3000.0, "filesize_approx": 90000000},
            {"format_id": "22", "ext": "mp4", "width": 1280, "height": 720, "fps": 30, "vcodec": "avc1.64001F", "acodec": "mp4a.40.2", "tbr": 1500.0, "filesize": 67000000},
            {"format_id": "251", "ext": "webm", "width": null, "height": null, "vcodec": "none", "acodec": "opus", "abr": 130.0, "filesize": 3500000},
            {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 128.0, "tbr": 128.0}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesMetadataFields() {
        val info = MediaFormatProbe.parseMediaInfo(singleJson)!!
        assertEquals("dQw4w9WgXcQ", info.id)
        assertEquals("Sample Video", info.title)
        assertEquals(213.0, info.durationSeconds!!, 0.001)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", info.thumbnailUrl)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", info.webpageUrl)
        assertEquals("Sample Channel", info.uploader)
        assertEquals(5, info.formats.size)
    }

    @Test
    fun parsesPerFormatFields() {
        val video = MediaFormatProbe.parseMediaInfo(singleJson)!!.formats.first { it.formatId == "137" }
        assertEquals("mp4", video.extension)
        assertEquals(1920, video.width)
        assertEquals(1080, video.height)
        assertEquals(30.0, video.fps!!, 0.001)
        assertEquals("avc1.640028", video.vcodec)
        assertEquals("none", video.acodec)
        assertEquals(4200.0, video.tbr!!, 0.001)
        assertEquals(121_000_000L, video.filesize)
        assertTrue(video.hasVideo)
        assertTrue(!video.hasAudio)
    }

    @Test
    fun filesizeApproxIsUsedWhenFilesizeIsMissing() {
        val webm = MediaFormatProbe.parseMediaInfo(singleJson)!!.formats.first { it.formatId == "248" }
        assertNull(webm.filesize)
        assertEquals(90_000_000L, webm.filesizeApprox)
        assertEquals(90_000_000L, webm.bestKnownSize)
    }

    @Test
    fun playlistPayloadUsesFirstEntry() {
        val playlistJson = """
            {"_type": "playlist", "entries": [
               {"id": "a", "title": "First", "duration": 10.0, "formats": []},
               {"id": "b", "title": "Second", "duration": 20.0, "formats": []}
            ]}
        """.trimIndent()
        val info = MediaFormatProbe.parseMediaInfo(playlistJson)!!
        assertEquals("First", info.title)
    }

    @Test
    fun malformedPayloadReturnsNull() {
        assertNull(MediaFormatProbe.parseMediaInfo("not json"))
        assertNull(MediaFormatProbe.parseMediaInfo(""))
    }

    @Test
    fun fallbackTitleIsUsedWhenMissing() {
        val info = MediaFormatProbe.parseMediaInfo("""{"id":"x","formats":[]}""", "Fallback")!!
        assertEquals("Fallback", info.title)
    }
}
