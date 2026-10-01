package com.mvrk.vrka.share

import com.mvrk.vrka.AppSettings
import com.mvrk.vrka.ThemeMode
import com.mvrk.vrka.resolveDarkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Theme system: Light / Dark / Auto, with Auto following the device setting and being the default.
 */
class ThemeModeTest {

    @Test
    fun autoIsTheDefaultForFreshInstalls() {
        assertEquals(ThemeMode.AUTO, AppSettings().themeMode)
    }

    @Test
    fun allThreeModesAreAvailable() {
        assertEquals(
            listOf(ThemeMode.AUTO, ThemeMode.LIGHT, ThemeMode.DARK),
            ThemeMode.entries.toList(),
        )
    }

    @Test
    fun autoFollowsTheSystem() {
        assertTrue(resolveDarkMode(ThemeMode.AUTO, systemInDarkTheme = true))
        assertFalse(resolveDarkMode(ThemeMode.AUTO, systemInDarkTheme = false))
    }

    @Test
    fun lightModeIgnoresTheSystem() {
        assertFalse(resolveDarkMode(ThemeMode.LIGHT, systemInDarkTheme = true))
        assertFalse(resolveDarkMode(ThemeMode.LIGHT, systemInDarkTheme = false))
    }

    @Test
    fun darkModeIgnoresTheSystem() {
        assertTrue(resolveDarkMode(ThemeMode.DARK, systemInDarkTheme = true))
        assertTrue(resolveDarkMode(ThemeMode.DARK, systemInDarkTheme = false))
    }

    @Test
    fun switchingTheSystemModeFlipsAutoImmediately() {
        // Same persisted selection, different device setting -> different effective theme.
        val persisted = ThemeMode.fromStorage("AUTO")
        val before = resolveDarkMode(persisted, systemInDarkTheme = false)
        val after = resolveDarkMode(persisted, systemInDarkTheme = true)
        assertNotEquals(before, after)
    }

    @Test
    fun storedSelectionIsRestoredVerbatim() {
        assertEquals(ThemeMode.AUTO, ThemeMode.fromStorage("AUTO"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorage("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("DARK"))
    }

    @Test
    fun legacyAndUnknownValuesFallBackSafely() {
        // Legacy VRKA stored "AMOLED" for its dark theme.
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("AMOLED"))
        assertEquals(ThemeMode.AUTO, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.AUTO, ThemeMode.fromStorage(""))
        assertEquals(ThemeMode.AUTO, ThemeMode.fromStorage("something-else"))
    }

    @Test
    fun everyModeHasItsOwnArabicLabel() {
        val ids = ThemeMode.entries.map { it.labelRes }
        assertTrue(ids.all { it != 0 })
        assertEquals(ids.size, ids.toSet().size)
    }

    // ---------------------------------------------------------------- persistence + coverage

    @Test
    fun selectionIsPersistedByTheSettingsRepository() {
        val repository = File("src/main/java/com/mvrk/vrka/SettingsRepository.kt")
            .readText(Charsets.UTF_8)
        assertTrue(repository.contains("preferences[Keys.themeMode] = value.name"))
        assertTrue(repository.contains("ThemeMode.fromStorage(storedTheme)"))
        // Default for a fresh install.
        assertTrue(repository.contains("val themeMode: ThemeMode = ThemeMode.AUTO"))
    }

    @Test
    fun themeIsResolvedCentrallyAndAppliesToEverySurface() {
        val theme = File("src/main/java/com/mvrk/vrka/Theme.kt").readText(Charsets.UTF_8)
        assertTrue(theme.contains("resolveDarkMode(themeMode, isSystemInDarkTheme())"))

        // Both entry points render through VrkaTheme, so Home / Queue / History / Settings and the
        // Quick Download share sheet all follow the same resolved theme.
        val root = File("src/main/java/com/mvrk/vrka/VrkaRoot.kt").readText(Charsets.UTF_8)
        assertTrue(root.contains("VrkaTheme("))
        assertTrue(root.contains("themeMode = settings.themeMode"))

        val share = File("src/main/java/com/mvrk/vrka/share/ShareActivity.kt").readText(Charsets.UTF_8)
        assertTrue(share.contains("VrkaTheme("))
        assertTrue(share.contains("themeMode = settings.themeMode"))
    }

    @Test
    fun sheetsAndDialogsDoNotHardCodeDarkOnlyBackgrounds() {
        // The Quick Download sheet must read colours from the theme tokens, not fixed black.
        val sheet = File("src/main/java/com/mvrk/vrka/share/QuickDownloadScreen.kt")
            .readText(Charsets.UTF_8)
        assertTrue(sheet.contains("VrkaTokens.SurfaceElevated"))
        assertFalse(sheet.contains("Color(0xFF000000)"))
        assertFalse(sheet.contains("Color.Black"))
    }
}
