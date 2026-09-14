package com.sales.visits

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Windows desktop auto-update check (plan 6.5) — free, via GitHub.
 *
 * Reads a tiny JSON manifest committed to the repo (served by raw.githubusercontent, no release
 * needed and no cost), compares its build number to this build, and — on the user's click — opens the
 * download page in the browser. The installer itself is hosted as a GitHub Release asset (free, holds
 * large files), so the manifest's `url` points at the release.
 *
 * To ship a desktop update:
 *  1) bump [APP_BUILD] + [APP_VERSION] here and `packageVersion` in desktop/build.gradle.kts,
 *  2) build the installer and upload it to a GitHub Release,
 *  3) bump `build`/`version` in desktop-update.json and point `url` at that release.
 */
object DesktopUpdater {
    const val APP_VERSION = "1.8.0"
    const val APP_BUILD = 1              // monotonically increasing; compared against the manifest

    const val MANIFEST_URL =
        "https://raw.githubusercontent.com/Kamal142-D/sales-visits/main/desktop-update.json"

    @Serializable
    data class Manifest(val build: Int = 0, val version: String = "", val url: String = "", val notes: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    /** Fetches the manifest; returns it only when it describes a build newer than this one. */
    fun check(): Manifest? = runCatching {
        val conn = (URL(MANIFEST_URL + "?t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000; readTimeout = 60000; instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val m = json.decodeFromString<Manifest>(text.ifBlank { "{}" })
            if (m.build > APP_BUILD && m.url.isNotBlank()) m else null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Opens the update's download page in the default browser. */
    fun openDownload(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }
}
