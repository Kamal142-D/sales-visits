package com.sales.visits

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Free cross-device attachment sync (plan 6.3) via the user's own Google Drive.
 *
 * Uses the `drive.file` scope — the app can only see files it created, nothing else in the user's
 * Drive — which is a non-sensitive scope, so no Google app-verification is required. Files upload to
 * a "VisitFlow" folder in the user's Drive; the returned Drive fileId is stored on the [Attachment].
 *
 * Requires (one-time, in Google Cloud console for the Firebase project):
 *  - Google Drive API enabled,
 *  - an Android OAuth client (package com.visit.flow + the signing SHA-1),
 *  - the drive.file scope on the OAuth consent screen.
 *
 * Everything degrades gracefully: if not connected, or any call fails, attachments stay local.
 */
object DriveSync {
    private const val SCOPE_STR = "https://www.googleapis.com/auth/drive.file"
    private val SCOPE = Scope(SCOPE_STR)

    fun signInClient(ctx: Context): GoogleSignInClient {
        // One Google connection grants both attachment sync (drive.file) and calendar sync
        // (calendar.events) — see CalendarSync. Requesting both here avoids a second sign-in.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(SCOPE, Scope(CalendarSync.SCOPE_STR))
            .build()
        return GoogleSignIn.getClient(ctx, gso)
    }

    /** True once the user connected Drive and granted the file scope. */
    fun isConnected(ctx: Context): Boolean =
        GoogleSignIn.getLastSignedInAccount(ctx)?.let { GoogleSignIn.hasPermissions(it, SCOPE) } == true

    fun connectedEmail(ctx: Context): String =
        GoogleSignIn.getLastSignedInAccount(ctx)?.email.orEmpty()

    fun disconnect(ctx: Context) { runCatching { signInClient(ctx).signOut() } }

    /** Blocking OAuth token for Drive REST — must be called off the main thread. */
    private fun token(ctx: Context): String? {
        val acct = GoogleSignIn.getLastSignedInAccount(ctx)?.account ?: return null
        return runCatching { GoogleAuthUtil.getToken(ctx, acct, "oauth2:$SCOPE_STR") }.getOrNull()
    }

    private fun conn(urlStr: String, method: String, token: String): HttpURLConnection =
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000; readTimeout = 120000
            instanceFollowRedirects = true
            setRequestProperty("Authorization", "Bearer $token")
        }

    /** Uploads [file] to the user's Drive; returns the Drive fileId, or null on failure. Blocking. */
    fun upload(ctx: Context, file: File, name: String, mime: String): String? {
        val token = token(ctx) ?: return null
        return runCatching {
            // 1) create the metadata entry (name only; drive.file gives us our own scope)
            val meta = conn("https://www.googleapis.com/drive/v3/files", "POST", token).apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                outputStream.use { it.write(JSONObject(mapOf("name" to name)).toString().toByteArray()) }
            }
            if (meta.responseCode !in 200..299) return null
            val id = JSONObject(meta.inputStream.bufferedReader().use { it.readText() }).optString("id")
            meta.disconnect()
            if (id.isBlank()) return null
            // 2) upload the bytes into that file
            val up = conn("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media", "PATCH", token).apply {
                doOutput = true
                setRequestProperty("Content-Type", mime.ifBlank { "application/octet-stream" })
                file.inputStream().use { input -> outputStream.use { input.copyTo(it) } }
            }
            val ok = up.responseCode in 200..299
            up.disconnect()
            if (ok) id else null
        }.getOrNull()
    }

    /** Downloads a Drive file by id into [dest]; returns true on success. Blocking.
     *  Writes to a temp file and renames on success, so a stopped download never leaves a partial file
     *  at [dest] that a later open could mistake for a complete one. */
    fun download(ctx: Context, fileId: String, dest: File): Boolean {
        val token = token(ctx) ?: return false
        val tmp = File(dest.absolutePath + ".part")
        return runCatching {
            val c = conn("https://www.googleapis.com/drive/v3/files/$fileId?alt=media", "GET", token)
            if (c.responseCode !in 200..299) { c.disconnect(); tmp.delete(); return false }
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            c.disconnect()
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
            dest.exists()
        }.getOrElse { runCatching { tmp.delete() }; false }
    }
}
