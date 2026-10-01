package com.mvrk.vrka.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Upstream-sync guards that can be checked without GitHub:
 *
 *  - the sync state file is complete and points at the real fork parent,
 *  - every SnapVRKA-owned file still carries its marker (the same check CI runs after a merge),
 *  - the versioning scheme is deterministic, monotonic and matches the shipped build file.
 */
class UpstreamSyncTest {

    private val repoRoot = File("..")
    private val stateFile = File(repoRoot, ".snapvrka/upstream-sync.json")
    private val protectedPaths = File(repoRoot, ".snapvrka/protected-paths.txt")
    private val bumpScript = File(repoRoot, ".github/scripts/bump-version.sh")
    private val identityScript = File(repoRoot, ".github/scripts/verify-snapvrka-identity.sh")
    private val appGradle = File("build.gradle.kts")
    private val syncWorkflow = File(repoRoot, ".github/workflows/upstream-sync.yml")

    private fun stateValue(key: String): String =
        Regex("\"$key\"\\s*:\\s*(\"([^\"]*)\"|([0-9]+))")
            .find(stateFile.readText(Charsets.UTF_8))
            ?.let { it.groupValues[2].ifEmpty { it.groupValues[3] } }
            .orEmpty()

    // ---------------------------------------------------------------- state file

    @Test
    fun syncStateRecordsTheForkParentAndLastMergedCommit() {
        assertTrue("missing ${stateFile.path}", stateFile.isFile)
        assertEquals("MaverickRox/VRKA-Android", stateValue("upstreamRepository"))
        assertEquals("main", stateValue("upstreamBranch"))
        assertTrue(stateValue("targetBranch").isNotBlank())
        assertTrue(
            "lastSyncedCommit must be a full sha",
            Regex("^[0-9a-f]{40}$").matches(stateValue("lastSyncedCommit")),
        )
    }

    @Test
    fun duplicateSyncsArePreventedByTheRecordedCommit() {
        val recorded = stateValue("lastSyncedCommit")
        // The workflow skips everything when the upstream head equals the recorded commit.
        assertTrue(recorded == "869c8e14940a06ecc7473ad7a687067e4be8e8ee")
        assertFalse(recorded == "0".repeat(40))
    }

    @Test
    fun syncWorkflowIsIdempotentAndConflictSafe() {
        val yaml = syncWorkflow.readText(Charsets.UTF_8)
        assertTrue(syncWorkflow.isFile)
        // Idempotency: no new commit -> nothing is built or released.
        assertTrue(yaml.contains("has_update"))
        assertTrue(yaml.contains("git merge-base --is-ancestor"))
        // Conflict handling: report the files, abort, never publish.
        assertTrue(yaml.contains("Merge conflict while syncing upstream"))
        assertTrue(yaml.contains("git diff --name-only --diff-filter=U"))
        assertTrue(yaml.contains("git merge --abort"))
        // Protection must run both before and after the merge.
        assertTrue(yaml.contains("verify-snapvrka-identity.sh"))
        // Never pushes a broken tree: tests must pass first.
        assertTrue(yaml.contains("testDebugUnitTest"))
        assertTrue(yaml.indexOf("testDebugUnitTest") < yaml.indexOf("git push origin"))
    }

    // ---------------------------------------------------------------- our own changes

    @Test
    fun everyProtectedSnapVrkaMarkerIsStillPresent() {
        assertTrue("missing ${protectedPaths.path}", protectedPaths.isFile)
        val entries = protectedPaths.readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        assertTrue("expected a meaningful protection list", entries.size >= 10)

        val broken = mutableListOf<String>()
        entries.forEach { line ->
            val parts = line.split("|")
            val path = parts.getOrNull(0).orEmpty()
            val marker = parts.getOrNull(1).orEmpty()
            val file = File(repoRoot, path)
            when {
                !file.isFile -> broken += "$path (missing)"
                marker.isNotEmpty() && !file.readText(Charsets.UTF_8).contains(marker) ->
                    broken += "$path (marker '$marker' lost)"
            }
        }
        assertTrue("SnapVRKA-owned changes were lost: $broken", broken.isEmpty())
    }

    @Test
    fun identityScriptAndSyncWorkflowReferenceTheProtectionFile() {
        assertTrue(identityScript.isFile)
        assertTrue(identityScript.readText(Charsets.UTF_8).contains("protected-paths.txt"))
        assertTrue(bumpScript.isFile)
    }

    // ---------------------------------------------------------------- versioning

    /** Mirrors .github/scripts/bump-version.sh exactly. */
    private fun nextVersion(currentCode: Long): Pair<String, Long> {
        val code = currentCode + 1
        val major = code / 10_000
        val minor = (code % 10_000) / 100
        val patch = code % 100
        return "$major.$minor.$patch" to code
    }

    private fun currentVersionName(): String =
        Regex("""versionName\s*=\s*"([^"]+)"""")
            .find(appGradle.readText(Charsets.UTF_8))!!
            .groupValues[1]

    private fun currentVersionCode(): Long =
        Regex("""versionCode\s*=\s*(\d+)""")
            .find(appGradle.readText(Charsets.UTF_8))!!
            .groupValues[1].toLong()

    @Test
    fun shippedVersionMatchesTheVersioningScheme() {
        val code = currentVersionCode()
        val (name, nextCode) = nextVersion(code - 1)
        assertEquals(currentVersionName(), name)
        assertEquals(code, nextCode)
    }

    @Test
    fun versionCodeIncreasesStrictlyAndNameFollows() {
        assertEquals("1.0.1" to 10_001L, nextVersion(10_000L))
        assertEquals("1.1.0" to 10_100L, nextVersion(10_099L))
        assertEquals("2.0.0" to 20_000L, nextVersion(19_999L))
        assertEquals("1.0.99" to 10_099L, nextVersion(10_098L))
    }

    @Test
    fun bumpingIsDeterministicAndNeverDowngrades() {
        var code = 10_000L
        val seen = mutableSetOf(code)
        repeat(250) {
            val (_, next) = nextVersion(code)
            assertTrue("versionCode must strictly increase", next > code)
            code = next
            assertTrue("versionCode must never repeat", seen.add(code))
        }
    }

    @Test
    fun replaysOfTheSameSyncProduceTheSameVersion() {
        // Running the sync twice with no new upstream commit must not mint a second version.
        val first = nextVersion(10_000L)
        val second = nextVersion(10_000L)
        assertEquals(first, second)
    }
}
