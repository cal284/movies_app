package com.databounty.movies.youtube

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.databounty.movies.BuildConfig

/**
 * Checks whether a specific movie is posted on the official YouTube movie channels,
 * and opens it. Used by the movie detail screen to add a "YouTube · Free" pill.
 *
 * Results are cached per movie for 7 days, because each search costs
 * 100 of your 10,000 free daily YouTube API units per channel.
 */
object YouTubeLookup {

    // Same official channels as the YouTube screen. Keep the two lists in sync.
    val channels = listOf(
        "@FilmRiseMovies",
        "@Popcornflix"
    )

    private const val PREFS = "youtube_lookup_cache"
    private const val CACHE_MS = 7L * 24 * 60 * 60 * 1000

    /** Returns the YouTube video ID for this movie, or null if it isn't on the channels. */
    fun find(ctx: Context, tmdbId: Int, title: String, year: String): String? {
        if (BuildConfig.YOUTUBE_API_KEY.isBlank() || title.isBlank()) return null

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "movie_$tmdbId"
        if (System.currentTimeMillis() - prefs.getLong("${key}_time", 0) < CACHE_MS) {
            return prefs.getString(key, "")?.ifEmpty { null }
        }

        val repo = YouTubeRepository(BuildConfig.YOUTUBE_API_KEY)
        val wanted = normalize(title)
        val wantedYear = year.toIntOrNull()
        var hadError = false
        var found: String? = null

        for (channel in channels) {
            val results = try {
                repo.searchChannel(channel, "$title $year".trim(), 10)
            } catch (e: Exception) {
                hadError = true
                emptyList()
            }
            found = results.firstOrNull { video ->
                val t = normalize(video.title)
                // Short titles (like "X") must be at the start; longer ones can appear anywhere
                val titleMatches = if (wanted.length < 8) t.startsWith(wanted) else t.contains(wanted)
                val yearMatches = wantedYear == null || video.year == null || video.year == wantedYear
                titleMatches && yearMatches
            }?.videoId
            if (found != null) break
        }

        // Don't cache a "not found" caused by an error (e.g. quota used up)
        if (found != null || !hadError) {
            prefs.edit()
                .putString(key, found ?: "")
                .putLong("${key}_time", System.currentTimeMillis())
                .apply()
        }
        return found
    }

    /** "Boo! A Madea Halloween" -> "booamadeahalloween" */
    private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    /** Opens the video in the Fire TV YouTube app. Returns false if nothing could open it. */
    fun open(ctx: Context, videoId: String): Boolean {
        val url = "https://www.youtube.com/watch?v=$videoId"
        for (pkg in listOf("com.amazon.firetv.youtube", "com.google.android.youtube.tv")) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) { }
        }
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
