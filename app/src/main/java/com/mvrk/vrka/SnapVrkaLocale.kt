package com.mvrk.vrka

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Forces the installed application, activities and notifications into Arabic RTL
 * regardless of the phone language. Upstream Compose strings stay English in source
 * and are translated at display time by [SnapVrkaArabic].
 */
object SnapVrkaLocale {
    const val LANGUAGE_TAG = "ar"

    fun locale(): Locale = Locale.forLanguageTag(LANGUAGE_TAG)

    fun wrap(base: Context): Context {
        val locale = locale()
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun applyTo(context: Context) {
        val locale = locale()
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
