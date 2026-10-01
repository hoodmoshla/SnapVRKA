package com.mvrk.vrka.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Delta-update planning. SnapVRKA has no on-device patcher yet, so the planner must always fall
 * back to the full APK while still exposing the delta contract.
 */
class UpdateArtifactPlannerTest {

    private fun release(deltas: List<ReleaseAsset> = emptyList()) = AppReleaseInfo(
        tagName = "v1.0.1",
        version = SemanticVersion(1, 0, 1),
        name = "SnapVRKA v1.0.1",
        body = "notes",
        publishedAt = "2026-10-01T00:00:00Z",
        apkDownloadUrl = "https://github.com/hoodmoshla/SnapVRKA/releases/download/v1.0.1/SnapVRKA-v1.0.1.apk",
        apkFileName = "SnapVRKA-v1.0.1.apk",
        apkSizeBytes = 1_000L,
        apkSha256Url = "https://github.com/hoodmoshla/SnapVRKA/releases/download/v1.0.1/SnapVRKA-v1.0.1.apk.sha256",
        apkSha256 = "a".repeat(64),
        deltaAssets = deltas,
    )

    @Test
    fun deltaNamingFollowsTheDocumentedConvention() {
        assertEquals("SnapVRKA-v1.0.1-from-10000.delta", UpdateArtifactPlanner.deltaAssetName("1.0.1", 10_000L))
    }

    @Test
    fun theFullApkIsUsedWhileNoPatcherIsShipped() {
        val plan = UpdateArtifactPlanner.plan(release(), fromVersionCode = 10_000L)
        assertEquals(UpdateArtifactKind.FULL_APK, plan.kind)
        assertTrue(plan.url.endsWith("SnapVRKA-v1.0.1.apk"))
        assertEquals("a".repeat(64), plan.sha256)
    }

    @Test
    fun evenWithADeltaAssetPresentTheFullApkIsChosenWithoutAPatcher() {
        val delta = ReleaseAsset(
            fileName = "SnapVRKA-v1.0.1-from-10000.delta",
            downloadUrl = "https://github.com/hoodmoshla/SnapVRKA/releases/download/v1.0.1/SnapVRKA-v1.0.1-from-10000.delta",
            sizeBytes = 250L,
            sha256 = "b".repeat(64),
        )
        val plan = UpdateArtifactPlanner.plan(release(listOf(delta)), fromVersionCode = 10_000L)
        assertEquals(UpdateArtifactKind.FULL_APK, plan.kind)
    }

    @Test
    fun aDeltaMayOnlyBeUsedTogetherWithAnAvailablePatcher() {
        // Documents the invariant the CI and the updater rely on.
        if (UpdateArtifactPlanner.DELTA_PATCHER_AVAILABLE) {
            val delta = ReleaseAsset(
                fileName = UpdateArtifactPlanner.deltaAssetName("1.0.1", 10_000L),
                downloadUrl = "https://github.com/hoodmoshla/SnapVRKA/releases/download/v1.0.1/delta",
                sizeBytes = 250L,
                sha256 = "b".repeat(64),
            )
            val plan = UpdateArtifactPlanner.plan(release(listOf(delta)), fromVersionCode = 10_000L)
            assertEquals(UpdateArtifactKind.DELTA_PATCH, plan.kind)
        } else {
            assertFalse(UpdateArtifactPlanner.DELTA_PATCHER_AVAILABLE)
        }
    }

    @Test
    fun theFallbackIsAlwaysTheVerifiedFullApk() {
        val plan = UpdateArtifactPlanner.plan(release(), fromVersionCode = 10_000L)
        assertEquals(plan.fallbackApkUrl, plan.url)
        assertEquals("SnapVRKA-v1.0.1.apk", plan.fallbackApkFileName)
        assertEquals("a".repeat(64), plan.fallbackApkSha256)
    }

    @Test
    fun aDeltaForADifferentBaseVersionIsIgnored() {
        val staleDelta = ReleaseAsset(
            fileName = "SnapVRKA-v1.0.1-from-9999.delta",
            downloadUrl = "https://github.com/hoodmoshla/SnapVRKA/releases/download/v1.0.1/stale.delta",
            sizeBytes = 250L,
            sha256 = "b".repeat(64),
        )
        val plan = UpdateArtifactPlanner.plan(release(listOf(staleDelta)), fromVersionCode = 10_000L)
        assertEquals(UpdateArtifactKind.FULL_APK, plan.kind)
    }
}
