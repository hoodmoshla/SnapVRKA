package com.mvrk.vrka

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Download notification controls: pause / resume the **same** task, and a completion notification
 * that stays until the user taps or swipes it.
 *
 * The service and manager are Android components, so the invariants that matter (no new task,
 * which actions exist, whether the finished notification is dismissible) are asserted against the
 * sources plus the pure [JobState] model.
 */
class DownloadPauseResumeTest {

    private val service = File("src/main/java/com/mvrk/vrka/DownloadService.kt").readText(Charsets.UTF_8)
    private val manager = File("src/main/java/com/mvrk/vrka/VrkaDownloadManager.kt").readText(Charsets.UTF_8)

    // ---------------------------------------------------------------- state model

    @Test
    fun pausedIsANonTerminalState() {
        assertFalse(JobState.PAUSED.isTerminal)
        assertTrue(JobState.PAUSED.isActive)
        assertEquals(R.string.state_paused, JobState.PAUSED.labelRes)
    }

    @Test
    fun pausedKeepsTheForegroundServiceAliveSoTheNotificationStays() {
        // If a paused job were not "foreground work", the service would stop and the
        // notification with the Resume button would disappear.
        assertTrue(JobState.PAUSED.isForegroundWork)
    }

    @Test
    fun pausedLabelIsArabic() {
        val strings = File("src/main/res/values/strings.xml").readText(Charsets.UTF_8)
        assertTrue(strings.contains("""<string name="state_paused">متوقف مؤقتًا</string>"""))
    }

    // ---------------------------------------------------------------- notification actions

    @Test
    fun runningNotificationOffersPauseAndCancel() {
        assertTrue(service.contains("""const val ACTION_PAUSE = "com.mvrk.vrka.PAUSE""""))
        assertTrue(service.contains("getString(R.string.notification_action_pause)"))
        assertTrue(service.contains("R.drawable.ic_pause"))
        assertTrue(service.contains("getString(R.string.action_cancel)"))
        assertTrue(service.contains("ACTION_CANCEL"))
    }

    @Test
    fun pausedNotificationOffersResumeAndCancel() {
        assertTrue(service.contains("""const val ACTION_RESUME = "com.mvrk.vrka.RESUME""""))
        assertTrue(service.contains("""const val ACTION_PAUSE = "com.mvrk.vrka.PAUSE""""))
        assertTrue(service.contains("getString(R.string.notification_action_resume)"))
        assertTrue(service.contains("R.drawable.ic_resume"))
    }

    @Test
    fun notificationChoosesTheActionFromTheJobState() {
        assertTrue(service.contains("if (job.state == JobState.PAUSED)"))
        assertTrue(service.contains("ACTION_RESUME"))
        assertTrue(service.contains("ACTION_PAUSE"))
    }

    @Test
    fun eachActionUsesItsOwnPendingIntentRequestCode() {
        assertTrue(service.contains("REQUEST_PAUSE"))
        assertTrue(service.contains("REQUEST_RESUME"))
        assertTrue(service.contains("REQUEST_CANCEL"))
        assertTrue(service.contains("REQUEST_OPEN"))
    }

    @Test
    fun serviceRoutesPauseAndResumeToTheManager() {
        assertTrue(service.contains("ACTION_PAUSE -> intent.getStringExtra(EXTRA_JOB_ID)?.let(manager::pause)"))
        assertTrue(service.contains("ACTION_RESUME -> intent.getStringExtra(EXTRA_JOB_ID)?.let(manager::resume)"))
    }

    // ---------------------------------------------------------------- completion notification

    @Test
    fun completionNotificationSaysDownloadedAndIsNotOngoing() {
        assertTrue(service.contains("getString(R.string.notification_completed_title)"))
        assertTrue(service.contains(".setAutoCancel(true)"))
        assertTrue(service.contains(".setOngoing(false)"))
    }

    @Test
    fun completionNotificationSurvivesTheServiceStopping() {
        // DETACH keeps the notification after the foreground service goes away.
        assertTrue(service.contains("stopForeground(STOP_FOREGROUND_DETACH)"))
    }

    @Test
    fun completionIsOnlyPostedOncePerJob() {
        assertTrue(service.contains("completionNotified"))
        assertTrue(service.contains("observedActive"))
    }

    @Test
    fun completionTextIsArabic() {
        val strings = File("src/main/res/values/strings.xml").readText(Charsets.UTF_8)
        assertTrue(strings.contains("""<string name="notification_completed_title">تم التحميل</string>"""))
        assertTrue(strings.contains("""<string name="notification_action_pause">إيقاف مؤقت</string>"""))
        assertTrue(strings.contains("""<string name="notification_action_resume">استئناف</string>"""))
    }

    // ---------------------------------------------------------------- same task, no new job

    @Test
    fun pauseSuspendTheRunningTaskWithoutFinishingOrCleaningIt() {
        val pauseBody = functionBody("fun pause(jobId: String)")
        assertTrue(pauseBody.contains("JobState.PAUSED"))
        assertTrue(pauseBody.contains("destroyProcessById(jobId)"))
        // Pausing must not lose progress: no staging cleanup, no cancellation, no new job.
        assertFalse(pauseBody.contains("cleanupStaging"))
        assertFalse(pauseBody.contains("JobState.CANCELLED"))
        assertFalse(pauseBody.contains("UUID.randomUUID()"))
    }

    @Test
    fun resumeContinuesTheSameJobIdInsteadOfCreatingANewTask() {
        val resumeBody = functionBody("fun resume(jobId: String)")
        assertTrue(resumeBody.contains("queue.trySend(jobId)"))
        assertFalse(resumeBody.contains("UUID.randomUUID()"))
        assertFalse(resumeBody.contains("mutate"))
        assertFalse(resumeBody.contains("cleanupStaging"))
    }

    @Test
    fun pausedJobsAreNotReportedAsFailuresAndKeepTheirPartialFile() {
        // A destroyed process raises an exception; the manager must swallow it for paused jobs.
        assertTrue(manager.contains("if (isPaused(jobId))"))
        assertTrue(manager.contains("keeping staging for resume"))
    }

    @Test
    fun trailingProgressCallbacksCannotUnpauseTheJob() {
        assertTrue(manager.contains("!isPaused(job.id)"))
    }

    @Test
    fun terminalStatesClearThePausedFlag() {
        assertTrue(manager.contains("if (state != null && state.isTerminal)"))
        assertTrue(manager.contains("paused -= jobId"))
    }

    @Test
    fun browserTransportStopsWhenPaused() {
        assertTrue(manager.contains("isCancelled = { isCancelled(job.id) || isPaused(job.id) }"))
    }

    /** Extracts a single function body by brace matching starting at its declaration. */
    private fun functionBody(signature: String): String {
        val start = manager.indexOf(signature)
        if (start < 0) return ""
        var depth = 0
        var index = manager.indexOf('{', start)
        if (index < 0) return ""
        val bodyStart = index
        while (index < manager.length) {
            when (manager[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return manager.substring(bodyStart, index + 1)
                }
            }
            index++
        }
        return manager.substring(bodyStart)
    }
}
