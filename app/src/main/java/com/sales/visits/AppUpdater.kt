package com.sales.visits

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
)

/**
 * Lightweight in-app updater. Reads a small JSON manifest hosted on GitHub, compares
 * its version to the installed one, and (on the user's tap) downloads the new APK and
 * hands it to the system installer.
 *
 * To ship an update:
 *  1) bump versionCode/versionName in app/build.gradle.kts and build a release APK,
 *  2) upload the APK to the repo (app-release.apk),
 *  3) update update.json to point at it.
 */
object AppUpdater {
    const val MANIFEST_URL =
        "https://raw.githubusercontent.com/Kamal142-D/sales-visits/main/update.json"

    fun currentVersionCode(context: Context): Long {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }

    fun currentVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

    private fun open(urlStr: String): HttpURLConnection {
        val url = URL(urlStr)
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
        }
    }

    /** Fetches the manifest; returns the update only when it is newer than installed. */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = open(MANIFEST_URL + "?t=" + System.currentTimeMillis())
        try {
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text.ifBlank { "{}" })
            val info = UpdateInfo(
                versionCode = json.optLong("versionCode", 0),
                versionName = json.optString("versionName", ""),
                apkUrl = json.optString("apkUrl", ""),
                notes = json.optString("notes", ""),
            )
            if (info.versionCode > currentVersionCode(context) && info.apkUrl.isNotBlank()) info else null
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads the update APK into the cache, reporting 0..1 progress. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "visitflow-${info.versionCode}.apk")
            val conn = open(info.apkUrl)
            try {
                if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            file
        }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
