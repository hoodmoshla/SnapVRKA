package com.mvrk.vrka.share

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvrk.vrka.AppSettings
import com.mvrk.vrka.DownloadRequest
import com.mvrk.vrka.DownloadService
import com.mvrk.vrka.VrkaTheme
import com.mvrk.vrka.vrkaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Standalone share-sheet entry point.
 *
 * Responsibilities:
 *  - receive a link (ACTION_SEND text or ACTION_VIEW data) and extract the URL,
 *  - probe available media formats with yt-dlp,
 *  - present the Quick Download sheet,
 *  - hand the chosen request to the existing [com.mvrk.vrka.VrkaDownloadManager] queue and start
 *    [DownloadService] so the transfer continues in the background,
 *  - finish immediately so the user returns to the app that shared the link.
 *
 * It deliberately never starts the Home screen and never brings SnapVRKA's main task forward.
 */
class ShareActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { vrkaApplication.downloads }
    private var currentUrl: String? = null

    /**
     * Registration must happen before [onStart]; the result is intentionally ignored because
     * notifications are optional and must never block or delay a download.
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op: the download already started */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentUrl = ShareUrlParser.fromIntent(intent)
        requestNotificationPermissionIfNeeded()
        render(currentUrl)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = ShareUrlParser.fromIntent(intent)
        currentUrl = url
        render(url)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun requestNotificationPermissionIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (NotificationPermission.shouldRequest(Build.VERSION.SDK_INT, granted)) {
            runCatching { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    private fun render(url: String?) {
        setContent {
            val settings by manager.settingsRepository.settings.collectAsStateWithLifecycle()
            VrkaTheme(
                themeMode = settings.themeMode,
                amoled = settings.amoled,
                fontPreference = settings.fontPreference,
            ) {
                ShareContent(
                    settings = settings,
                    initialUrl = url,
                    onEnqueue = { request ->
                        // Hand the job to the existing pipeline and start the foreground service
                        // while this activity is still in the foreground (Android 12+ requires a
                        // foreground context to start a dataSync FGS).
                        manager.enqueue(request)
                        runCatching { DownloadService.start(this) }
                    },
                    onProbe = { target ->
                        manager.ensureRuntimeReady()
                        MediaFormatProbe.probe(target, scope)
                    },
                    onFinish = { finishShareSheet() },
                )
            }
        }
    }

    /**
     * Closing the sheet must land the user back in the app that shared the link.
     * The activity lives in its own task (`taskAffinity=""`), so a plain finish is enough —
     * no host-app launching and no artificial delay.
     */
    private fun finishShareSheet() {
        if (isFinishing) return
        finish()
    }
}

@Composable
private fun ShareContent(
    settings: AppSettings,
    initialUrl: String?,
    onEnqueue: (DownloadRequest) -> Unit,
    onProbe: suspend (String) -> MediaInfo,
    onFinish: () -> Unit,
) {
    var state by remember {
        mutableStateOf(
            QuickDownloadState(
                phase = if (initialUrl == null) ProbePhase.FAILED else ProbePhase.ANALYSING,
                url = initialUrl.orEmpty(),
                errorMessage = null,
            ),
        )
    }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(initialUrl, attempt) {
        val url = initialUrl
        if (url == null) {
            state = state.copy(phase = ProbePhase.FAILED, errorMessage = null)
            return@LaunchedEffect
        }
        state = state.copy(phase = ProbePhase.ANALYSING, errorMessage = null, media = null)
        runCatching {
            state = state.copy(phase = ProbePhase.FETCHING_INFO)
            val media = onProbe(url)
            state = state.copy(phase = ProbePhase.READING_FORMATS, media = media)
            state = QuickDownloadAnalyzer.ready(media, url)
        }.onFailure {
            state = QuickDownloadAnalyzer.failed(url)
        }
    }

    RtlContainer {
        QuickDownloadSheet(
            state = state,
            onSelectVideo = { option ->
                state = QuickDownloadAnalyzer.selectVideo(state, option)
            },
            onSelectAudio = { option ->
                state = QuickDownloadAnalyzer.selectAudio(state, option)
            },
            onDownload = {
                val request = QuickDownloadAnalyzer.requestForSelection(state)
                if (request != null) {
                    val destination = settings.outputTreeUri.takeIf { it.isNotBlank() }
                    val ready = request.copy(destinationTreeUri = destination)
                    runCatching { onEnqueue(ready) }
                    state = state.copy(enqueued = true)
                    // Enqueue is synchronous; closing right away returns the user to the host app
                    // while the download keeps running inside DownloadService.
                    onFinish()
                }
            },
            onRetry = { attempt += 1 },
            onDismiss = onFinish,
        )
    }
}
