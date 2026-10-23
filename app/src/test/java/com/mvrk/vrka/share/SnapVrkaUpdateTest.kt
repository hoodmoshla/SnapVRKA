package com.mvrk.vrka.share

import com.mvrk.vrka.update.AppUpdateManager
import com.mvrk.vrka.update.SemanticVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapVrkaUpdateTest {

    @Test
    fun apkNamePatternAcceptsSnapVrkaNaming() {
        assertTrue(AppUpdateManager.APK_NAME_PATTERN.matches("SnapVRKA-v5.0.2.1-arm64-v8a-release.apk"))
        assertTrue(AppUpdateManager.APK_NAME_PATTERN.matches("SnapVRKA-v5.0.2.2-arm64-v8a-release.apk"))
        assertTrue(AppUpdateManager.APK_NAME_PATTERN.matches("SnapVRKA-v5.0.3.1-arm64-v8a-release.apk"))
        assertTrue(AppUpdateManager.APK_NAME_PATTERN.matches("SnapVRKA-v5.1.0.1-arm64-v8a-release.apk"))
        assertTrue(AppUpdateManager.APK_NAME_PATTERN.matches("SnapVRKA-v1.0.2.1-arm64-v8a-release.apk"))
    }

    @Test
    fun apkNamePatternRejectsLegacyVrkaAndroidNaming() {
        assertFalse(AppUpdateManager.APK_NAME_PATTERN.matches("VRKA-Android-v5.0.2-arm64-v8a-release.apk"))
        assertFalse(AppUpdateManager.APK_NAME_PATTERN.matches("VRKA-Android-v4.5.2-arm64-v8a-release.apk"))
        assertFalse(AppUpdateManager.APK_NAME_PATTERN.matches("SnapVRKA-arm64-v8a.apk"))
        assertFalse(AppUpdateManager.APK_NAME_PATTERN.matches("SnapVRKA-v1.0.1.zip"))
        assertFalse(AppUpdateManager.APK_NAME_PATTERN.matches("other-app.apk"))
    }

    @Test
    fun releaseJsonWithSnapVrkaAssetIsAccepted() {
        val release = AppUpdateManager.parseReleaseJson(releaseJson("v1.0.1", "SnapVRKA-v1.0.1-arm64-v8a-release.apk"))
        assertNotNull(release)
        assertEquals("v1.0.1", release!!.tagName)
        assertEquals("SnapVRKA-v1.0.1-arm64-v8a-release.apk", release.apkFileName)
        assertEquals(SemanticVersion(1, 0, 1), release.version)
    }

    @Test
    fun releaseJsonWithOnlyLegacyAssetIsRejected() {
        val release = AppUpdateManager.parseReleaseJson(releaseJson("v1.0.1", "VRKA-Android-v1.0.1-arm64-v8a-release.apk"))
        assertNull(release)
    }

    @Test
    fun releaseJsonWithADifferentSnapVrkaVersionAssetIsRejected() {
        val release = AppUpdateManager.parseReleaseJson(releaseJson("v1.0.1", "SnapVRKA-v1.0.0-arm64-v8a-release.apk"))
        assertNull(release)
    }

    @Test
    fun releaseJsonFromTheUpstreamRepositoryIsRejected() {
        val json = releaseJson("v5.0.2", "SnapVRKA-v5.0.2-arm64-v8a-release.apk")
            .replace(
                "https://github.com/hoodmoshla/SnapVRKA/releases/download/v5.0.2/SnapVRKA-v5.0.2-arm64-v8a-release.apk",
                "https://objects.githubusercontent.com/MaverickRox/VRKA-Android/releases/download/v5.0.2/SnapVRKA-v5.0.2-arm64-v8a-release.apk",
            )
            .replace("SnapVRKA-v5.0.2-arm64-v8a-release.apk", "SnapVRKA-v5.0.2-arm64-v8a-release.apk")
        assertNull(AppUpdateManager.parseReleaseJson(json))
    }

    @Test
    fun releaseJsonWithNonHttpsDownloadUrlIsRejected() {
        val json = """
            {
              "tag_name": "v1.0.1",
              "name": "SnapVRKA v1.0.1",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "SnapVRKA-v1.0.1-arm64-v8a-release.apk",
                  "browser_download_url": "http://github.com/hoodmoshla/SnapVRKA/releases/download/v1.0.1/SnapVRKA-v1.0.1-arm64-v8a-release.apk",
                  "size": 1234
                }
              ]
            }
        """.trimIndent()
        assertNull(AppUpdateManager.parseReleaseJson(json))
    }

    @Test
    fun releaseJsonWithUnapprovedHostIsRejected() {
        val json = """
            {
              "tag_name": "v1.0.1",
              "name": "SnapVRKA v1.0.1",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "SnapVRKA-v1.0.1-arm64-v8a-release.apk",
                  "browser_download_url": "https://evil.example.com/SnapVRKA-v1.0.1-arm64-v8a-release.apk",
                  "size": 1234
                }
              ]
            }
        """.trimIndent()
        assertNull(AppUpdateManager.parseReleaseJson(json))
    }

    @Test
    fun draftAndPrereleaseReleasesAreIgnored() {
        assertNull(AppUpdateManager.parseReleaseJson(releaseJson("v1.0.1", "SnapVRKA-v1.0.1-arm64-v8a-release.apk", draft = true)))
        assertNull(AppUpdateManager.parseReleaseJson(releaseJson("v1.0.1", "SnapVRKA-v1.0.1-arm64-v8a-release.apk", prerelease = true)))
    }

    private fun releaseJson(
        tag: String,
        assetName: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ): String = """
        {
          "tag_name": "$tag",
          "name": "SnapVRKA $tag",
          "body": "notes",
          "published_at": "2026-01-01T00:00:00Z",
          "draft": $draft,
          "prerelease": $prerelease,
          "assets": [
            {
              "name": "$assetName",
              "browser_download_url": "https://github.com/hoodmoshla/SnapVRKA/releases/download/$tag/$assetName",
              "size": 12345
            }
          ]
        }
    """.trimIndent()
}
