package com.databounty.movies

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Maps TMDB provider names to Android TV / Fire TV app packages and opens them. */
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

    private fun packagesFor(name: String): List<String> {
        val n = name.lowercase()
        return known.firstOrNull { n.contains(it.first) }?.second ?: emptyList()
    }

    /** Tries to open the provider's app. Returns a message to show the user. */
    fun open(ctx: Context, provider: Provider, title: String): String {
        val pkgs = packagesFor(provider.name)
        val pm = ctx.packageManager

        for (pkg in pkgs) {
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(intent)
                    return "Opened ${provider.name} — search for \"$title\""
                } catch (e: Exception) {
                    // try the next package
                }
            }
        }

        val pkg = pkgs.firstOrNull()
            ?: return "${provider.name} isn't mapped to an app yet. Find it in the Appstore and search for \"$title\"."

        val storeUris = listOf("amzn://apps/android?p=$pkg", "market://details?id=$pkg")
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
