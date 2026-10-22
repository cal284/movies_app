package com.databounty.movies

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri

/**
 * Opens the streaming app for a TMDB provider name (e.g. "The Roku Channel").
 *
 * Order of attempts:
 *  1. Known package names below
 *  2. Any installed app whose NAME matches the provider (e.g. label "The Roku Channel")
 *     -> new services work automatically once their app is installed
 *  3. The Appstore page (known package) or an Appstore search (unknown package)
 */
object Launcher {
    private val known = listOf(
        "tubi" to listOf("com.tubitv"),
        "pluto" to listOf("tv.pluto.android"),
        "plex" to listOf("com.plexapp.android"),
        "youtube" to listOf("com.amazon.firetv.youtube", "com.google.android.youtube.tv", "com.google.android.youtube"),
        "freevee" to listOf("com.amazon.avod"),
        "amazon" to listOf("com.amazon.avod", "com.amazon.avod.thirdpartyclient"),
        "netflix" to listOf("com.netflix.ninja", "com.netflix.mediaclient"),
        "kanopy" to listOf("com.kanopy"),
        "crackle" to listOf("com.crackle.androidtv"),
        "disney" to listOf("com.disney.disneyplus"),
        "hulu" to listOf("com.hulu.plus"),
        "peacock" to listOf("com.peacocktv.peacockandroid"),
        "paramount" to listOf("com.cbs.ott", "com.cbs.app")
    )

    /** Extra app names to look for when the app's name differs from TMDB's service name. */
    private val labelAliases = mapOf(
        "fandango" to listOf("fandango", "vudu"),
        "roku" to listOf("roku"),
        "paramount" to listOf("paramount"),
        "amazon" to listOf("prime video", "freevee")
    )

    /** Words in TMDB service names that don't identify the service. */
    private val stopWords = setOf(
        "the", "tv", "free", "with", "ads", "channel", "premium", "plus", "at", "home",
        "on", "and", "standard", "basic", "video", "movies", "app"
    )

    private fun packagesFor(name: String): List<String> {
        val n = name.lowercase()
        return known.firstOrNull { n.contains(it.first) }?.second ?: emptyList()
    }

    /** "Fandango at Home Free" -> ["fandango", "vudu"]; "The Roku Channel" -> ["roku"] */
    private fun keywordsFor(name: String): List<String> {
        val words = name.lowercase()
            .split(Regex("[^a-z0-9+]+"))
            .filter { it.length >= 3 && it !in stopWords }
        val aliases = labelAliases.entries
            .filter { (key, _) -> name.lowercase().contains(key) }
            .flatMap { it.value }
        return (words + aliases).distinct()
    }

    /** Looks through installed apps for one whose name matches the provider. */
    private fun findInstalledByName(ctx: Context, providerName: String): Intent? {
        val keywords = keywordsFor(providerName)
        if (keywords.isEmpty()) return null
        val pm = ctx.packageManager

        val apps = mutableListOf<ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            apps += pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        }

        val match = apps.firstOrNull { ri ->
            if (ri.activityInfo.packageName == ctx.packageName) return@firstOrNull false
            val label = ri.loadLabel(pm).toString().lowercase()
            keywords.any { kw -> if (kw.contains(' ')) label.contains(kw) else label.split(Regex("[^a-z0-9+]+")).contains(kw) }
        } ?: return null

        val pkg = match.activityInfo.packageName
        return pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    }

    private fun launch(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    /** Tries to open the provider's app. Returns a message to show the user. */
    fun open(ctx: Context, provider: Provider, title: String): String {
        val pm = ctx.packageManager
        val pkgs = packagesFor(provider.name)

        // 1. Known package names
        for (pkg in pkgs) {
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            if (intent != null && launch(ctx, intent)) return "Opened ${provider.name} — search for \"$title\""
        }

        // 2. Installed app with a matching name
        findInstalledByName(ctx, provider.name)?.let { intent ->
            if (launch(ctx, intent)) return "Opened ${provider.name} — search for \"$title\""
        }

        // 3. Not installed: Appstore page, or an Appstore search by name
        val storeUris = pkgs.firstOrNull()?.let { pkg ->
            listOf("amzn://apps/android?p=$pkg", "market://details?id=$pkg")
        } ?: run {
            val q = Uri.encode(keywordsFor(provider.name).firstOrNull() ?: provider.name)
            listOf("amzn://apps/android?s=$q", "market://search?q=$q")
        }
        for (uri in storeUris) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "${provider.name} isn't installed — opening the Appstore."
            } catch (e: ActivityNotFoundException) {
                // try next store
            }
        }
        return "${provider.name} isn't installed. Install it, then search for \"$title\"."
    }
}
