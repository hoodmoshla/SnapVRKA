package com.mvrk.vrka.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Regression coverage for the only upstream 5.0.2 update behaviour adopted by
 * SnapVRKA: retry temporary network failures and resume an incomplete download.
 * Package, signature and version rejection stay in UpdateVerificationTest.
 */
class AppUpdateDownloadPolicyTest {

    @Test
    fun temporaryNetworkFailuresAreRetriedUntilTheAttemptLimit() {
        val failure = IOException("connection reset")
        assertTrue(AppUpdateDownloadPolicy.shouldRetry(0, failure))
        assertTrue(AppUpdateDownloadPolicy.shouldRetry(3, failure))
        assertFalse(AppUpdateDownloadPolicy.shouldRetry(4, failure))
        assertFalse(AppUpdateDownloadPolicy.shouldRetry(9, failure))
    }

    @Test
    fun temporaryHttpStatusesAreRetriedAndPermanentStatusesStop() {
        assertTrue(AppUpdateDownloadPolicy.shouldRetry(1, AppUpdateHttpException(503, "unavailable")))
        assertTrue(AppUpdateDownloadPolicy.shouldRetry(1, AppUpdateHttpException(429, "rate limited")))
        assertFalse(AppUpdateDownloadPolicy.shouldRetry(1, AppUpdateHttpException(404, "missing")))
        assertFalse(AppUpdateDownloadPolicy.shouldRetry(1, AppUpdateHttpException(403, "forbidden")))
    }

    @Test
    fun integrityAndIdentityFailuresAreNeverRetried() {
        val permanent = PermanentAppUpdateException("SHA-256 mismatch")
        assertEquals(
            AppUpdateDownloadPolicy.FailureDisposition.PERMANENT,
            AppUpdateDownloadPolicy.dispositionFor(permanent),
        )
        assertFalse(AppUpdateDownloadPolicy.shouldRetry(0, permanent))
        assertFalse(AppUpdateDownloadPolicy.shouldRetry(0, IOException(SecurityException("bad cert"))))
        assertFalse(AppUpdateDownloadPolicy.shouldRetry(0, IllegalArgumentException("bad url")))
    }

    @Test
    fun incompleteDownloadResumesAndAFullOrOversizedPartialDoesNot() {
        assertEquals(0L, AppUpdateDownloadPolicy.resumeOffset(0L, 1_000L))
        assertEquals(420L, AppUpdateDownloadPolicy.resumeOffset(420L, 1_000L))
        assertEquals(0L, AppUpdateDownloadPolicy.resumeOffset(1_000L, 1_000L))
        assertEquals(0L, AppUpdateDownloadPolicy.resumeOffset(1_200L, 1_000L))
        assertEquals(0L, AppUpdateDownloadPolicy.resumeOffset(50L, 0L))
    }

    @Test
    fun rangeHeaderIsSentOnlyForAResumedTransfer() {
        assertNull(AppUpdateDownloadPolicy.rangeHeader(0L))
        assertEquals("bytes=420-", AppUpdateDownloadPolicy.rangeHeader(420L))
    }
}
