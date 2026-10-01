package com.mvrk.vrka.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The in-app update trust chain: SHA-256, package name, signing certificate and versionCode.
 */
class UpdateVerificationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val shaA = "a".repeat(64)
    private val shaB = "b".repeat(64)

    private fun identity(
        packageName: String = UpdateVerification.EXPECTED_PACKAGE,
        versionCode: Long = 10001L,
        versionName: String = "1.0.1",
        signers: List<String> = listOf(UpdateVerification.PINNED_CERT_SHA256),
    ) = ApkIdentity(packageName, versionCode, versionName, signers)

    private fun verify(
        identity: ApkIdentity? = identity(),
        fileSize: Long = 1_000L,
        actualSha: String? = shaA,
        expectedSha: String? = shaA,
        currentVersionCode: Long = 10000L,
        expectedSize: Long = 0L,
    ) = UpdateVerification.verify(
        identity = identity,
        fileSizeBytes = fileSize,
        actualSha256 = actualSha,
        expectedSha256 = expectedSha,
        currentVersionCode = currentVersionCode,
        expectedSizeBytes = expectedSize,
    )

    private fun rejection(result: UpdateVerificationResult): UpdateRejectionReason {
        assertTrue("expected a rejection but got $result", result is UpdateVerificationResult.Rejected)
        return (result as UpdateVerificationResult.Rejected).reason
    }

    // ---------------------------------------------------------------- hashing helpers

    @Test
    fun sha256HexMatchesTheKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdateVerification.sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun sha256HexOfFileIsStableAndDetectsChanges() {
        val file = tempFolder.newFile("payload.bin")
        file.writeBytes(ByteArray(4096) { (it % 251).toByte() })
        val first = UpdateVerification.sha256Hex(file)
        assertEquals(first, UpdateVerification.sha256Hex(file))
        file.appendBytes(byteArrayOf(1))
        assertFalse(first == UpdateVerification.sha256Hex(file))
    }

    @Test
    fun sha256HexOfMissingFileIsNull() {
        assertNull(UpdateVerification.sha256Hex(java.io.File(tempFolder.root, "nope.bin")))
    }

    // ---------------------------------------------------------------- normalisation

    @Test
    fun hexNormalisationAcceptsCommonFormats() {
        val upper = shaA.uppercase()
        assertEquals(shaA, UpdateVerification.normalizeHex(upper))
        assertEquals(shaA, UpdateVerification.normalizeHex(shaA.chunked(2).joinToString(":")))
        assertEquals(shaA, UpdateVerification.normalizeHex("  $shaA  "))
        assertNull(UpdateVerification.normalizeHex("not-a-hash"))
        assertNull(UpdateVerification.normalizeHex(shaA.dropLast(1)))
        assertNull(UpdateVerification.normalizeHex(null))
    }

    @Test
    fun sha256ComparisonIsCaseInsensitiveButStrict() {
        assertTrue(UpdateVerification.sha256Matches(shaA.uppercase(), shaA))
        assertFalse(UpdateVerification.sha256Matches(shaB, shaA))
        assertFalse(UpdateVerification.sha256Matches(null, shaA))
        assertFalse(UpdateVerification.sha256Matches(shaA, null))
        assertFalse(UpdateVerification.sha256Matches("", shaA))
    }

    @Test
    fun certificateComparisonUsesThePinnedFingerprint() {
        assertTrue(UpdateVerification.certificateMatches(UpdateVerification.PINNED_CERT_SHA256))
        assertTrue(
            UpdateVerification.certificateMatches(
                UpdateVerification.PINNED_CERT_SHA256.chunked(2).joinToString(":").uppercase(),
            ),
        )
        assertFalse(UpdateVerification.certificateMatches(shaB))
        assertFalse(UpdateVerification.certificateMatches(null))
    }

    @Test
    fun pinnedFingerprintIsTheProjectCertificate() {
        assertEquals(
            "90a91740960b8f47f5bf302d1f13dd804853b73a61613e2eb395fefe72e58f27",
            UpdateVerification.PINNED_CERT_SHA256,
        )
        assertEquals("com.hoodmoshla.snapvrka", UpdateVerification.EXPECTED_PACKAGE)
    }

    // ---------------------------------------------------------------- checksum files

    @Test
    fun checksumFileParsingHandlesRealWorldFormats() {
        val name = "SnapVRKA-v1.0.1.apk"
        assertEquals(shaA, UpdateVerification.parseChecksumFile("$shaA  $name"))
        assertEquals(shaA, UpdateVerification.parseChecksumFile("${shaA.uppercase()} *$name"))
        assertEquals(shaA, UpdateVerification.parseChecksumFile("$shaA\n"))
        assertEquals(shaA, UpdateVerification.parseChecksumFile("$shaA  other.apk"))
        assertEquals(shaA, UpdateVerification.parseChecksumFile("$shaA  $name", name))
        assertNull(UpdateVerification.parseChecksumFile("$shaA  other.apk", name))
        assertNull(UpdateVerification.parseChecksumFile(""))
        assertNull(UpdateVerification.parseChecksumFile(null))
        assertNull(UpdateVerification.parseChecksumFile("no checksum here"))
    }

    // ---------------------------------------------------------------- happy path

    @Test
    fun aFullyVerifiedUpdateIsTrusted() {
        assertEquals(UpdateVerificationResult.Trusted, verify())
    }

    @Test
    fun installedBytesSizeIsEnforcedWhenTheReleaseAdvertisesIt() {
        assertEquals(UpdateVerificationResult.Trusted, verify(fileSize = 1_000L, expectedSize = 1_000L))
        assertEquals(
            UpdateRejectionReason.SIZE_MISMATCH,
            rejection(verify(fileSize = 999L, expectedSize = 1_000L)),
        )
    }

    // ---------------------------------------------------------------- rejections

    @Test
    fun emptyDownloadIsRejected() {
        assertEquals(UpdateRejectionReason.EMPTY_FILE, rejection(verify(fileSize = 0L)))
    }

    @Test
    fun aReleaseWithoutAChecksumIsRejected() {
        assertEquals(UpdateRejectionReason.SHA256_MISSING, rejection(verify(expectedSha = null)))
        assertEquals(UpdateRejectionReason.SHA256_MISSING, rejection(verify(expectedSha = "garbage")))
    }

    @Test
    fun aWrongSha256IsRejected() {
        assertEquals(
            UpdateRejectionReason.SHA256_MISMATCH,
            rejection(verify(expectedSha = shaA, actualSha = shaB)),
        )
    }

    @Test
    fun anUnreadableApkIsRejected() {
        assertEquals(UpdateRejectionReason.UNREADABLE_APK, rejection(verify(identity = null)))
    }

    @Test
    fun aDifferentPackageIsRejected() {
        assertEquals(
            UpdateRejectionReason.PACKAGE_MISMATCH,
            rejection(verify(identity = identity(packageName = "com.example.evil"))),
        )
    }

    @Test
    fun aDebugSignatureIsRejected() {
        // The debug keystore fingerprint must never be accepted as a SnapVRKA release.
        assertEquals(
            UpdateRejectionReason.SIGNATURE_MISMATCH,
            rejection(verify(identity = identity(signers = listOf("747aa8e3bfafedf6c35414135393c59e2e3e4db5f7d3d26c5ed5addbeb8cfdbb")))),
        )
        assertEquals(
            UpdateRejectionReason.SIGNATURE_MISMATCH,
            rejection(verify(identity = identity(signers = emptyList()))),
        )
    }

    @Test
    fun aRotationHistoryContainingOurCertificateIsAccepted() {
        assertEquals(
            UpdateVerificationResult.Trusted,
            verify(identity = identity(signers = listOf(shaB, UpdateVerification.PINNED_CERT_SHA256))),
        )
    }

    @Test
    fun downgradesAreRejected() {
        assertEquals(
            UpdateRejectionReason.DOWNGRADE,
            rejection(verify(identity = identity(versionCode = 9_999L), currentVersionCode = 10_000L)),
        )
    }

    @Test
    fun reinstallingTheSameVersionCodeIsRejected() {
        assertEquals(
            UpdateRejectionReason.SAME_VERSION,
            rejection(verify(identity = identity(versionCode = 10_000L), currentVersionCode = 10_000L)),
        )
    }

    @Test
    fun rejectionsCarryAHumanReadableReason() {
        val rejected = verify(expectedSha = shaA, actualSha = shaB) as UpdateVerificationResult.Rejected
        assertTrue(rejected.detail.contains("SHA-256"))
        assertTrue(rejected.detail.isNotBlank())
    }

    // ---------------------------------------------------------------- ordering

    @Test
    fun checksumIsCheckedBeforePackagesAndSignatures() {
        // A file that is both misnamed and wrongly signed must fail on the checksum first.
        assertEquals(
            UpdateRejectionReason.SHA256_MISMATCH,
            rejection(
                verify(
                    identity = identity(packageName = "com.evil", signers = listOf(shaB)),
                    expectedSha = shaA,
                    actualSha = shaB,
                ),
            ),
        )
    }
}
