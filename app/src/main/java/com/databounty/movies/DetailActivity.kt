package com.databounty.movies

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.databounty.movies.youtube.YouTubeLookup
import java.util.concurrent.Executors

class DetailActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var webChip: TextView? = null      // "More options (browser)" — always kept last
    private var providerCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val id = intent.getIntExtra("id", 0)
        val title = intent.getStringExtra("title") ?: ""
        val year = intent.getStringExtra("year") ?: ""
        val overview = intent.getStringExtra("overview") ?: ""
        val poster = intent.getStringExtra("poster")
        val backdrop = intent.getStringExtra("backdrop")
        val rating = intent.getDoubleExtra("rating", 0.0)
        val region = Settings.load(this).region

        findViewById<TextView>(R.id.title).text = title
        findViewById<TextView>(R.id.meta).text =
            listOfNotNull(
                year.ifEmpty { null },
                if (rating > 0) "★ ${"%.1f".format(rating)}" else null
            ).joinToString("   ")
        findViewById<TextView>(R.id.overview).text = overview.ifEmpty { "No description available." }
        findViewById<TextView>(R.id.whereLabel).text = "Where to watch in ${Regions.name(region)}"

        val posterView = findViewById<ImageView>(R.id.poster)
        if (poster != null) {
            Glide.with(this).load(Tmdb.IMG + "w500" + poster)
                .placeholder(R.drawable.poster_placeholder).into(posterView)
        } else {
            posterView.setImageResource(R.drawable.poster_placeholder)
        }
        if (backdrop != null) {
            Glide.with(this).load(Tmdb.IMG + "w1280" + backdrop).into(findViewById<ImageView>(R.id.backdrop))
        }

        val row = findViewById<LinearLayout>(R.id.providerRow)
        val hint = findViewById<TextView>(R.id.hint)
        hint.text = "Checking streaming services…"

        // 1. Streaming services from TMDB
        io.execute {
            try {
                val info = Tmdb.watchProviders(id, region)
                main.post { if (!isFinishing && !isDestroyed) showProviders(info, title, region, row, hint) }
            } catch (e: Exception) {
                main.post {
                    if (!isFinishing && !isDestroyed) hint.text = "Couldn't load services: ${e.message}"
                }
            }
        }

        // 2. Is this movie on an official YouTube movie channel? (runs after step 1)
        io.execute {
            val videoId = try { YouTubeLookup.find(this, id, title, year) } catch (e: Exception) { null }
            if (videoId != null) {
                main.post { if (!isFinishing && !isDestroyed) addYouTubeChip(videoId, row, hint) }
            }
        }
    }

    private fun showProviders(info: WatchInfo, title: String, region: String, row: LinearLayout, hint: TextView) {
        row.removeAllViews()
        providerCount = info.providers.size
        info.providers.forEach { p ->
            val chip = Ui.chip(this, "${p.name}  ·  ${p.kind}") {
                val msg = Launcher.open(this, p, title)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
            if (row.childCount == 0) (chip.layoutParams as LinearLayout.LayoutParams).marginStart = 0
            row.addView(chip)
        }
        if (info.link != null) {
            val web = Ui.chip(this, "More options (browser)") {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.link)))
                } catch (e: Exception) {
                    Toast.makeText(this, "No web browser installed on this device.", Toast.LENGTH_LONG).show()
                }
            }
            if (row.childCount == 0) (web.layoutParams as LinearLayout.LayoutParams).marginStart = 0
            row.addView(web)
            webChip = web
        }

        hint.text = if (info.providers.isEmpty()) {
            "Not listed on any streaming service in ${Regions.name(region)}. Try another region from the main screen."
        } else {
            "Opens the service's app — then search for the title there. Your VPN must be set to ${Regions.name(region)}."
        }
        row.getChildAt(0)?.requestFocus()
    }

    /** Adds "YouTube · Free" to the same row as the other services (before "More options"). */
    private fun addYouTubeChip(videoId: String, row: LinearLayout, hint: TextView) {
        val chip = Ui.chip(this, "YouTube  ·  Free") {
            if (!YouTubeLookup.open(this, videoId)) {
                Toast.makeText(this, "Install the YouTube app from the Appstore.", Toast.LENGTH_LONG).show()
            }
        }
        val lp = chip.layoutParams as LinearLayout.LayoutParams
        val normalMargin = lp.marginStart
        val index = webChip?.let { row.indexOfChild(it) }?.takeIf { it >= 0 } ?: row.childCount

        if (index == 0) {
            // Becomes the first pill: no left margin, and the old first pill gets the normal gap
            lp.marginStart = 0
            row.getChildAt(0)?.let { (it.layoutParams as LinearLayout.LayoutParams).marginStart = normalMargin }
        }
        row.addView(chip, index)

        if (providerCount == 0) {
            hint.text = "Free on YouTube from an official movie channel — no VPN needed."
            chip.requestFocus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdownNow()
    }
}
