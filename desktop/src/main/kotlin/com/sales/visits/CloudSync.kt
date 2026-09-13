package com.sales.visits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cloud sync for the desktop app using the Firebase REST APIs (the Firebase SDK is Android-only).
 * It signs in with the SAME account as the phone and reads/writes the SAME Firestore document
 * (users/{uid}/private/state, field "snapshot"), so data flows both ways.
 *
 * Realtime isn't available over REST without gRPC, so the desktop pulls on sign-in and then polls
 * every 30 seconds; local edits are pushed (debounced). Security is still enforced by the Firestore
 * rules — the desktop only ever touches its own users/{uid} document.
 */
class CloudSync(private val store: Store) {
    var email by mutableStateOf<String?>(store.cloudPref("cloud_email").ifBlank { null }); private set
    var busy by mutableStateOf(false); private set
    var errorCode by mutableStateOf<String?>(null); private set
    var syncState by mutableStateOf("local"); private set   // local · syncing · synced · conflict · offline

    private var idToken: String? = null
    private var refreshToken: String? = store.cloudPref("cloud_refresh").ifBlank { null }
    private var uid: String? = store.cloudPref("cloud_uid").ifBlank { null }
    private var tokenExpiry = 0L
    private var firstSyncDone = false
    private var uploadJob: Job? = null
    private var pollJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val signedIn: Boolean get() = refreshToken != null

    init {
        store.onDataChanged = ::queueUpload
        if (refreshToken != null) scope.launch { runCatching { connect() } }
    }

    private fun lastKey(u: String) = "cloud_last_$u"

    // ---- auth ----
    fun signIn(mail: String, password: String) = authRequest("accounts:signInWithPassword", mail, password, null)
    fun createAccount(name: String, mail: String, password: String) = authRequest("accounts:signUp", mail, password, name)

    private fun authRequest(endpoint: String, mail: String, password: String, name: String?) {
        if (mail.isBlank() || password.length < 6 || busy) return
        busy = true; errorCode = null
        scope.launch {
            try {
                val body = JSONObject().put("email", mail.trim()).put("password", password).put("returnSecureToken", true)
                val res = postJson("https://identitytoolkit.googleapis.com/v1/$endpoint?key=${FirebaseConfig.API_KEY}", body)
                idToken = res.getString("idToken")
                refreshToken = res.getString("refreshToken")
                uid = res.getString("localId")
                tokenExpiry = System.currentTimeMillis() + (res.optLong("expiresIn", 3600) - 60) * 1000
                store.setCloudPref("cloud_refresh", refreshToken!!)
                store.setCloudPref("cloud_email", mail.trim())
                email = mail.trim()
                withContext(Dispatchers.Main) { busy = false }
                connect()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { busy = false; errorCode = authError(e.message); syncState = "local" }
            }
        }
    }

    fun signOut() {
        pollJob?.cancel(); uploadJob?.cancel()
        idToken = null; refreshToken = null; firstSyncDone = false
        store.setCloudPref("cloud_refresh", ""); store.setCloudPref("cloud_email", "")
        email = null; syncState = "local"; errorCode = null
    }

    private suspend fun ensureToken(): String {
        if (idToken != null && System.currentTimeMillis() < tokenExpiry) return idToken!!
        val rt = refreshToken ?: throw IOException("no_refresh")
        val res = postForm(
            "https://securetoken.googleapis.com/v1/token?key=${FirebaseConfig.API_KEY}",
            "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(rt, "UTF-8"),
        )
        idToken = res.getString("id_token")
        refreshToken = res.getString("refresh_token")
        uid = res.getString("user_id")
        tokenExpiry = System.currentTimeMillis() + (res.optLong("expires_in", 3600) - 60) * 1000
        store.setCloudPref("cloud_refresh", refreshToken!!)
        return idToken!!
    }

    // ---- sync ----
    private suspend fun connect() {
        withContext(Dispatchers.Main) { syncState = "syncing" }
        try {
            ensureToken()
            val u = uid ?: return
            store.prepareForAccount(u)
            firstSync(u)
            startPolling(u)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { syncState = "offline" }
        }
    }

    private suspend fun firstSync(u: String) {
        val remote = pull(u)
        val local = store.cloudSnapshot()
        if (remote.isNullOrBlank()) {
            push(u, local)
        } else {
            val last = store.cloudPref(lastKey(u)).ifBlank { null }
            val resolved = when {
                local == last -> remote
                remote == last -> local
                else -> store.threeWayMerge(last, local, remote) ?: local
            }
            if (resolved != local) withContext(Dispatchers.Main) { store.restoreCloudSnapshot(resolved) }
            if (resolved == remote) {
                store.setCloudPref(lastKey(u), resolved)
                withContext(Dispatchers.Main) { syncState = if (store.lastMergeConflicts > 0) "conflict" else "synced" }
            } else {
                // Push through the merging, precondition-guarded upload (base stays `last`).
                push(u, resolved)
            }
        }
        firstSyncDone = true
    }

    private fun queueUpload() {
        if (!firstSyncDone) return
        val u = uid ?: return
        uploadJob?.cancel()
        uploadJob = scope.launch {
            delay(600)
            runCatching { push(u, store.cloudSnapshot()) }
        }
    }

    private fun startPolling(u: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                delay(30_000)
                runCatching {
                    val remote = pull(u) ?: return@runCatching
                    val last = store.cloudPref(lastKey(u)).ifBlank { null }
                    if (remote != last) {
                        if (remote == store.cloudSnapshot()) {
                            store.setCloudPref(lastKey(u), remote)   // already in sync; just advance the base
                        } else {
                            // Reconcile via the merging push so local edits are never clobbered.
                            push(u, store.cloudSnapshot())
                        }
                    }
                }
            }
        }
    }

    private fun docUrl(u: String) =
        "https://firestore.googleapis.com/v1/projects/${FirebaseConfig.PROJECT_ID}/databases/(default)/documents/users/$u/private/state"

    /** Returns the remote snapshot string, or null when the document/field doesn't exist. */
    private suspend fun pull(u: String): String? = pullWithMeta(u).first

    /** Returns (snapshot, document updateTime) — updateTime drives the optimistic-concurrency check. */
    private suspend fun pullWithMeta(u: String): Pair<String?, String?> {
        val token = ensureToken()
        val (code, text) = httpGet(docUrl(u), token)
        if (code == 404) return null to null
        if (code !in 200..299) throw IOException("HTTP $code")
        val obj = JSONObject(text)
        val updateTime = obj.optString("updateTime").ifBlank { null }
        val snapshot = obj.optJSONObject("fields")?.optJSONObject("snapshot")?.optString("stringValue")
        return snapshot to updateTime
    }

    /**
     * Pushes [snapshot] with a three-way merge and an optimistic-concurrency precondition (plan 1.4):
     * it re-reads the latest remote (and its updateTime), merges local+remote against our last-synced
     * base, then PATCHes only if the document hasn't changed since that read. If another device wrote
     * in between (precondition fails), it re-reads and retries — so a concurrent push is merged in,
     * never overwritten. `last` advances (and we mark "synced") only on a confirmed write.
     */
    private suspend fun push(u: String, snapshot: String, attempts: Int = 3) {
        val token = ensureToken()
        val base = store.cloudPref(lastKey(u)).ifBlank { null }
        val (remoteNow, updateTime) = pullWithMeta(u)
        val merged = if (remoteNow.isNullOrBlank()) snapshot
            else store.threeWayMerge(base, snapshot, remoteNow) ?: snapshot
        // updateMask keeps us from clobbering the "profile" field the phone writes.
        var url = docUrl(u) + "?updateMask.fieldPaths=snapshot&updateMask.fieldPaths=updatedAt"
        if (updateTime != null) url += "&currentDocument.updateTime=" + URLEncoder.encode(updateTime, "UTF-8")
        val body = JSONObject().put("fields", JSONObject()
            .put("snapshot", JSONObject().put("stringValue", merged))
            .put("updatedAt", JSONObject().put("timestampValue", java.time.Instant.now().toString())))
        val (code, text) = httpSend("PATCH", url, body.toString(), token)
        when {
            code in 200..299 -> {
                store.setCloudPref(lastKey(u), merged)
                if (merged != snapshot) withContext(Dispatchers.Main) { store.restoreCloudSnapshot(merged) }
                withContext(Dispatchers.Main) { syncState = if (store.lastMergeConflicts > 0) "conflict" else "synced" }
            }
            // Someone else wrote between our read and write: re-read, re-merge, retry.
            attempts > 1 && (code == 409 || code == 412 || text.contains("FAILED_PRECONDITION")) ->
                push(u, snapshot, attempts - 1)
            else -> withContext(Dispatchers.Main) { syncState = "offline" }
        }
    }

    // ---- HTTP helpers ----
    private fun postJson(url: String, body: JSONObject): JSONObject {
        val (code, text) = httpSend("POST", url, body.toString(), null)
        if (code !in 200..299) throw IOException(text)
        return JSONObject(text)
    }

    private fun postForm(url: String, form: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15000; readTimeout = 30000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write(form.toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) throw IOException(text)
        return JSONObject(text)
    }

    private fun httpGet(url: String, token: String): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15000; readTimeout = 30000
            setRequestProperty("Authorization", "Bearer $token")
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        return code to text
    }

    private fun httpSend(method: String, url: String, body: String, token: String?): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; doOutput = true; connectTimeout = 15000; readTimeout = 30000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        return code to text
    }

    private fun authError(raw: String?): String {
        val body = raw ?: ""
        val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        val eff = msg.ifBlank { body }
        return when {
            eff.contains("EMAIL_EXISTS") -> "email_exists"
            eff.contains("INVALID_LOGIN_CREDENTIALS") || eff.contains("INVALID_PASSWORD") || eff.contains("EMAIL_NOT_FOUND") -> "bad_login"
            eff.contains("WEAK_PASSWORD") -> "weak_password"
            eff.contains("OPERATION_NOT_ALLOWED") -> "provider_disabled"
            eff.contains("API key not valid", true) || eff.contains("API_KEY") -> "bad_key"
            // Surface the real reason for anything unexpected so it can be diagnosed.
            else -> eff.take(160).ifBlank { "sync_error" }
        }
    }
}
