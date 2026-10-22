package com.databounty.movies.youtube

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.databounty.movies.BuildConfig

/**
 * "Free on YouTube" screen.
 * Browses / searches official movie channels and opens the film in the
 * Fire TV YouTube app. UI is built in code (no XML) and works with the
 * Fire TV remote (D-pad up/down, OK to select).
 */
class YouTubeMoviesActivity : Activity() {

    // Official distributor channels that post full movies legally.
    // Check each handle exists by opening youtube.com/<handle> in a browser.
    // Add or remove handles here.
    private val channels = listOf(
        "@FilmRiseMovies",
        "@Popcornflix"
    )

    private val minYear = 2015
    private var onlyRecent = true

    private val repo by lazy { YouTubeRepository(BuildConfig.YOUTUBE_API_KEY) }

    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var yearToggle: Button

    private val gold = Color.parseColor("#E8C766")
    private val rowBg = Color.parseColor("#1E2533")
    private val rowFocused = Color.parseColor("#3A4660")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()

        if (BuildConfig.YOUTUBE_API_KEY.isBlank()) {
            status.text = "No YouTube API key in this build. Add the YOUTUBE_API_KEY secret in GitHub and rebuild."
            return
        }
        loadLatest()
    }

    // ---------- UI ----------

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121722"))
            setPadding(48, 32, 48, 32)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(TextView(this).apply {
            text = "Free on YouTube"
            textSize = 24f
            setTextColor(gold)
            setPadding(0, 0, 32, 0)
        })

        searchBox = EditText(this).apply {
            hint = "Search movies..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    event?.keyCode == KeyEvent.KEYCODE_ENTER) { runSearch(); true } else false
            }
        }
        header.addView(searchBox, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        header.addView(Button(this).apply {
            text = "Search"
            setOnClickListener { runSearch() }
        })

        header.addView(Button(this).apply {
            text = "Latest"
            setOnClickListener { searchBox.setText(""); loadLatest() }
        })

        yearToggle = Button(this).apply {
            text = yearLabel()
            setOnClickListener {
                onlyRecent = !onlyRecent
                text = yearLabel()
                if (searchBox.text.isBlank()) loadLatest() else runSearch()
            }
        }
        header.addView(yearToggle)

        root.addView(header)

        status = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 15f
            setPadding(0, 16, 0, 16)
        }
        root.addView(status)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun yearLabel() = if (onlyRecent) "$minYear+ ✓" else "All years"

    private fun showMovies(movies: List<YtMovie>, label: String) {
        list.removeAllViews()
        val filtered = if (onlyRecent) movies.filter { (it.year ?: 0) >= minYear } else movies
        status.text = if (filtered.isEmpty())
            "$label: nothing found. Try \"All years\" or another search."
        else "$label: ${filtered.size} movies"

        filtered.forEach { m ->
            val row = TextView(this).apply {
                text = "${m.title}\n${m.channel}${m.year?.let { "  ·  $it" } ?: ""}"
                setTextColor(Color.WHITE)
                textSize = 17f
                setPadding(24, 18, 24, 18)
                setBackgroundColor(rowBg)
                isFocusable = true
                isClickable = true
                setOnFocusChangeListener { v, has ->
                    v.setBackgroundColor(if (has) rowFocused else rowBg)
                }
                setOnClickListener { openVideo(m.videoId) }
            }
            list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = 10
            })
        }
        list.getChildAt(0)?.requestFocus()
    }

    // ---------- Data (network runs off the main thread) ----------

    private fun loadLatest() {
        status.text = "Loading latest uploads..."
        Thread {
            try {
                val all = channels.flatMap { repo.latestFromChannel(it) }
                runOnUiThread { showMovies(all, "Latest") }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Error: ${e.message}" }
            }
        }.start()
    }

    private fun runSearch() {
        val q = searchBox.text.toString().trim()
        if (q.isEmpty()) { loadLatest(); return }
        status.text = "Searching \"$q\"..."
        Thread {
            try {
                val all = channels.flatMap { repo.searchChannel(it, q) }
                runOnUiThread { showMovies(all, "Results for \"$q\"") }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Error: ${e.message}" }
            }
        }.start()
    }

    // ---------- Playback hand-off ----------

    private fun openVideo(videoId: String) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        // Try the Fire TV YouTube app first, then the Android TV one, then anything.
        val packages = listOf("com.amazon.firetv.youtube", "com.google.android.youtube.tv")
        for (pkg in packages) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) { }
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Install the YouTube app from the Appstore.", Toast.LENGTH_LONG).show()
        }
    }
}
