package com.mvrk.vrka.share

import android.content.Intent

/**
 * Extracts the first http/https URL from shared text or a viewed link.
 *
 * Only http and https are accepted; every other scheme (file://, content://, javascript:, ...)
 * is rejected so the downloader can never be pointed at a local or dangerous URI.
 */
object ShareUrlParser {

    private val URL_PATTERN = Regex("""https?://[^\s<>"'`\[\]{}]+""", RegexOption.IGNORE_CASE)
    private val TRAILING_JUNK = charArrayOf(
        '.', ',', ';', ':', '!', '?', '"', '\'', ')', ']', '}', '>', '«', '»', '،', '؛',
        '—', '–', '…', '\u00a0',
    )

    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = URL_PATTERN.find(text) ?: return null
        return normalize(match.value)
    }

    fun normalize(candidate: String?): String? {
        val trimmed = candidate?.trim()?.trim(*TRAILING_JUNK)?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        val afterScheme = trimmed.substringAfter("://", "")
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isBlank()) return null
        val host = authority.substringAfter('@')
        if (host.isBlank() || host.startsWith('.') || !host.contains('.') && host != "localhost") {
            return null
        }
        return trimmed
    }

    /**
     * Resolves the download target from a [Intent].
     *
     * ACTION_SEND with EXTRA_TEXT may contain surrounding prose ("شاهد هذا الفيديو: <url>"),
     * so the URL is extracted from the text body. ACTION_VIEW is expected to carry the URL
     * directly in [Intent.getDataString].
     */
    fun fromIntent(action: String?, dataString: String?, extraText: String?): String? = when {
        action == Intent.ACTION_VIEW -> normalize(dataString) ?: extractUrl(dataString)
        action == Intent.ACTION_SEND -> extractUrl(extraText) ?: normalize(extraText)
        else -> extractUrl(extraText) ?: normalize(dataString)
    }

    fun fromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val extra = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        return fromIntent(intent.action, intent.dataString, extra)
    }
}
