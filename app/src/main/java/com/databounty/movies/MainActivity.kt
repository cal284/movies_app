package com.databounty.movies

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    private lateinit var settings: Settings
    private lateinit var adapter: MovieAdapter
    private lateinit var grid: RecyclerView
    private lateinit var status: TextView
    private lateinit var searchBox: EditText
    private lateinit var genreRow: LinearLayout
    private lateinit var regionChip: TextView
    private lateinit var yearsChip: TextView
    private lateinit var freeChip: TextView

    private var generation = 0
    private var page = 0
    private var totalPages = 1
    private var loading = false
    private var genreId: Int? = null
    private var searchQuery: String? = null
    private val genreChips = ArrayList<Pair<Int?, TextView>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings.load(this)

        status = findViewById(R.id.statusText)
        searchBox = findViewById(R.id.searchBox)
        genreRow = findViewById(R.id.genreRow)
        grid = findViewById(R.id.grid)

        // Header buttons
        val header = findViewById<LinearLayout>(R.id.headerButtons)
        header.addView(Ui.chip(this, "Search") { doSearch() })
        regionChip = Ui.chip(this, "") { pickRegion() }
        yearsChip = Ui.chip(this, "") { pickYears() }
        freeChip = Ui.chip(this, "") { toggleFree() }
        header.addView(regionChip)
        header.addView(yearsChip)
        header.addView(freeChip)
        updateHeader()

        searchBox.setOnEditorActionListener { _, _, _ ->
            doSearch()
            true
        }

        // Poster grid sized to the screen
        val dm = resources.displayMetrics
        val widthDp = dm.widthPixels / dm.density - 96f
        val span = (widthDp / 152f).toInt().coerceIn(3, 8)
        val lm = GridLayoutManager(this, span)
        grid.layoutManager = lm
        adapter = MovieAdapter { openDetail(it) }
        grid.adapter = adapter
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (lm.findLastVisibleItemPosition() >= adapter.itemCount - span * 2) loadNext()
            }
        })

        if (!Tmdb.hasKey) {
            status.text = "No TMDB API key in this build. Add the TMDB_API_KEY secret in your GitHub repo and run the build again."
            return
        }
        loadGenres()
        startNew()
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdownNow()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (searchQuery != null) {
            searchQuery = null
            searchBox.setText("")
            startNew()
        } else {
            super.onBackPressed()
        }
    }

    // ---------- Background work ----------

    private fun <T> bg(checkGeneration: Boolean, work: () -> T, onOk: (T) -> Unit) {
        val myGen = generation
        io.execute {
            try {
                val r = work()
                main.post {
                    if (!isFinishing && (!checkGeneration || myGen == generation)) onOk(r)
                }
            } catch (e: Exception) {
                main.post {
                    if (!isFinishing && (!checkGeneration || myGen == generation)) {
                        loading = false
                        status.text = "Couldn't load: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
            }
        }
    }

    // ---------- Loading ----------

    private fun startNew() {
        generation++
        page = 0
        totalPages = 1
        loading = false
        adapter.clear()
        updateStatusLine()
        loadNext()
    }

    private fun loadNext() {
        if (!Tmdb.hasKey || loading || page >= totalPages) return
        loading = true
        if (page == 0) status.text = "Loading…"
        val next = page + 1
        val s = settings
        val q = searchQuery
        val g = genreId
        bg(true, { if (q != null) Tmdb.search(q, next) else Tmdb.discover(s, g, next) }) { res ->
            loading = false
            page = res.page
            totalPages = res.totalPages.coerceAtMost(500)
            val wasEmpty = adapter.itemCount == 0
            adapter.add(res.movies)
            if (adapter.itemCount == 0) {
                status.text = if (q != null) "No movies match \"$q\"."
                else "No movies found for ${Regions.name(s.region)} with these filters. Try another region, years or genre."
            } else {
                updateStatusLine()
            }
            if (wasEmpty && adapter.itemCount > 0) {
                grid.post { grid.getChildAt(0)?.requestFocus() }
            }
        }
    }

    private fun loadGenres() {
        bg(false, { Tmdb.genres() }) { genres ->
            genreRow.removeAllViews()
            genreChips.clear()
            addGenreChip(null, "All")
            genres.forEach { addGenreChip(it.id, it.name) }
            markSelectedGenre()
        }
    }

    private fun addGenreChip(id: Int?, name: String) {
        val chip = Ui.chip(this, name) {
            genreId = id
            searchQuery = null
            searchBox.setText("")
            markSelectedGenre()
            startNew()
        }
        if (genreRow.childCount == 0) {
            (chip.layoutParams as LinearLayout.LayoutParams).marginStart = 0
        }
        genreRow.addView(chip)
        genreChips += id to chip
    }

    private fun markSelectedGenre() {
        genreChips.forEach { (id, chip) -> chip.isSelected = searchQuery == null && id == genreId }
    }

    // ---------- Search ----------

    private fun doSearch() {
        val q = searchBox.text.toString().trim()
        hideKeyboard()
        if (q.isEmpty()) {
            if (searchQuery != null) {
                searchQuery = null
                startNew()
            }
            return
        }
        searchQuery = q
        markSelectedGenre()
        startNew()
        grid.requestFocus()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
        searchBox.clearFocus()
    }

    // ---------- Settings ----------

    private fun updateHeader() {
        regionChip.text = "Region: ${settings.region}"
        yearsChip.text = settings.yearsLabel()
        freeChip.text = if (settings.freeOnly) "Free only ✓" else "Free + subscription"
    }

    private fun updateStatusLine() {
        val where = Regions.name(settings.region)
        status.text = if (searchQuery != null) {
            "Search results for \"$searchQuery\" (all services). Open a title to see where it streams in $where."
        } else {
            val what = if (settings.freeOnly) "Free movies" else "Streaming movies"
            "$what in $where · ${settings.yearsLabel()} — set your VPN to $where for playback."
        }
    }

    private fun applySettings(new: Settings) {
        settings = new
        settings.save(this)
        updateHeader()
        startNew()
    }

    private fun pickRegion() {
        val names = Regions.list.map { "${it.second} (${it.first})" }.toTypedArray()
        val checked = Regions.list.indexOfFirst { it.first == settings.region }
        AlertDialog.Builder(this)
            .setTitle("Streaming region (match your VPN)")
            .setSingleChoiceItems(names, checked) { d, which ->
                d.dismiss()
                applySettings(settings.copy(region = Regions.list[which].first))
            }
            .show()
    }

    private fun pickYears() {
        val labels = YearPresets.list.map { it.first }.toTypedArray()
        val checked = YearPresets.list.indexOfFirst { it.second == settings.yearFrom && it.third == settings.yearTo }
        AlertDialog.Builder(this)
            .setTitle("Release years")
            .setSingleChoiceItems(labels, checked) { d, which ->
                d.dismiss()
                val p = YearPresets.list[which]
                applySettings(settings.copy(yearFrom = p.second, yearTo = p.third))
            }
            .show()
    }

    private fun toggleFree() {
        applySettings(settings.copy(freeOnly = !settings.freeOnly))
    }

    // ---------- Navigation ----------

    private fun openDetail(m: Movie) {
        startActivity(
            Intent(this, DetailActivity::class.java)
                .putExtra("id", m.id)
                .putExtra("title", m.title)
                .putExtra("year", m.year)
                .putExtra("overview", m.overview)
                .putExtra("poster", m.posterPath)
                .putExtra("backdrop", m.backdropPath)
                .putExtra("rating", m.rating)
        )
    }
}
