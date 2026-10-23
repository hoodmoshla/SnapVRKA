package com.mvrk.vrka.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mvrk.vrka.update.AppUpdateManager
import com.mvrk.vrka.update.AppUpdateDownloadPolicy
import com.mvrk.vrka.update.AppUpdateNotificationFactory
import com.mvrk.vrka.update.AppUpdateHttpException
import com.mvrk.vrka.update.PermanentAppUpdateException
import com.mvrk.vrka.update.UpdateVerification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection

class AppUpdateDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL)
            ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Missing download URL"))
        val apkFileName = inputData.getString(KEY_APK_NAME)
            ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Missing APK file name"))
        val targetVersion = inputData.getString(KEY_TARGET_VERSION) ?: ""
        val expectedSizeBytes = inputData.getLong(KEY_EXPECTED_SIZE, 0L)
        val expectedSha256 = UpdateVerification.normalizeHex(inputData.getString(KEY_EXPECTED_SHA256))

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_STATE, STATE_DOWNLOADING)
            .putString(KEY_VERSION, targetVersion)
            .apply()

        val updatesDir = File(applicationContext.cacheDir, "updates")
        if (!updatesDir.exists()) updatesDir.mkdirs()

        val targetFile = File(updatesDir, apkFileName)
        val tempFile = File(updatesDir, "$apkFileName.download.tmp")

        try {
            Log.i(TAG, "Starting persistent APK download from $downloadUrl to ${targetFile.absolutePath} (attempt ${runAttemptCount + 1})")
            var currentUrl = downloadUrl
            var redirects = 0
            val maxRedirects = 5
            var success = false
            val resumeFrom = AppUpdateDownloadPolicy.resumeOffset(tempFile.length(), expectedSizeBytes)
            if (resumeFrom == 0L && tempFile.exists()) tempFile.delete()

            while (redirects < maxRedirects && !success) {
                val url = AppUpdateManager.validateHttpsUrl(currentUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "SnapVRKA-AppUpdater")
                AppUpdateDownloadPolicy.rangeHeader(resumeFrom)?.let { range ->
                    conn.setRequestProperty("Range", range)
                    conn.setRequestProperty("Accept-Encoding", "identity")
                }
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000

                try {
                    when (val code = conn.responseCode) {
                        HttpURLConnection.HTTP_OK -> {
                            if (resumeFrom > 0L) {
                                // The server ignored Range. Restart so the partial bytes are not duplicated.
                                tempFile.delete()
                                throw IOException("Server ignored the resume range; restarting the update download")
                            }
                            writeBody(conn, tempFile, 0L, expectedSizeBytes, prefs)
                            success = true
                        }
                        HttpURLConnection.HTTP_PARTIAL -> {
                            if (resumeFrom <= 0L) {
                                throw PermanentAppUpdateException("Unexpected partial response for a new update download")
                            }
                            val contentRange = conn.getHeaderField("Content-Range").orEmpty()
                            if (!contentRange.startsWith("bytes $resumeFrom-")) {
                                tempFile.delete()
                                throw IOException("Server resumed from an unexpected byte range")
                            }
                            val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSizeBytes
                            writeBody(conn, tempFile, resumeFrom, totalBytes, prefs)
                            success = true
                        }
                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_SEE_OTHER,
                        307, 308 -> {
                            val location = conn.getHeaderField("Location")
                                ?: throw IOException("HTTP redirect $code without Location header")
                            val nextUrl = AppUpdateManager.resolveRedirectUrl(url, location)
                            AppUpdateManager.validateHttpsUrl(nextUrl)
                            currentUrl = nextUrl
                            redirects++
                        }
                        else -> {
                            if (AppUpdateDownloadPolicy.isRetryableHttpStatus(code)) {
                                throw AppUpdateHttpException(code, "Temporary HTTP error $code from $currentUrl")
                            }
                            throw PermanentAppUpdateException("HTTP error $code from $currentUrl")
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            }

            if (!success || !tempFile.exists() || tempFile.length() == 0L) {
                throw IOException("APK download failed or exceeded redirects")
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            Log.i(TAG, "APK download completed: ${targetFile.absolutePath} (${targetFile.length()} bytes)")

            // ---- Integrity gate: the bytes must match the published SHA-256 before anything else.
            if (expectedSha256 == null) {
                targetFile.delete()
                throw PermanentAppUpdateException("The release did not publish a SHA-256 checksum; refusing the update.")
            }
            if (expectedSizeBytes > 0L && targetFile.length() != expectedSizeBytes) {
                targetFile.delete()
                throw PermanentAppUpdateException(
                    "Size mismatch: expected $expectedSizeBytes bytes but downloaded ${targetFile.length()} bytes.",
                )
            }
            val actualSha256 = UpdateVerification.sha256Hex(targetFile)
            if (!UpdateVerification.sha256Matches(expectedSha256, actualSha256)) {
                targetFile.delete()
                throw PermanentAppUpdateException("SHA-256 mismatch: the downloaded APK does not match the published checksum.")
            }
            Log.i(TAG, "SHA-256 verified for ${targetFile.name}")

            prefs.edit()
                .putString(KEY_STATE, STATE_READY_TO_INSTALL)
                .putString(KEY_FILE_PATH, targetFile.absolutePath)
                .putString(KEY_VERSION, targetVersion)
                .putString(KEY_EXPECTED_SHA256, expectedSha256)
                .apply()
            AppUpdateNotificationFactory.notifyReady(applicationContext, targetVersion)

            Result.success(
                workDataOf(
                    KEY_FILE_PATH to targetFile.absolutePath,
                    KEY_VERSION to targetVersion,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AppUpdateDownloadWorker failed: ${e.message}", e)
            if (AppUpdateDownloadPolicy.shouldRetry(runAttemptCount, e)) {
                prefs.edit()
                    .putString(KEY_STATE, STATE_DOWNLOADING)
                    .putString(KEY_ERROR, e.message ?: "Temporary update download failure")
                    .apply()
                Result.retry()
            } else {
                tempFile.delete()
                prefs.edit()
                    .putString(KEY_STATE, STATE_FAILED)
                    .putString(KEY_ERROR, e.message ?: "Download failed")
                    .apply()
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed")))
            }
        }
    }

    private suspend fun writeBody(
        conn: HttpURLConnection,
        tempFile: File,
        alreadyDownloaded: Long,
        announcedTotal: Long,
        prefs: android.content.SharedPreferences,
    ) {
        var downloadedBytes = alreadyDownloaded
        conn.inputStream.use { input ->
            FileOutputStream(tempFile, alreadyDownloaded > 0L).use { output ->
                val buffer = ByteArray(8192)
                var lastUpdate = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (isStopped) {
                        prefs.edit().putString(KEY_STATE, STATE_CANCELLED).apply()
                        throw CancellationException("Application update download cancelled")
                    }
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 250 || downloadedBytes == announcedTotal) {
                        lastUpdate = now
                        val progress = if (announcedTotal > 0) downloadedBytes.toFloat() / announcedTotal.toFloat() else 0f
                        setProgress(
                            workDataOf(
                                KEY_PROGRESS to progress,
                                KEY_DOWNLOADED_BYTES to downloadedBytes,
                                KEY_TOTAL_BYTES to announcedTotal,
                            )
                        )
                    }
                }
                output.flush()
            }
        }
    }

    companion object {
        private const val TAG = "SnapVRKA-AppUpdateWorker"
        const val WORK_NAME = "VRKA_APP_UPDATE_WORK"
        const val PREFS_NAME = "vrka_app_update_prefs"

        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_APK_NAME = "apk_name"
        const val KEY_EXPECTED_SIZE = "expected_size"
        const val KEY_EXPECTED_SHA256 = "expected_sha256"
        const val KEY_TARGET_VERSION = "target_version"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_VERSION = "version"
        const val KEY_ERROR = "error"
        const val KEY_STATE = "state"

        const val STATE_IDLE = "IDLE"
        const val STATE_DOWNLOADING = "DOWNLOADING"
        const val STATE_READY_TO_INSTALL = "READY_TO_INSTALL"
        const val STATE_FAILED = "FAILED"
        const val STATE_CANCELLED = "CANCELLED"
    }
}
