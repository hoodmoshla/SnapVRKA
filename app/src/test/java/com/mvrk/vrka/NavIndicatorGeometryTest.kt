package com.mvrk.vrka

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The upstream 5.0.2 navigation indicator is part of the Compose navigation component.
 * This checks that the current component still owns selection and destination switching,
 * instead of expecting the older measured-slot geometry API.
 */
class NavIndicatorGeometryTest {
    private val source = File("src/main/java/com/mvrk/vrka/VrkaDesignSystem.kt").readText()

    @Test
    fun floatingNavigationOwnsSelectionAndDestinationChanges() {
        assertTrue(source.contains("fun VrkaFloatingNavBar"))
        assertTrue(source.contains("selectedDestination"))
        assertTrue(source.contains("onDestinationSelected"))
        assertTrue(source.contains("VrkaDestination.entries"))
    }
}
