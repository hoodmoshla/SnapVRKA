package com.mvrk.vrka.update

import java.io.IOException

/**
 * Retry and resume rules for SnapVRKA's own update download.
 *
 * These rules are intentionally independent of VRKA 5.0.2's update rewrite.
 * Identity, package, signature and version checks stay in [UpdateVerification];
 * this object only decides whether a failed transfer may continue, and where a
 * resumed transfer should start.
 */
object AppUpdateDownloadPolicy {
    const val MAX_ATTEMPTS = 5
    const val INITIAL_BACKOFF_MILLIS = 30_000L

    enum class FailureDisposition {
        RETRY,
        PERMANENT,
    }

    fun isRetryableHttpStatus(statusCode: Int): Boolean =
        statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode in RETRYABLE_SERVER

    fun dispositionFor(failure: Throwable): FailureDisposition {
        var current: Throwable? = failure
        while (current != null) {
            when (current) {
                is PermanentAppUpdateException,
                is SecurityException,
                is IllegalArgumentException,
                -> return FailureDisposition.PERMANENT
                is AppUpdateHttpException -> return if (isRetryableHttpStatus(current.statusCode)) {
                    FailureDisposition.RETRY
                } else {
                    FailureDisposition.PERMANENT
                }
            }
            current = current.cause
        }
        return if (failure is IOException) FailureDisposition.RETRY else FailureDisposition.PERMANENT
    }

    fun shouldRetry(runAttemptCount: Int, failure: Throwable): Boolean =
        runAttemptCount in 0 until MAX_ATTEMPTS - 1 &&
            dispositionFor(failure) == FailureDisposition.RETRY

    /**
     * A partial file is reusable only when it is shorter than the published size.
     * A file that already reached or passed that size must be discarded: resuming
     * it would either duplicate bytes or hide a corrupt transfer.
     */
    fun resumeOffset(partialBytes: Long, expectedSizeBytes: Long): Long {
        if (partialBytes <= 0L || expectedSizeBytes <= 0L) return 0L
        return if (partialBytes < expectedSizeBytes) partialBytes else 0L
    }

    fun rangeHeader(offset: Long): String? = if (offset > 0L) "bytes=$offset-" else null

    private val RETRYABLE_SERVER = setOf(500, 502, 503, 504)
}

class AppUpdateHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)

class PermanentAppUpdateException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
