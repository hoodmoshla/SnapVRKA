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

/**
 * Foreground service that owns the download notification.
 *
 * The notification is the same object for the whole lifecycle of a task:
 *  - running  -> [Pause] + [Cancel]
 *  - paused   -> [Resume] + [Cancel]  (the same task, resumed from its partial file)
 *  - finished -> "تم التحميل", no longer ongoing, dismissed only by tapping or swiping it.
 *
 * Pause/Resume never create a new download task: they act on the job id that is already queued.
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { applicationContext.vrkaApplication.downloads }

    /** Jobs this service instance actually drove, so stale completions are never re-posted. */
    private val observedActive = mutableSetOf<String>()
    private val completionNotified = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        showForeground(activeJob())
        serviceScope.launch {
            manager.jobs.collectLatest { jobs -> syncNotification(jobs) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> intent.getStringExtra(EXTRA_JOB_ID)?.let(manager::pause)
            ACTION_RESUME -> intent.getStringExtra(EXTRA_JOB_ID)?.let(manager::resume)
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_JOB_ID)?.let(manager::cancel)
            ACTION_STOP_IF_IDLE -> {
                if (manager.jobs.value.none { it.state.isForegroundWork }) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        showForeground(activeJob())
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun activeJob(): DownloadJob? =
        manager.jobs.value.firstOrNull { it.state.isForegroundWork }

    private fun syncNotification(jobs: List<DownloadJob>) {
        val active = jobs.firstOrNull { it.state.isForegroundWork }
        val finished = jobs.firstOrNull {
            it.state == JobState.DONE && it.id in observedActive && it.id !in completionNotified
        }

        if (finished != null) {
            completionNotified += finished.id
            notifyCompleted(finished)
            if (active == null) {
                // Keep the finished notification visible after the service goes away.
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
                return
            }
        }

        if (active == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            showForeground(active)
        }
    }

    private fun showForeground(job: DownloadJob?) {
        job?.let { observedActive += it.id }
        val notification = buildProgressNotification(job)
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

    private fun buildProgressNotification(job: DownloadJob?): Notification {
        val title = job?.title?.ifBlank { getString(R.string.app_name) } ?: getString(R.string.app_name)
        val status = job?.let { jobStatusLabel(this, it) } ?: getString(R.string.state_preparing)
        val summary = job?.let {
            val progress = jobProgressSummary(this, it)
            if (progress == it.detail) "$status • ${requestSummary(this, it.request)}"
            else "$status • $progress"
        } ?: getString(R.string.state_preparing)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.vrka_purple))
            .setContentTitle(title)
            .setContentText(summary)
            .setSubText("${getString(R.string.app_name)} • $status")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(openAppIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (job != null) {
            if (job.state == JobState.DOWNLOADING && job.progress > 0f) {
                builder.setProgress(100, job.progress.toInt(), false)
            } else if (job.state == JobState.PAUSED) {
                builder.setProgress(100, job.progress.toInt().coerceIn(0, 100), false)
            } else {
                builder.setProgress(100, 0, true)
            }

            if (job.state == JobState.PAUSED) {
                builder.addAction(
                    R.drawable.ic_resume,
                    getString(R.string.notification_action_resume),
                    jobActionIntent(ACTION_RESUME, job.id, REQUEST_RESUME),
                )
            } else {
                builder.addAction(
                    R.drawable.ic_pause,
                    getString(R.string.notification_action_pause),
                    jobActionIntent(ACTION_PAUSE, job.id, REQUEST_PAUSE),
                )
            }
            builder.addAction(
                R.drawable.ic_close,
                getString(R.string.action_cancel),
                jobActionIntent(ACTION_CANCEL, job.id, REQUEST_CANCEL),
            )
        }
        return builder.build()
    }

    /** Completion notification: replaces the progress one and stays until tapped or swiped. */
    private fun notifyCompleted(job: DownloadJob) {
        val title = job.title.ifBlank { getString(R.string.app_name) }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.vrka_purple))
            .setContentTitle(getString(R.string.notification_completed_title))
            .setContentText(title)
            .setSubText(getString(R.string.app_name))
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setProgress(0, 0, false)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN,
        Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_QUEUE, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun jobActionIntent(action: String, jobId: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, DownloadService::class.java)
                .setAction(action)
                .putExtra(EXTRA_JOB_ID, jobId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "snapvrka_downloads"
        private const val NOTIFICATION_ID = 4107

        private const val REQUEST_OPEN = 10
        private const val REQUEST_PAUSE = 11
        private const val REQUEST_RESUME = 12
        private const val REQUEST_CANCEL = 13

        private const val ACTION_PAUSE = "com.mvrk.vrka.PAUSE"
        private const val ACTION_RESUME = "com.mvrk.vrka.RESUME"
        private const val ACTION_CANCEL = "com.mvrk.vrka.CANCEL"
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
