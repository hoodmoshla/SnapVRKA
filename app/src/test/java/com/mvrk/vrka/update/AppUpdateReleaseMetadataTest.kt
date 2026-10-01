package com.mvrk.vrka.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Release-metadata discovery for the in-app updater: which asset is the APK, which one is the
 * checksum, and what happens for releases that do not follow the convention.
 */
class AppUpdateReleaseMetadataTest {

    private fun releaseJson(
        tag: String = "v1.0.1",
        assets: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ) = """
        {
          "tag_name": "$tag",
          "name": "SnapVRKA $tag",
          "body": "release notes",
          "published_at": "2026-10-01T00:00:00Z",
          "html_url": "https://github.com/hoodmoshla/SnapVRKA/releases/tag/$tag",
          "draft": $draft,
          "prerelease": $prerelease,
          "assets": [$assets]
        }
    """.trimIndent()

    private fun asset(name: String, tag: String = "v1.0.1", size: Long = 1234L) = """
        {
          "name": "$name",
          "size": $size,
          "browser_download_url": "https://github.com/hoodmoshla/SnapVRKA/releases/download/$tag/$name"
        }
    """.trimIndent()

    @Test
    fun apkAndChecksumAssetsAreBothDiscovered() {
        val release = AppUpdateManager.parseReleaseJson(
            releaseJson(
                assets = listOf(
                    asset("SnapVRKA-v1.0.1.apk", size = 166_000_000L),
                    asset("SnapVRKA-v1.0.1.apk.sha256", size = 86L),
                ).joinToString(","),
            ),
        )
        assertNotNull(release)
        assertEquals("v1.0.1", release!!.tagName)
        assertEquals(SemanticVersion(1, 0, 1), release.version)
        assertEquals("SnapVRKA-v1.0.1.apk", release.apkFileName)
        assertEquals(166_000_000L, release.apkSizeBytes)
        assertNotNull("the .sha256 asset must be picked up", release.apkSha256Url)
        assertTrue(release.apkSha256Url!!.endsWith("SnapVRKA-v1.0.1.apk.sha256"))
        assertTrue(release.isVerifiable)
        assertTrue(release.htmlUrl.endsWith("/v1.0.1"))
    }

    @Test
    fun aReleaseWithoutAChecksumIsStillParsedButNotVerifiable() {
        val release = AppUpdateManager.parseReleaseJson(
            releaseJson(assets = asset("SnapVRKA-v1.0.1.apk")),
        )
        assertNotNull(release)
        assertNull(release!!.apkSha256Url)
        assertFalse(release.isVerifiable)
    }

    @Test
    fun deltaAssetsAreCollectedForFutureUse() {
        val release = AppUpdateManager.parseReleaseJson(
            releaseJson(
                assets = listOf(
                    asset("SnapVRKA-v1.0.1.apk"),
                    asset("SnapVRKA-v1.0.1.apk.sha256"),
                    asset("SnapVRKA-v1.0.1-from-10000.delta", size = 250L),
                ).joinToString(","),
            ),
        )
        assertNotNull(release)
        assertEquals(1, release!!.deltaAssets.size)
        assertEquals("SnapVRKA-v1.0.1-from-10000.delta", release.deltaAssets.first().fileName)
    }

    @Test
    fun unrelatedAndLegacyAssetsAreIgnored() {
        assertNull(
            AppUpdateManager.parseReleaseJson(
                releaseJson(
                    assets = listOf(
                        asset("VRKA-Android-v1.0.1.apk"),
                        asset("SnapVRKA-arm64-v8a.apk"),
                        asset("SnapVRKA-v1.0.1.zip"),
                    ).joinToString(","),
                ),
            ),
        )
    }

    @Test
    fun draftAndPrereleaseReleasesAreNeverOffered() {
        assertNull(AppUpdateManager.parseReleaseJson(releaseJson(assets = asset("SnapVRKA-v1.0.1.apk"), draft = true)))
        assertNull(AppUpdateManager.parseReleaseJson(releaseJson(assets = asset("SnapVRKA-v1.0.1.apk"), prerelease = true)))
    }

    @Test
    fun aChecksumAssetForAnotherVersionIsNotUsed() {
        val release = AppUpdateManager.parseReleaseJson(
            releaseJson(
                assets = listOf(
                    asset("SnapVRKA-v1.0.1.apk", size = 10L),
                    asset("SnapVRKA-v1.0.0.apk.sha256", tag = "v1.0.0", size = 86L),
                ).joinToString(","),
            ),
        )
        assertNotNull(release)
        // Only the checksum that belongs to the selected APK may be used.
        assertFalse(release!!.apkSha256Url?.endsWith("SnapVRKA-v1.0.0.apk.sha256") == true)
    }

    @Test
    fun updateAvailabilityUsesSemanticVersionOrdering() {
        val current = SemanticVersion.parseOrNull("1.0.0")!!
        assertTrue(SemanticVersion.parseOrNull("1.0.1")!!.isNewerThan(current))
        assertFalse(current.isNewerThan(current))
        assertFalse(SemanticVersion.parseOrNull("0.9.9")!!.isNewerThan(current))
    }
}
