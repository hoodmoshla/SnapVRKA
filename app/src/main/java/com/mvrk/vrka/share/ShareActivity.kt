package com.mvrk.vrka.share

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvrk.vrka.AppSettings
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
 * Receives a link (ACTION_SEND text or ACTION_VIEW data), probes available media formats and
 * presents the Quick Download sheet. It never opens the Home screen first, and the download
 * itself keeps running in the background after this activity is finished.
 */
class ShareActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { vrkaApplication.downloads }
    private var currentUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentUrl = ShareUrlParser.fromIntent(intent)
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
                        manager.enqueue(request)
                    },
                    onProbe = { target ->
                        manager.ensureRuntimeReady()
                        MediaFormatProbe.probe(target, scope)
                    },
                    onFinish = { finish() },
                )
            }
        }
    }
}

@Composable
private fun ShareContent(
    settings: AppSettings,
    initialUrl: String?,
    onEnqueue: (com.mvrk.vrka.DownloadRequest) -> Unit,
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
    var enqueuedRequest by remember { mutableStateOf<com.mvrk.vrka.DownloadRequest?>(null) }

    LaunchedEffect(initialUrl, attempt) {
        val url = initialUrl
        if (url == null) {
            state = state.copy(
                phase = ProbePhase.FAILED,
                errorMessage = null,
            )
            return@LaunchedEffect
        }
        state = state.copy(phase = ProbePhase.ANALYSING, errorMessage = null, media = null)
        runCatching {
            state = state.copy(phase = ProbePhase.FETCHING_INFO)
            val media = onProbe(url)
            state = state.copy(phase = ProbePhase.READING_FORMATS, media = media)
            val videos = QuickDownloadPlanner.videoOptions(media)
            val audios = QuickDownloadPlanner.audioOptions(media)
            state = state.copy(
                phase = ProbePhase.READY,
                media = media,
                videoOptions = videos,
                audioOptions = audios,
                selectedVideo = videos.firstOrNull(),
                selectedAudio = null,
            )
        }.onFailure {
            state = state.copy(phase = ProbePhase.FAILED, errorMessage = null)
        }
    }

    LaunchedEffect(state.enqueued, enqueuedRequest) {
        if (state.enqueued) {
            Handler(Looper.getMainLooper()).postDelayed({ onFinish() }, 900)
        }
    }

    RtlContainer {
        QuickDownloadSheet(
            state = state,
            onSelectVideo = { option ->
                state = state.copy(selectedVideo = option, selectedAudio = null)
            },
            onSelectAudio = { option ->
                state = state.copy(selectedAudio = option, selectedVideo = null)
            },
            onDownload = {
                val url = state.url
                val audio = state.selectedAudio
                val video = state.selectedVideo
                val request = when {
                    audio != null -> QuickDownloadPlanner.audioRequest(url, audio)
                    video != null -> QuickDownloadPlanner.videoRequest(url, video)
                    else -> null
                }
                if (request != null) {
                    val withLocation = if (!settings.isDownloadLocationConfigured) {
                        request
                    } else {
                        request.copy(destinationTreeUri = settings.outputTreeUri.ifBlank { null })
                    }
                    enqueuedRequest = withLocation
                    runCatching { onEnqueue(withLocation) }
                    state = state.copy(enqueued = true)
                }
            },
            onRetry = { attempt += 1 },
            onDismiss = onFinish,
        )
    }
}
