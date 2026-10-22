package com.databounty.movies

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** yearFrom / yearTo of 0 means "no limit". */
data class Settings(
    val region: String = "US",
    val yearFrom: Int = 2020,
    val yearTo: Int = 2023,
    val freeOnly: Boolean = true
) {
    fun yearsLabel(): String = when {
        yearFrom == 0 && yearTo == 0 -> "Any year"
        yearFrom == 0 -> "Before ${yearTo + 1}"
        yearTo == 0 -> "$yearFrom+"
        yearFrom == yearTo -> "$yearFrom"
        else -> "$yearFrom–$yearTo"
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("region", region)
            .putInt("yearFrom", yearFrom)
            .putInt("yearTo", yearTo)
            .putBoolean("freeOnly", freeOnly)
            .apply()
    }

    companion object {
        private const val PREFS = "settings"

        fun load(ctx: Context): Settings {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Settings(
                region = p.getString("region", "US") ?: "US",
                yearFrom = p.getInt("yearFrom", 2020),
                yearTo = p.getInt("yearTo", 2023),
                freeOnly = p.getBoolean("freeOnly", true)
            )
        }
    }
}

object Regions {
    val list = listOf(
        "US" to "United States",
        "GB" to "United Kingdom",
        "CA" to "Canada",
        "AU" to "Australia",
        "IE" to "Ireland",
        "NZ" to "New Zealand",
        "DE" to "Germany",
        "FR" to "France",
        "NL" to "Netherlands",
        "ES" to "Spain",
        "IT" to "Italy",
        "SE" to "Sweden",
        "BR" to "Brazil",
        "MX" to "Mexico",
        "IN" to "India",
        "JM" to "Jamaica",
        "TT" to "Trinidad and Tobago"
    )

    fun name(code: String) = list.firstOrNull { it.first == code }?.second ?: code
}

object YearPresets {
    val list = listOf(
        Triple("2020–2023", 2020, 2023),
        Triple("2024 onward", 2024, 0),
        Triple("2015–2019", 2015, 2019),
        Triple("2010–2014", 2010, 2014),
        Triple("2000–2009", 2000, 2009),
        Triple("1990–1999", 1990, 1999),
        Triple("Before 1990", 0, 1989),
        Triple("Any year", 0, 0)
    )
}

/** Remote-friendly pill button used across the app. */
object Ui {
    fun chip(ctx: Context, text: String, onClick: (View) -> Unit): TextView {
        val d = ctx.resources.displayMetrics.density
        return TextView(ctx).apply {
            this.text = text
            textSize = 14f
            setTextColor(ContextCompat.getColorStateList(ctx, R.color.chip_text))
            setBackgroundResource(R.drawable.chip_bg)
            setPadding((18 * d).toInt(), (9 * d).toInt(), (18 * d).toInt(), (9 * d).toInt())
            isFocusable = true
            isClickable = true
            setSingleLine(true)
            setOnClickListener(onClick)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (10 * d).toInt() }
        }
    }
}
