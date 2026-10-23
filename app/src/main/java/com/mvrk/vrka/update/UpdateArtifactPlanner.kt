package com.mvrk.vrka.update

/** Which artifact the updater should transfer. */
enum class UpdateArtifactKind {
    /** Complete APK. Always available and always the guaranteed fallback. */
    FULL_APK,

    /** Binary patch against the installed build. */
    DELTA_PATCH,
}

data class UpdateArtifactPlan(
    val kind: UpdateArtifactKind,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    /** Checksum published for this artifact; the final APK checksum is verified separately. */
    val sha256: String?,
    /** Full APK to fall back to when the delta cannot be used for any reason. */
    val fallbackApkUrl: String,
    val fallbackApkFileName: String,
    val fallbackApkSha256: String?,
)

/**
 * Chooses between a delta patch and the full APK.
 *
 * SnapVRKA ships **no on-device patcher yet**, so [DELTA_PATCHER_AVAILABLE] is `false` and every
 * update resolves to the full APK. The planning logic, the naming convention and the fallback
 * contract are in place so a patcher can be enabled later without touching the download or the
 * verification chain — and any delta failure can only ever fall back to the verified full APK.
 */
object UpdateArtifactPlanner {

    /** Flip to true once an on-device patcher is packaged with the app. */
    const val DELTA_PATCHER_AVAILABLE = false

    /** `SnapVRKA-v1.0.1-from-10000.delta` */
    fun deltaAssetName(targetVersion: String, fromVersionCode: Long): String =
        "SnapVRKA-v$targetVersion-from-$fromVersionCode.delta"

    fun plan(release: AppReleaseInfo, fromVersionCode: Long): UpdateArtifactPlan {
        val full = UpdateArtifactPlan(
            kind = UpdateArtifactKind.FULL_APK,
            url = release.apkDownloadUrl,
            fileName = release.apkFileName,
            sizeBytes = release.apkSizeBytes,
            sha256 = release.apkSha256,
            fallbackApkUrl = release.apkDownloadUrl,
            fallbackApkFileName = release.apkFileName,
            fallbackApkSha256 = release.apkSha256,
        )

        // The current release metadata verifies one canonical APK only. Delta metadata is intentionally
        // not part of that contract, so every plan resolves to the signed full APK.
        return full
    }
}
