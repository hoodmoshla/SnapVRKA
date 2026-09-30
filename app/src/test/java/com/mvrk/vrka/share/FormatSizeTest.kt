package com.mvrk.vrka.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatSizeTest {

    @Test
    fun exactFileSizeWinsOverApproximation() {
        val format = MediaFormat(formatId = "137", filesize = 10L, filesizeApprox = 999L)
        assertEquals(10L, QuickDownloadPlanner.knownSize(format))
    }

    @Test
    fun approximateSizeIsUsedWhenExactIsMissing() {
        val format = MediaFormat(formatId = "137", filesizeApprox = 42L)
        assertEquals(42L, QuickDownloadPlanner.knownSize(format))
    }

    @Test
    fun sizeIsEstimatedFromBitrateAndDuration() {
        // 320 kbps for 200 seconds => 320_000/8 * 200 = 8_000_000 bytes
        assertEquals(8_000_000L, QuickDownloadPlanner.estimateBytes(320, 200.0))
    }

    @Test
    fun estimationRequiresPositiveDuration() {
        assertNull(QuickDownloadPlanner.estimateBytes(320, null))
        assertNull(QuickDownloadPlanner.estimateBytes(320, 0.0))
        assertNull(QuickDownloadPlanner.estimatedSize(null, 100.0))
        assertNull(QuickDownloadPlanner.estimatedSize(0.0, 100.0))
    }

    @Test
    fun estimatedSizeUsesKiloBitsPerSecond() {
        // 1000 kbps for 8 seconds => 1_000_000 bytes
        assertEquals(1_000_000L, QuickDownloadPlanner.estimatedSize(1000.0, 8.0))
    }
}
