package com.mvrk.vrka.share

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareUrlParserTest {

    @Test
    fun actionSendExtraTextExtractsUrl() {
        val url = ShareUrlParser.fromIntent(
            Intent.ACTION_SEND,
            null,
            "شاهد هذا الفيديو:\nhttps://www.youtube.com/watch?v=dQw4w9WgXcQ",
        )
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", url)
    }

    @Test
    fun actionViewUsesIntentData() {
        val url = ShareUrlParser.fromIntent(
            Intent.ACTION_VIEW,
            "https://x.com/user/status/12345",
            null,
        )
        assertEquals("https://x.com/user/status/12345", url)
    }

    @Test
    fun urlWithSurroundingProseIsExtracted() {
        val url = ShareUrlParser.extractUrl(
            "Look at this! https://www.instagram.com/reel/ABC123/?utm_source=share — enjoy",
        )
        assertEquals("https://www.instagram.com/reel/ABC123/?utm_source=share", url)
    }

    @Test
    fun trailingPunctuationIsStripped() {
        assertEquals(
            "https://www.tiktok.com/@user/video/123",
            ShareUrlParser.extractUrl("(https://www.tiktok.com/@user/video/123)."),
        )
    }

    @Test
    fun nonHttpSchemesAreRejected() {
        assertNull(ShareUrlParser.normalize("file:///etc/passwd"))
        assertNull(ShareUrlParser.normalize("content://media/external/video/1"))
        assertNull(ShareUrlParser.normalize("javascript:alert(1)"))
        assertNull(ShareUrlParser.normalize("ftp://example.com/video.mp4"))
        assertNull(ShareUrlParser.extractUrl("there is no link here"))
    }

    @Test
    fun emptyOrBlankInputIsRejected() {
        assertNull(ShareUrlParser.extractUrl(null))
        assertNull(ShareUrlParser.extractUrl("   "))
        assertNull(ShareUrlParser.normalize(null))
        assertNull(ShareUrlParser.normalize("   "))
    }

    @Test
    fun hostlessUrlsAreRejected() {
        assertNull(ShareUrlParser.normalize("https://"))
        assertNull(ShareUrlParser.normalize("http:///path"))
    }
}
