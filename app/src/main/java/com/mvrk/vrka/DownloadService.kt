package com.mvrk.vrka

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { applicationContext.vrkaApplication.downloads }
    private val completionNotified = mutableSetOf<String>()
    private val observedActive = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val initialJob = manager.jobs.value.firstOrNull { it.state.isForegroundWork }
        showForeground(initialJob)
        serviceScope.launch {
            manager.jobs.collectLatest { jobs ->
                val active = jobs.firstOrNull { it.state.isForegroundWork }
                if (active == null) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                } else {
                    showForeground(active)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_JOB_ID)?.let(manager::cancel)
            ACTION_PAUSE -> intent.getStringExtra(EXTRA_JOB_ID)?.let(manager::pause)
            ACTION_RESUME -> intent.getStringExtra(EXTRA_JOB_ID)?.let(manager::resume)
            ACTION_STOP_IF_IDLE -> {
                if (manager.jobs.value.none { it.state.isForegroundWork }) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        val active = manager.jobs.value.firstOrNull { it.state.isForegroundWork }
        showForeground(active)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showForeground(job: DownloadJob?) {
        val notification = buildNotification(job)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(job: DownloadJob?): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_QUEUE, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = job?.title?.ifBlank { "VRKA download" } ?: "VRKA"
        val status = job?.let(::jobStatusLabel) ?: "Preparing"
        val summary = job?.let {
            val progress = jobProgressSummary(it)
            if (progress == it.detail) "$status • ${requestSummary(it.request)}"
            else "$status • $progress"
        } ?: "Preparing download"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.vrka_purple))
            .setContentTitle(title)
            .setContentText(summary)
            .setSubText("VRKA • $status")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (job != null) {
            if (job.state == JobState.DOWNLOADING && job.progress > 0f) {
                builder.setProgress(100, job.progress.toInt(), false)
            } else if (job.state != JobState.PAUSED) {
                builder.setProgress(100, 0, true)
            }
        }
        if (job != null && job.state == JobState.DOWNLOADING) {
            builder.addAction(R.drawable.ic_pause, getString(R.string.notification_action_pause), jobAction(job.id, ACTION_PAUSE))
        }
        if (job?.state == JobState.PAUSED) {
            // if (job.state == JobState.PAUSED)
            builder.addAction(R.drawable.ic_resume, getString(R.string.notification_action_resume), jobAction(job.id, ACTION_RESUME))
        }
        if (job != null && job.state == JobState.DONE && !completionNotified.contains(job.id)) {
            observedActive.remove(job.id)
            completionNotified += job.id
            builder.setContentTitle(getString(R.string.notification_completed_title))
                .setAutoCancel(true)
                .setOngoing(false)
        }
        if (job != null && !job.state.isTerminal) {
            builder.addAction(0, getString(R.string.action_cancel), jobAction(job.id, ACTION_CANCEL))
        }
        return builder.build()
    }

    private fun jobAction(jobId: String, action: String): PendingIntent =
        PendingIntent.getService(
            this,
            if (action == ACTION_PAUSE) REQUEST_PAUSE else if (action == ACTION_RESUME) REQUEST_RESUME else if (action == ACTION_CANCEL) REQUEST_CANCEL else REQUEST_OPEN,
            Intent(this, DownloadService::class.java).setAction(action).putExtra(EXTRA_JOB_ID, jobId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progress for downloads started in VRKA"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "snapvrka_downloads"
        private const val NOTIFICATION_ID = 4107
        const val ACTION_CANCEL = "com.mvrk.vrka.CANCEL"
        const val ACTION_PAUSE = "com.mvrk.vrka.PAUSE"
        const val ACTION_RESUME = "com.mvrk.vrka.RESUME"
        const val REQUEST_PAUSE = 4101
        const val REQUEST_RESUME = 4102
        const val REQUEST_CANCEL = 4103
        const val REQUEST_OPEN = 4104
        private const val ACTION_STOP_IF_IDLE = "com.mvrk.vrka.STOP_IF_IDLE"
        private const val EXTRA_JOB_ID = "job_id"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopIfIdle(context: Context) {
            context.startService(
                Intent(context, DownloadService::class.java).setAction(ACTION_STOP_IF_IDLE),
            )
        }
    }
}
