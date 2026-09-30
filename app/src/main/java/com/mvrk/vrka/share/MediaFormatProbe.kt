package com.mvrk.vrka.share

import com.mvrk.vrka.DownloadRequestFactory
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads media metadata from yt-dlp and converts the raw JSON into typed models.
 *
 * The UI never touches raw JSON: it only receives [MediaInfo].
 */
object MediaFormatProbe {

    /** In-flight probes keyed by URL so a double-share does not spawn duplicate yt-dlp runs. */
    private val inFlight = ConcurrentHashMap<String, Deferred<MediaInfo>>()

    /**
     * Probes [url], de-duplicating concurrent probes for the same link.
     *
     * The entry is always removed afterwards so a deliberate re-download later still re-probes.
     */
    suspend fun probe(url: String, scope: CoroutineScope): MediaInfo =
        try {
            inFlight.computeIfAbsent(url) {
                scope.async(Dispatchers.IO) { runProbe(url) }
            }.await()
        } finally {
            inFlight.remove(url)
        }

    private fun runProbe(url: String): MediaInfo {
        val request = DownloadRequestFactory.info(
            com.mvrk.vrka.DownloadRequest(url = url),
        )
        val response = YoutubeDL.getInstance().getInfo(request)
        val json = response.out.orEmpty()
        val fallbackTitle = response.title
        return parseMediaInfo(json, fallbackTitle)
            ?: throw IllegalStateException("Empty yt-dlp metadata for $url")
    }

    /** Pure parsing entry point (unit-testable, no Android dependencies). */
    fun parseMediaInfo(json: String, fallbackTitle: String? = null): MediaInfo? {
        if (json.isBlank()) return null
        val root = runCatching { JSONObject(json.trim()) }.getOrNull() ?: return null
        val node = firstEntry(root) ?: root
        return parseNode(node, fallbackTitle)
    }

    private fun firstEntry(root: JSONObject): JSONObject? {
        val entries = root.optJSONArray("entries") ?: return null
        if (entries.length() == 0) return null
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            return entry
        }
        return null
    }

    private fun parseNode(node: JSONObject, fallbackTitle: String?): MediaInfo {
        val formats = mutableListOf<MediaFormat>()
        node.optJSONArray("formats")?.let { array ->
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { formats.add(parseFormat(it)) }
            }
        }
        return MediaInfo(
            id = node.optStringOrNull("id"),
            title = node.optStringOrNull("title") ?: fallbackTitle ?: "",
            durationSeconds = node.optDoubleOrNull("duration"),
            thumbnailUrl = node.optStringOrNull("thumbnail"),
            webpageUrl = node.optStringOrNull("webpage_url"),
            uploader = node.optStringOrNull("uploader") ?: node.optStringOrNull("channel"),
            formats = formats,
        )
    }

    private fun parseFormat(json: JSONObject): MediaFormat {
        val acodec = json.optStringOrNull("acodec")
        val vcodec = json.optStringOrNull("vcodec")
        return MediaFormat(
            formatId = json.optStringOrNull("format_id") ?: "",
            extension = json.optStringOrNull("ext") ?: "",
            container = json.optStringOrNull("container"),
            width = json.optIntOrNull("width"),
            height = json.optIntOrNull("height"),
            fps = json.optDoubleOrNull("fps"),
            vcodec = vcodec,
            acodec = acodec,
            abr = json.optDoubleOrNull("abr"),
            vbr = json.optDoubleOrNull("vbr"),
            tbr = json.optDoubleOrNull("tbr"),
            filesize = json.optLongOrNull("filesize"),
            filesizeApprox = json.optLongOrNull("filesize_approx"),
            protocol = json.optStringOrNull("protocol"),
        )
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (!has(name) || isNull(name)) null else optInt(name).takeIf { it > 0 }

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name).takeIf { it > 0L }

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() && it > 0.0 }
}
