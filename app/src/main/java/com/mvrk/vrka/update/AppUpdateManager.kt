package com.mvrk.vrka.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mvrk.vrka.BuildConfig
import com.mvrk.vrka.SettingsRepository
import com.mvrk.vrka.worker.AppUpdateDownloadWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val apiEndpoint: String = DEFAULT_API_ENDPOINT,
) {
    private val _checkState = MutableStateFlow<AppUpdateCheckState>(AppUpdateCheckState.Idle)
    val checkState: StateFlow<AppUpdateCheckState> = _checkState.asStateFlow()

    private val _downloadState = MutableStateFlow<AppUpdateDownloadState>(AppUpdateDownloadState.Idle)
    val downloadState: StateFlow<AppUpdateDownloadState> = _downloadState.asStateFlow()

    private var activeCheckJob: Job? = null
    private var activeDownloadJob: Job? = null

    val currentVersion: SemanticVersion by lazy {
        SemanticVersion.parseOrNull(BuildConfig.VERSION_NAME) ?: SemanticVersion(1, 0, 0)
    }

    init {
        restoreStateAndObserveWorker()
    }

    private fun restoreStateAndObserveWorker() {
        val prefs = context.getSharedPreferences(AppUpdateDownloadWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val savedState = prefs.getString(AppUpdateDownloadWorker.KEY_STATE, null)
        val savedFilePath = prefs.getString(AppUpdateDownloadWorker.KEY_FILE_PATH, null)
        if (savedState == AppUpdateDownloadWorker.STATE_READY_TO_INSTALL && !savedFilePath.isNullOrBlank()) {
            val file = File(savedFilePath)
            if (file.exists() && file.length() > 0L) {
                _downloadState.value = AppUpdateDownloadState.ReadyToInstall(file)
            }
        }

        runCatching {
            val workManager = WorkManager.getInstance(context)
            scope.launch {
                workManager.getWorkInfosForUniqueWorkFlow(AppUpdateDownloadWorker.WORK_NAME).collect { workInfos ->
                    val workInfo = workInfos.firstOrNull() ?: return@collect
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getFloat(AppUpdateDownloadWorker.KEY_PROGRESS, 0f)
                            val downloaded = workInfo.progress.getLong(AppUpdateDownloadWorker.KEY_DOWNLOADED_BYTES, 0L)
                            val total = workInfo.progress.getLong(AppUpdateDownloadWorker.KEY_TOTAL_BYTES, 0L)
                            _downloadState.value = AppUpdateDownloadState.Downloading(progress, downloaded, total)
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val filePath = workInfo.outputData.getString(AppUpdateDownloadWorker.KEY_FILE_PATH)
                                ?: prefs.getString(AppUpdateDownloadWorker.KEY_FILE_PATH, null)
                            if (filePath != null) {
                                val file = File(filePath)
                                if (file.exists() && file.length() > 0L) {
                                    _downloadState.value = AppUpdateDownloadState.ReadyToInstall(file)
                                }
                            }
                        }
                        WorkInfo.State.FAILED -> {
                            val error = workInfo.outputData.getString(AppUpdateDownloadWorker.KEY_ERROR)
                                ?: prefs.getString(AppUpdateDownloadWorker.KEY_ERROR, null)
                                ?: context.getString(com.mvrk.vrka.R.string.update_failed)
                            _downloadState.value = AppUpdateDownloadState.Error(error)
                        }
                        WorkInfo.State.CANCELLED -> {
                            _downloadState.value = AppUpdateDownloadState.Idle
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    fun checkForUpdate(isManual: Boolean = false) {
        if (activeCheckJob?.isActive == true) {
            Log.d(TAG, "Update check already in progress")
            return
        }

        activeCheckJob = scope.launch {
            try {
                if (!isManual) {
                    if (!settingsRepository.settings.value.autoUpdateCheck) {
                        Log.d(TAG, "Automatic update checks are disabled in settings")
                        _checkState.value = AppUpdateCheckState.Idle
                        return@launch
                    }
                    val lastCheck = settingsRepository.getLastAppUpdateCheckTimestamp()
                    val now = System.currentTimeMillis()
                    if (now - lastCheck < AUTO_CHECK_INTERVAL_MS) {
                        Log.d(TAG, "Skipping automatic update check; the cached result is still fresh")
                        return@launch
                    }
                }

                _checkState.value = AppUpdateCheckState.Checking

                val release = fetchLatestRelease()
                val now = System.currentTimeMillis()
                settingsRepository.setLastAppUpdateCheckTimestamp(now)

                if (release != null && release.version.isNewerThan(currentVersion)) {
                    Log.i(TAG, "Update available: ${release.version} (current: $currentVersion)")
                    _checkState.value = AppUpdateCheckState.UpdateAvailable(release)
                } else {
                    Log.i(TAG, "App is up-to-date (version: $currentVersion)")
                    _checkState.value = AppUpdateCheckState.UpToDate(BuildConfig.VERSION_NAME)
                }
            } catch (ce: CancellationException) {
                Log.d(TAG, "Update check cancelled")
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                if (isManual) {
                    val message = when (e) {
                        is RateLimitException -> e.message ?: "GitHub API rate limit reached. Try again later."
                        is IOException -> "Network error checking for updates."
                        else -> e.message ?: "Unable to check for updates."
                    }
                    _checkState.value = AppUpdateCheckState.Error(message)
                } else {
                    // Silent failure on startup/background
                    _checkState.value = AppUpdateCheckState.Idle
                }
            }
        }
    }

    fun dismissUpdate() {
        _checkState.value = AppUpdateCheckState.Idle
        _downloadState.value = AppUpdateDownloadState.Idle
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(AppUpdateDownloadWorker.WORK_NAME)
        }
    }

    fun resetState() {
        _checkState.value = AppUpdateCheckState.Idle
        _downloadState.value = AppUpdateDownloadState.Idle
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(AppUpdateDownloadWorker.WORK_NAME)
        }
    }

    fun downloadAndInstall(release: AppReleaseInfo) {
        if (_downloadState.value is AppUpdateDownloadState.Downloading) return

        _downloadState.value = AppUpdateDownloadState.Downloading(0f, 0L, release.apkSizeBytes)

        scope.launch {
            try {
                // The published SHA-256 is fetched *before* downloading so the transfer can never
                // succeed without something to verify the bytes against.
                val sha256 = resolvePublishedSha256(release)
                if (sha256 == null) {
                    Log.e(TAG, "Release ${release.tagName} publishes no usable SHA-256 checksum")
                    _downloadState.value = AppUpdateDownloadState.Error(
                        context.getString(com.mvrk.vrka.R.string.update_sha_missing),
                        UpdateRejectionReason.SHA256_MISSING,
                    )
                    return@launch
                }
                context.getSharedPreferences(AppUpdateDownloadWorker.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(AppUpdateDownloadWorker.KEY_EXPECTED_SHA256, sha256)
                    .apply()
                enqueueDownloadWorker(release, sha256)
            } catch (e: Exception) {
                Log.e(TAG, "Could not prepare the update download: ${e.message}", e)
                _downloadState.value = AppUpdateDownloadState.Error(
                    e.message ?: context.getString(com.mvrk.vrka.R.string.update_failed),
                )
            }
        }
    }

    private suspend fun resolvePublishedSha256(release: AppReleaseInfo): String? {
        UpdateVerification.normalizeHex(release.apkSha256)?.let { return it }
        val checksumUrl = release.apkSha256Url?.takeIf { it.isNotBlank() } ?: return null
        val text = withContext(Dispatchers.IO) { httpGet(checksumUrl) }
        return UpdateVerification.parseChecksumFile(text, release.apkFileName)
    }

    private fun enqueueDownloadWorker(release: AppReleaseInfo, sha256: String) {
        runCatching {
            val workManager = WorkManager.getInstance(context)
            val inputData = workDataOf(
                AppUpdateDownloadWorker.KEY_DOWNLOAD_URL to release.apkDownloadUrl,
                AppUpdateDownloadWorker.KEY_APK_NAME to release.apkFileName,
                AppUpdateDownloadWorker.KEY_EXPECTED_SIZE to release.apkSizeBytes,
                AppUpdateDownloadWorker.KEY_EXPECTED_SHA256 to sha256,
                AppUpdateDownloadWorker.KEY_TARGET_VERSION to release.version.toString(),
            )
            val workRequest = OneTimeWorkRequestBuilder<AppUpdateDownloadWorker>()
                .setInputData(inputData)
                .build()

            workManager.enqueueUniqueWork(
                AppUpdateDownloadWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
        }.onFailure { error ->
            Log.e(TAG, "Failed to enqueue AppUpdateDownloadWorker: ${error.message}", error)
            _downloadState.value = AppUpdateDownloadState.Error(error.message ?: context.getString(com.mvrk.vrka.R.string.update_failed))
        }
    }

    /** versionCode of the running build, used to reject downgrades. */
    val currentVersionCode: Long by lazy {
        runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrDefault(0L)
    }

    /** Reads package name, versionCode and signing certificate digests straight from the archive. */
    fun readApkIdentity(file: File): ApkIdentity? = runCatching {
        if (!file.isFile || file.length() == 0L) return null
        val pm = context.packageManager
        val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } ?: return null

        val certDigests = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = archiveInfo.signingInfo
            val signers = when {
                signingInfo == null -> null
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
            signers?.forEach { certDigests.add(UpdateVerification.sha256Hex(it.toByteArray())) }
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.signatures?.forEach { certDigests.add(UpdateVerification.sha256Hex(it.toByteArray())) }
        }

        ApkIdentity(
            packageName = archiveInfo.packageName.orEmpty(),
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            },
            versionName = archiveInfo.versionName.orEmpty(),
            signerCertSha256 = certDigests,
        )
    }.getOrNull()

    /**
     * Runs the complete trust chain on a downloaded APK and, only when it passes, hands it to the
     * Android package installer. Returns the verification outcome so the UI can explain a refusal.
     */
    fun installVerifiedApk(
        file: File,
        expectedSha256: String?,
        expectedSizeBytes: Long = 0L,
    ): UpdateVerificationResult {
        _downloadState.value = AppUpdateDownloadState.Verifying
        val actualSha256 = UpdateVerification.sha256Hex(file)
        val result = UpdateVerification.verify(
            identity = readApkIdentity(file),
            fileSizeBytes = if (file.exists()) file.length() else 0L,
            actualSha256 = actualSha256,
            expectedSha256 = expectedSha256,
            currentVersionCode = currentVersionCode,
            expectedSizeBytes = expectedSizeBytes,
        )
        return when (result) {
            is UpdateVerificationResult.Trusted -> {
                Log.i(TAG, "Update verified (sha256 + package + signature + versionCode); launching installer")
                installApk(file)
                result
            }
            is UpdateVerificationResult.Rejected -> {
                Log.e(TAG, "Update rejected: ${result.reason} - ${result.detail}")
                runCatching { if (file.exists()) file.delete() }
                _downloadState.value = AppUpdateDownloadState.Error(result.detail, result.reason)
                result
            }
        }
    }

    /** The SHA-256 that the completed download was verified against. */
    fun expectedSha256ForPendingUpdate(): String? = UpdateVerification.normalizeHex(
        context.getSharedPreferences(AppUpdateDownloadWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(AppUpdateDownloadWorker.KEY_EXPECTED_SHA256, null),
    )

    fun installApk(file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(manageIntent)
                    return
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            _downloadState.value = AppUpdateDownloadState.Installing
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            _downloadState.value = AppUpdateDownloadState.Error("${context.getString(com.mvrk.vrka.R.string.update_failed)}: ${e.message}")
        }
    }

    private suspend fun fetchLatestRelease(): AppReleaseInfo? = withContext(Dispatchers.IO) {
        val jsonStr = httpGet(apiEndpoint)
        parseReleaseJson(jsonStr)
    }

    private fun httpGet(urlString: String, maxRedirects: Int = 5): String {
        var currentUrl = urlString
        var redirects = 0

        while (redirects < maxRedirects) {
            val url = validateHttpsUrl(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS

            try {
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        return conn.inputStream.bufferedReader().use { it.readText() }
                    }
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307, 308 -> {
                        val location = conn.getHeaderField("Location")
                            ?: throw IOException("HTTP redirect $code without Location header")
                        val nextUrl = resolveRedirectUrl(url, location)
                        validateHttpsUrl(nextUrl)
                        currentUrl = nextUrl
                        redirects++
                    }
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        val reset = conn.getHeaderField("X-RateLimit-Reset")
                        throw RateLimitException("GitHub API rate limit exceeded. Reset: $reset")
                    }
                    else -> throw IOException("GitHub API returned HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects: $redirects")
    }

    private suspend fun downloadFileWithProgress(
        urlStr: String,
        targetFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        var currentUrl = urlStr
        var redirects = 0
        val maxRedirects = 5

        while (redirects < maxRedirects) {
            val url = validateHttpsUrl(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = DOWNLOAD_READ_TIMEOUT_MS

            try {
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val totalBytes = conn.contentLengthLong
                        var downloadedBytes = 0L
                        val tempFile = File("${targetFile.absolutePath}.tmp")

                        conn.inputStream.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var read: Int
                                var lastUpdate = 0L

                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    downloadedBytes += read
                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdate > 100 || downloadedBytes == totalBytes) {
                                        lastUpdate = now
                                        onProgress(downloadedBytes, totalBytes)
                                    }
                                }
                                output.flush()
                            }
                        }

                        if (tempFile.renameTo(targetFile)) {
                            onProgress(downloadedBytes, totalBytes)
                            return@withContext
                        } else {
                            tempFile.copyTo(targetFile, overwrite = true)
                            tempFile.delete()
                            onProgress(downloadedBytes, totalBytes)
                            return@withContext
                        }
                    }
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307, 308 -> {
                        val location = conn.getHeaderField("Location")
                            ?: throw IOException("HTTP redirect $code without Location header")
                        val nextUrl = resolveRedirectUrl(url, location)
                        validateHttpsUrl(nextUrl)
                        currentUrl = nextUrl
                        redirects++
                    }
                    else -> throw IOException("Download failed with HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects downloading APK")
    }

    companion object {
        private const val TAG = "VRKA-AppUpdater"
        private const val USER_AGENT = "SnapVRKA-AppUpdater"
        private const val DEFAULT_API_ENDPOINT =
            "https://api.github.com/repos/hoodmoshla/SnapVRKA/releases/latest"
        const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L // 86,400,000 ms

        /**
         * Minimum age of a cached check before an *automatic* check may run again
         * (app launch or return to foreground). Manual checks always bypass this.
         */
        const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours
        private const val CONNECT_TIMEOUT_MS = 6000
        private const val READ_TIMEOUT_MS = 6000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 30000
        private const val BUFFER_SIZE = 8192

        val ALLOWED_UPDATE_HOSTS = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "raw.githubusercontent.com",
        )

        val APK_NAME_PATTERN = Regex("""^SnapVRKA-v\d+\.\d+\.\d+\.apk$""", RegexOption.IGNORE_CASE)

        fun isApprovedHost(host: String): Boolean {
            val h = host.lowercase()
            return h in ALLOWED_UPDATE_HOSTS || (h.endsWith(".githubusercontent.com") && !h.startsWith("."))
        }

        fun validateHttpsUrl(urlString: String): URL {
            val url = try {
                URL(urlString)
            } catch (e: Exception) {
                throw SecurityException("Malformed update URL: $urlString", e)
            }
            if (!url.protocol.equals("https", ignoreCase = true)) {
                throw SecurityException("Insecure protocol '${url.protocol}' rejected (HTTPS required): $urlString")
            }
            val host = url.host.lowercase()
            if (!isApprovedHost(host)) {
                throw SecurityException("Host '$host' is not an approved GitHub update host: $urlString")
            }
            return url
        }

        fun resolveRedirectUrl(baseUrl: URL, location: String): String {
            val trimmed = location.trim()
            if (trimmed.isEmpty()) throw IOException("Empty redirect Location header")
            return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                trimmed
            } else {
                URL(baseUrl, trimmed).toString()
            }
        }

        @Volatile
        private var instance: AppUpdateManager? = null

        fun getInstance(context: Context, settingsRepository: SettingsRepository): AppUpdateManager {
            return instance ?: synchronized(this) {
                instance ?: AppUpdateManager(context.applicationContext, settingsRepository).also {
                    instance = it
                }
            }
        }

        fun parseReleaseJson(jsonString: String): AppReleaseInfo? {
            val trimmed = jsonString.trim()
            val json = if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                if (array.length() == 0) return null
                var found: JSONObject? = null
                for (i in 0 until array.length()) {
                    val candidate = array.getJSONObject(i)
                    if (!candidate.optBoolean("draft", false) &&
                        !candidate.optBoolean("prerelease", false)
                    ) {
                        found = candidate
                        break
                    }
                }
                found ?: return null
            } else {
                JSONObject(trimmed)
            }

            if (json.optBoolean("draft", false)) return null
            if (json.optBoolean("prerelease", false)) return null

            val tagName = json.optString("tag_name", "").trim()
            val version = SemanticVersion.parseOrNull(tagName) ?: return null

            val name = json.optString("name", "").ifBlank { tagName }
            val body = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")

            val assets = json.optJSONArray("assets") ?: return null
            var chosenAsset: JSONObject? = null
            var checksumAsset: JSONObject? = null
            val deltaAssets = mutableListOf<ReleaseAsset>()
            val expectedVersionApk = "SnapVRKA-v$version.apk"
            val expectedChecksumApk = "$expectedVersionApk.sha256"

            // APK asset selection must require the expected SnapVRKA APK naming convention:
            // SnapVRKA-vX.Y.Z.apk. Reject unrelated APK assets (including legacy VRKA-Android APKs).
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (APK_NAME_PATTERN.matches(assetName)) {
                    if (assetName.equals(expectedVersionApk, ignoreCase = true)) {
                        chosenAsset = asset
                        break
                    } else if (chosenAsset == null) {
                        chosenAsset = asset
                    }
                }
            }

            val asset = chosenAsset ?: return null
            val apkDownloadUrl = asset.optString("browser_download_url", "")
            if (!apkDownloadUrl.startsWith("https://", ignoreCase = true)) return null
            try {
                validateHttpsUrl(apkDownloadUrl)
            } catch (_: Exception) {
                return null
            }

            val apkFileName = asset.optString("name", expectedVersionApk)
            val apkSizeBytes = asset.optLong("size", 0L)

            // Second pass: checksum + optional delta assets for the chosen APK.
            for (i in 0 until assets.length()) {
                val candidate = assets.getJSONObject(i)
                val candidateName = candidate.optString("name", "")
                val candidateUrl = candidate.optString("browser_download_url", "")
                if (!candidateUrl.startsWith("https://", ignoreCase = true)) continue
                if (runCatching { validateHttpsUrl(candidateUrl) }.isFailure) continue

                when {
                    candidateName.equals("$apkFileName.sha256", ignoreCase = true) ||
                        candidateName.equals(expectedChecksumApk, ignoreCase = true) ->
                        checksumAsset = candidate

                    candidateName.endsWith(".delta", ignoreCase = true) ->
                        deltaAssets.add(
                            ReleaseAsset(
                                fileName = candidateName,
                                downloadUrl = candidateUrl,
                                sizeBytes = candidate.optLong("size", 0L),
                                sha256 = null,
                            ),
                        )
                }
            }

            return AppReleaseInfo(
                tagName = tagName,
                version = version,
                name = name,
                body = body,
                publishedAt = publishedAt,
                apkDownloadUrl = apkDownloadUrl,
                apkFileName = apkFileName,
                apkSizeBytes = apkSizeBytes,
                apkSha256Url = checksumAsset?.optString("browser_download_url", "")?.takeIf { it.isNotBlank() },
                apkSha256 = null,
                htmlUrl = json.optString("html_url", ""),
                deltaAssets = deltaAssets,
            )
        }
    }

    class RateLimitException(message: String) : IOException(message)
}
