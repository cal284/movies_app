package com.databounty.movies.youtube

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One full-length movie video on YouTube. */
data class YtMovie(
    val videoId: String,
    val title: String,
    val channel: String,
    val year: Int?          // release year parsed from the title, if present
)

/**
 * Talks to the YouTube Data API v3.
 * Only reads from official distributor channels, so everything returned is
 * uploaded legally by the rights holder.
 *
 * Quota (free tier = 10,000 units/day):
 *   channels.list      = 1 unit   (resolve @handle -> channel ID, cached)
 *   playlistItems.list = 1 unit   (browse a channel's latest uploads)
 *   search.list        = 100 units (search inside a channel)
 */
class YouTubeRepository(private val apiKey: String) {

    private val base = "https://www.googleapis.com/youtube/v3"
    private val channelIdCache = mutableMapOf<String, String>()

    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw IOException("YouTube API error $code: ${body.take(300)}")
        return JSONObject(body)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** "@FilmRiseMovies" -> "UCxxxx..." (cached after the first call) */
    fun resolveChannelId(handle: String): String? {
        channelIdCache[handle]?.let { return it }
        val json = get("$base/channels?part=id&forHandle=${enc(handle.removePrefix("@"))}&key=$apiKey")
        val items = json.optJSONArray("items") ?: return null
        if (items.length() == 0) return null
        val id = items.getJSONObject(0).getString("id")
        channelIdCache[handle] = id
        return id
    }

    /** Latest uploads from a channel. Cheap: uses the channel's "uploads" playlist. */
    fun latestFromChannel(handle: String, max: Int = 50): List<YtMovie> {
        val channelId = resolveChannelId(handle) ?: return emptyList()
        // Every channel ID "UC..." has an uploads playlist "UU..." with the same suffix
        val uploadsPlaylist = "UU" + channelId.substring(2)
        val json = get(
            "$base/playlistItems?part=snippet&maxResults=$max" +
            "&playlistId=$uploadsPlaylist&key=$apiKey"
        )
        val items = json.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val sn = items.getJSONObject(i).getJSONObject("snippet")
            val id = sn.optJSONObject("resourceId")?.optString("videoId") ?: return@mapNotNull null
            toMovie(id, sn.optString("title"), sn.optString("channelTitle"))
        }
    }

    /** Search inside one channel, long videos only (>20 min). Costs 100 quota units. */
    fun searchChannel(handle: String, query: String, max: Int = 15): List<YtMovie> {
        val channelId = resolveChannelId(handle) ?: return emptyList()
        val json = get(
            "$base/search?part=snippet&type=video&videoDuration=long&maxResults=$max" +
            "&channelId=$channelId&q=${enc(query)}&key=$apiKey"
        )
        val items = json.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val item = items.getJSONObject(i)
            val id = item.optJSONObject("id")?.optString("videoId") ?: return@mapNotNull null
            val sn = item.getJSONObject("snippet")
            toMovie(id, sn.optString("title"), sn.optString("channelTitle"))
        }
    }

    private fun toMovie(id: String, rawTitle: String, channel: String): YtMovie? {
        val title = decodeHtml(rawTitle)
        // Skip trailers, clips and deleted videos
        val lower = title.lowercase()
        if (id.isBlank() || lower.contains("trailer") || lower == "private video" || lower == "deleted video") return null
        return YtMovie(id, title, channel, parseYear(title))
    }

    /** Finds a year like "(2019)" or "2019" in the title. */
    private fun parseYear(title: String): Int? =
        Regex("""\b(19[5-9]\d|20[0-4]\d)\b""").find(title)?.value?.toIntOrNull()

    /** The API returns titles with HTML entities, e.g. &#39; for an apostrophe. */
    private fun decodeHtml(s: String) = s
        .replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
        .replace("&lt;", "<").replace("&gt;", ">")
}
