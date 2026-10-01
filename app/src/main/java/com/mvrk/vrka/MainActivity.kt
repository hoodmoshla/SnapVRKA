package com.mvrk.vrka

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val openQueueRequests = MutableStateFlow(0L)

    /**
     * Asks for POST_NOTIFICATIONS once, without blocking anything: the queue, the foreground
     * service and the downloads all work when the permission is denied — only the progress
     * notification is hidden.
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            VrkaRoot(vrkaApplication.downloads, openQueueRequests)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_QUEUE, false) == true) {
            openQueueRequests.value = System.currentTimeMillis()
            intent.removeExtra(EXTRA_OPEN_QUEUE)
        }
        val urlToEnqueue = intent?.getStringExtra(EXTRA_URL)
            ?: (if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null)
            ?: (if (intent?.action == Intent.ACTION_VIEW) intent.dataString else null)
        if (!urlToEnqueue.isNullOrBlank()) {
            vrkaApplication.downloads.enqueue(DownloadRequest(url = urlToEnqueue.trim()))
            openQueueRequests.value = System.currentTimeMillis()
            intent?.removeExtra(EXTRA_URL)
        }
    }

    companion object {
        const val EXTRA_OPEN_QUEUE = "com.mvrk.vrka.OPEN_QUEUE"
        const val EXTRA_URL = "com.mvrk.vrka.URL"
    }
}

