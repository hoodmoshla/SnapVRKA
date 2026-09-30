package com.mvrk.vrka.share

import android.content.Context
import com.mvrk.vrka.R
import java.util.Locale

/**
 * Formats byte sizes for the Arabic UI.
 *
 * Real sizes are shown as-is; estimated sizes are always prefixed with "حوالي"
 * so the UI never presents a fabricated number as exact.
 */
object SizeFormatter {

    private const val KB = 1024.0
    private const val MB = KB * 1024.0
    private const val GB = MB * 1024.0

    fun format(context: Context, bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return context.getString(R.string.quick_size_unknown)
        return context.getString(R.string.quick_size_exact, formatValue(context, bytes))
    }

    fun formatEstimated(context: Context, bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return context.getString(R.string.quick_size_unknown)
        return context.getString(R.string.quick_size_estimated, formatValue(context, bytes))
    }

    fun format(context: Context, bytes: Long?, approximate: Boolean): String =
        if (approximate) formatEstimated(context, bytes) else format(context, bytes)

    fun formatValue(context: Context, bytes: Long): String = when {
        bytes >= GB -> context.getString(
            R.string.size_value_gb,
            String.format(Locale.US, "%.1f", bytes / GB),
        )
        bytes >= MB -> context.getString(
            R.string.size_value_mb,
            String.format(Locale.US, "%.0f", bytes / MB),
        )
        else -> context.getString(
            R.string.size_value_kb,
            String.format(Locale.US, "%.0f", bytes / KB),
        )
    }
}
