package com.mvrk.vrka.update

import java.io.File
import java.security.MessageDigest

/**
 * Identity of an APK on disk, as reported by `PackageManager.getPackageArchiveInfo`.
 * Kept as a plain data class so the whole verification chain stays unit-testable.
 */
data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val signerCertSha256: List<String>,
)

enum class UpdateRejectionReason {
    EMPTY_FILE,
    SIZE_MISMATCH,
    SHA256_MISSING,
    SHA256_MISMATCH,
    PACKAGE_MISMATCH,
    SIGNATURE_MISMATCH,
    DOWNGRADE,
    SAME_VERSION,
    UNREADABLE_APK,
}

sealed interface UpdateVerificationResult {
    data object Trusted : UpdateVerificationResult

    data class Rejected(
        val reason: UpdateRejectionReason,
        val detail: String,
    ) : UpdateVerificationResult
}

/**
 * The complete trust chain for an in-app update:
 *
 * 1. the downloaded bytes match the SHA-256 published in the GitHub release,
 * 2. the archive is a readable APK,
 * 3. it targets the same package (`com.hoodmoshla.snapvrka`),
 * 4. it is signed with the one and only SnapVRKA release certificate,
 * 5. its versionCode is strictly newer than the installed build (no downgrades).
 *
 * Every step is required; a failure at any point rejects the update.
 */
object UpdateVerification {

    /** The permanent SnapVRKA release certificate fingerprint. */
    const val PINNED_CERT_SHA256 = "90a91740960b8f47f5bf302d1f13dd804853b73a61613e2eb395fefe72e58f27"

    /** The permanent application id. */
    const val EXPECTED_PACKAGE = "com.hoodmoshla.snapvrka"

    private val HEX_64 = Regex("^[0-9a-f]{64}$")

    /** Normalises `AB:CD:…`, uppercase or spaced hex into bare lowercase hex, or null. */
    fun normalizeHex(value: String?): String? {
        val cleaned = value
            ?.trim()
            ?.replace(":", "")
            ?.replace(" ", "")
            ?.lowercase()
            ?: return null
        return cleaned.takeIf { HEX_64.matches(it) }
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /** Streaming SHA-256 of a file; null when the file is missing or unreadable. */
    fun sha256Hex(file: File): String? = runCatching {
        if (!file.isFile) return null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    fun sha256Matches(expected: String?, actual: String?): Boolean {
        val e = normalizeHex(expected) ?: return false
        val a = normalizeHex(actual) ?: return false
        return e == a
    }

    fun certificateMatches(signerSha256: String?, pinned: String = PINNED_CERT_SHA256): Boolean =
        sha256Matches(pinned, signerSha256)

    /**
     * Parses a `sha256sum` style checksum file (`<hex>  <file>`), optionally requiring the
     * checksum line to reference [expectedFileName].
     */
    fun parseChecksumFile(content: String?, expectedFileName: String? = null): String? {
        if (content.isNullOrBlank()) return null
        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(Regex("\\s+"))
            val digest = normalizeHex(parts.firstOrNull()) ?: continue
            if (expectedFileName == null) return digest
            val fileName = parts.getOrNull(1)?.removePrefix("*")?.trim()
            if (fileName.isNullOrEmpty() || fileName.equals(expectedFileName, ignoreCase = true)) {
                return digest
            }
        }
        return null
    }

    /**
     * Full verification of a downloaded APK.
     *
     * @param fileSizeBytes real size on disk
     * @param expectedSizeBytes size advertised by the release asset (0 = unknown, not enforced)
     * @param expectedSha256 checksum published in the release `.sha256` asset (null = cannot trust)
     * @param actualSha256 digest of the downloaded file
     */
    fun verify(
        identity: ApkIdentity?,
        fileSizeBytes: Long,
        actualSha256: String?,
        expectedSha256: String?,
        currentVersionCode: Long,
        expectedPackage: String = EXPECTED_PACKAGE,
        expectedSizeBytes: Long = 0L,
        pinnedCertSha256: String = PINNED_CERT_SHA256,
    ): UpdateVerificationResult {
        if (fileSizeBytes <= 0L) {
            return UpdateVerificationResult.Rejected(
                UpdateRejectionReason.EMPTY_FILE,
                "Downloaded file is empty.",
            )
        }
        if (expectedSizeBytes > 0L && fileSizeBytes != expectedSizeBytes) {
            return UpdateVerificationResult.Rejected(
                UpdateRejectionReason.SIZE_MISMATCH,
                "Size mismatch: expected $expectedSizeBytes bytes but the file is $fileSizeBytes bytes.",
            )
        }
        if (normalizeHex(expectedSha256) == null) {
            return UpdateVerificationResult.Rejected(
                UpdateRejectionReason.SHA256_MISSING,
                "The release does not publish a usable SHA-256 checksum.",
            )
        }
        if (!sha256Matches(expectedSha256, actualSha256)) {
            return UpdateVerificationResult.Rejected(
                UpdateRejectionReason.SHA256_MISMATCH,
                "SHA-256 mismatch: the downloaded APK does not match the published checksum.",
            )
        }
        if (identity == null) {
            return UpdateVerificationResult.Rejected(
                UpdateRejectionReason.UNREADABLE_APK,
                "The downloaded file is not a readable APK.",
            )
        }
        if (identity.packageName != expectedPackage) {
            return UpdateVerificationResult.Rejected(
                UpdateRejectionReason.PACKAGE_MISMATCH,
                "Package name mismatch: expected '$expectedPackage' but the APK declares '${identity.packageName}'.",
            )
        }
        if (identity.signerCertSha256.none { certificateMatches(it, pinnedCertSha256) }) {
            return UpdateVerificationResult.Rejected(
                UpdateRejectionReason.SIGNATURE_MISMATCH,
                "Signing certificate mismatch: the APK was not signed with the SnapVRKA release key.",
            )
        }
        if (identity.versionCode < currentVersionCode) {
            return UpdateVerificationResult.Rejected(
                UpdateRejectionReason.DOWNGRADE,
                "Version downgrade rejected: the update has versionCode ${identity.versionCode} but $currentVersionCode is installed.",
            )
        }
        if (identity.versionCode == currentVersionCode) {
            return UpdateVerificationResult.Rejected(
                UpdateRejectionReason.SAME_VERSION,
                "The update has the same versionCode ($currentVersionCode) as the installed build.",
            )
        }
        return UpdateVerificationResult.Trusted
    }
}
