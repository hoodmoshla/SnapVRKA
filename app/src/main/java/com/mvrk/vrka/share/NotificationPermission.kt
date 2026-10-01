package com.mvrk.vrka.share

/**
 * Notification permission helper.
 *
 * The permission is only ever an *enhancement*: downloads, the queue and the foreground service
 * must keep working when it is denied, so nothing in the download path is gated on it.
 */
object NotificationPermission {

    const val RUNTIME_PERMISSION_SDK = 33

    fun shouldRequest(sdkInt: Int, alreadyGranted: Boolean): Boolean =
        sdkInt >= RUNTIME_PERMISSION_SDK && !alreadyGranted
}
