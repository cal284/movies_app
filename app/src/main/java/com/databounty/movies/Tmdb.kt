package com.databounty.movies

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Movie(
    val id: Int,
    val title: String,
    val year: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val rating: Double
)

data class Genre(val id: Int, val name: String)

data class Provider(val id: Int, val name: String, val kind: String)

data class MoviePage(val movies: List<Movie>, val page: Int, val totalPages: Int)

data class WatchInfo(val providers: List<Provider>, val link: String?)

/** Returns null when the key is missing or JSON null (optString would return "null"). */
fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key)

object Tmdb {
    private const val BASE = "https://api.themoviedb.org/3"
    const val IMG = "https://image.tmdb.org/t/p/"

    private val key: String get() = BuildConfig.TMDB_API_KEY.trim()
    val hasKey: Boolean get() = key.isNotEmpty()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Accepts either the short v3 "API Key" or the long v4 "Read Access Token". */
    private fun get(path: String, params: Map<String, String>): JSONObject {
        val useBearer = key.length > 60
        val all = if (useBearer) params else params + ("api_key" to key)
        val query = all.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val conn = URL("$BASE$path?$query").openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("Accept", "application/json")
        if (useBearer) conn.setRequestProperty("Authorization", "Bearer $key")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code == 401) throw RuntimeException("TMDB rejected the API key (401). Check the TMDB_API_KEY secret.")
            if (code !in 200..299) throw RuntimeException("TMDB error $code")
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parsePage(o: JSONObject): MoviePage {
        val list = ArrayList<Movie>()
        val arr = o.optJSONArray("results")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                list += Movie(
                    id = m.optInt("id"),
                    title = m.str("title") ?: "Untitled",
                    year = (m.str("release_date") ?: "").take(4),
                    overview = m.str("overview") ?: "",
                    posterPath = m.str("poster_path"),
                    backdropPath = m.str("backdrop_path"),
                    rating = m.optDouble("vote_average", 0.0)
                )
            }
        }
        return MoviePage(list, o.optInt("page", 1), o.optInt("total_pages", 1))
    }

    fun discover(s: Settings, genreId: Int?, page: Int): MoviePage {
        val p = linkedMapOf(
            "language" to "en-US",
            "sort_by" to "popularity.desc",
            "include_adult" to "false",
            "include_video" to "false",
            "vote_count.gte" to "20",
            "page" to page.toString(),
            "watch_region" to s.region,
            "with_watch_monetization_types" to if (s.freeOnly) "free|ads" else "flatrate|free|ads"
        )
        if (s.yearFrom > 0) p["primary_release_date.gte"] = "${s.yearFrom}-01-01"
        if (s.yearTo > 0) p["primary_release_date.lte"] = "${s.yearTo}-12-31"
        if (genreId != null) p["with_genres"] = genreId.toString()
        return parsePage(get("/discover/movie", p))
    }

    fun search(query: String, page: Int): MoviePage =
        parsePage(
            get(
                "/search/movie",
                mapOf(
                    "query" to query,
                    "language" to "en-US",
                    "include_adult" to "false",
                    "page" to page.toString()
                )
            )
        )

    fun genres(): List<Genre> {
        val arr = get("/genre/movie/list", mapOf("language" to "en-US")).optJSONArray("genres")
            ?: return emptyList()
        return (0 until arr.length()).map {
            val g = arr.getJSONObject(it)
            Genre(g.optInt("id"), g.optString("name"))
        }
    }

    fun watchProviders(movieId: Int, region: String): WatchInfo {
        val r = get("/movie/$movieId/watch/providers", emptyMap())
            .optJSONObject("results")?.optJSONObject(region)
            ?: return WatchInfo(emptyList(), null)
        val out = ArrayList<Provider>()
        val seen = HashSet<Int>()
        val kinds = listOf("free" to "Free", "ads" to "Free with ads", "flatrate" to "Subscription")
        for ((kind, label) in kinds) {
            val arr = r.optJSONArray(kind) ?: continue
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val id = p.optInt("provider_id")
                if (seen.add(id)) out += Provider(id, p.optString("provider_name"), label)
            }
        }
        return WatchInfo(out, r.str("link"))
    }
}
