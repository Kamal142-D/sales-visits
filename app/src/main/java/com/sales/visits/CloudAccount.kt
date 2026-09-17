package com.sales.visits

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CloudAccount(context: Context, private val store: Store) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("visitflow_cloud", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listener: ListenerRegistration? = null
    private var uploadJob: Job? = null
    private var firstSyncDone = false
    private var pendingName = ""

    var user by mutableStateOf(auth.currentUser)
        private set
    var profile by mutableStateOf(UserProfile())
        private set
    var busy by mutableStateOf(false)
        private set
    var errorCode by mutableStateOf<String?>(null)
        private set
    var syncState by mutableStateOf("local")
        private set

    private val authListener = FirebaseAuth.AuthStateListener { connect(it.currentUser) }

    init {
        store.onDataChanged = ::queueUpload
        auth.addAuthStateListener(authListener)
        connect(auth.currentUser)
    }

    private fun stateRef(uid: String) = db.collection("users").document(uid)
        .collection("private").document("state")

    private fun connect(next: FirebaseUser?) {
        if (user?.uid == next?.uid && listener != null) return
        listener?.remove()
        listener = null
        uploadJob?.cancel()
        user = next
        firstSyncDone = false
        errorCode = null
        if (next == null) {
            profile = UserProfile()
            syncState = "local"
            return
        }
        // Isolate accounts IMMEDIATELY on switch (from local storage, no network): stash the outgoing
        // account's data and load this one's, so no screen can read/upload the previous account's data
        // during the window before the first server snapshot arrives.
        store.prepareForAccount(next.uid)
        profile = loadProfile(next)
        syncState = "syncing"
        listener = stateRef(next.uid).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                android.util.Log.w("VisitFlowCloud", "Cloud sync failed", error)
                syncState = "offline"
                return@addSnapshotListener
            }
            if (snapshot == null || (snapshot.metadata.isFromCache && !firstSyncDone)) {
                syncState = "offline"
                return@addSnapshotListener
            }
            val remoteProfile = snapshot.getString("profile")
            if (!remoteProfile.isNullOrBlank()) {
                profile = runCatching { json.decodeFromString<UserProfile>(remoteProfile) }.getOrDefault(profile)
                saveProfileLocal(next.uid, profile)
            }
            val remote = snapshot.getString("snapshot")
            if (!firstSyncDone) {
                firstSyncDone = true
                resolveFirstSync(next.uid, remote)
                return@addSnapshotListener
            }
            if (!snapshot.metadata.hasPendingWrites() && !remote.isNullOrBlank()) {
                val last = prefs.getString(lastKey(next.uid), null)
                if (remote != last) {
                    // A remote change arrived. Reconcile via the transactional upload, which three-way
                    // merges local+remote against our last-synced base — so an incoming change never
                    // clobbers an unpushed local edit, deletes are honored, and conflicts keep both.
                    upload(store.cloudSnapshot())
                    return@addSnapshotListener
                }
            }
            syncState = if (snapshot.metadata.hasPendingWrites()) "syncing" else "synced"
        }
    }

    private fun resolveFirstSync(uid: String, remote: String?) {
        // (Account isolation already ran in connect(), before any snapshot.)
        val local = store.cloudSnapshot()
        if (remote.isNullOrBlank()) {
            upload(local)
            return
        }
        val last = prefs.getString(lastKey(uid), null)
        val resolved = when {
            local == last -> remote
            remote == last -> local
            else -> store.threeWayMerge(last, local, remote)
        }
        // A null merge means the remote uses a newer schema than this app understands. Do NOT fall back
        // to overwriting it with our local copy — that would erase the newer fields. Ask the user to update.
        if (resolved == null) { syncState = "outdated"; return }
        // restoreCloudSnapshot also refuses a future-schema remote (returns false) — don't claim "synced".
        if (resolved != local && !store.restoreCloudSnapshot(resolved)) { syncState = "outdated"; return }
        if (resolved == remote) {
            // Nothing new to push; remote is already the agreed state.
            saveLast(uid, resolved)
            syncState = if (store.lastMergeConflicts > 0) "conflict" else "synced"
        } else {
            // Push through the transactional upload (base stays `last`, so it merges — not clobbers).
            upload(resolved)
        }
    }

    private fun queueUpload() {
        if (user == null || !firstSyncDone) return
        uploadJob?.cancel()
        uploadJob = scope.launch {
            delay(500)
            upload(store.cloudSnapshot())
        }
    }

    /**
     * Pushes [snapshot] inside a Firestore transaction: it re-reads the latest remote and three-way
     * merges local+remote against our last-synced base BEFORE writing. This closes the lost-update
     * window — a concurrent push from another device is merged in, never overwritten. The document is
     * only marked synced (and `last` advanced) on a confirmed write, so a rejected/oversized push
     * stays "offline", never a false "synced".
     */
    private fun upload(snapshot: String) {
        val current = user ?: return
        syncState = "syncing"
        val ref = stateRef(current.uid)
        val base = prefs.getString(lastKey(current.uid), null)
        val profileJson = json.encodeToString(profile)
        val refused = booleanArrayOf(false)   // set inside the txn when the remote schema is too new to merge
        // One Firestore document is capped at ~1 MiB; a write beyond it fails the transaction and we
        // surface "offline" (not "synced"). Split into per-collection docs when data approaches it.
        db.runTransaction { txn ->
            val remoteNow = txn.get(ref).getString("snapshot")
            // If the remote can't be merged (newer schema), ABORT — never overwrite it with our copy.
            val merged = if (remoteNow.isNullOrBlank()) snapshot
                else store.threeWayMerge(base, snapshot, remoteNow)
                    ?: run { refused[0] = true; throw IllegalStateException("version_incompatible") }
            txn.set(
                ref,
                mapOf("snapshot" to merged, "profile" to profileJson, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            merged
        }.addOnSuccessListener { merged ->
            // The account may have signed out or switched during the network round-trip; if so, this
            // result belongs to a different session — don't apply it to whoever is signed in now.
            if (user?.uid != current.uid) return@addOnSuccessListener
            saveLast(current.uid, merged)
            // Reconcile edits made locally DURING the upload (base = what we sent) with the server
            // result, so a concurrent local edit isn't overwritten by the merged snapshot.
            val localNow = store.cloudSnapshot()
            val reconciled = store.threeWayMerge(snapshot, localNow, merged) ?: merged
            if (reconciled != localNow) store.restoreCloudSnapshot(reconciled)
            syncState = if (store.lastMergeConflicts > 0) "conflict" else "synced"
            if (reconciled != merged) queueUpload()   // new local edits appeared mid-upload → push them
        }.addOnFailureListener { syncState = if (refused[0]) "outdated" else "offline" }
    }

    fun signIn(email: String, password: String) {
        if (!valid(email, password)) return
        busy = true
        errorCode = null
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                busy = false
                if (!task.isSuccessful) errorCode = authError(task.exception)
            }
    }

    fun createAccount(name: String, email: String, password: String) {
        if (name.trim().isBlank()) { errorCode = "name"; return }
        if (!valid(email, password)) return
        busy = true
        errorCode = null
        pendingName = name.trim()
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                busy = false
                if (!task.isSuccessful) {
                    errorCode = authError(task.exception)
                    return@addOnCompleteListener
                }
                profile = UserProfile(name = pendingName)
                saveProfileLocal(task.result.user!!.uid, profile)
                task.result.user?.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(pendingName).build()
                )
                queueUpload()
            }
    }

    fun resetPassword(email: String) {
        if (!email.contains('@')) { errorCode = "email"; return }
        busy = true
        errorCode = null
        auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { task ->
            busy = false
            errorCode = if (task.isSuccessful) "reset_sent" else authError(task.exception)
        }
    }

    fun saveProfile(value: UserProfile) {
        val current = user ?: return
        val clean = value.copy(
            name = value.name.trim(), jobTitle = value.jobTitle.trim(),
            company = value.company.trim(), phone = value.phone.trim(),
            companyEmail = value.companyEmail.trim(),
            shareLoginEmail = value.shareLoginEmail,
        )
        if (clean.name.isBlank()) { errorCode = "name"; return }
        profile = clean
        saveProfileLocal(current.uid, clean)
        current.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(clean.name).build())
        upload(store.cloudSnapshot())
    }

    fun signOut() = auth.signOut()

    private fun valid(email: String, password: String): Boolean {
        errorCode = when {
            !email.contains('@') -> "email"
            password.length < 6 -> "password"
            else -> null
        }
        return errorCode == null
    }

    private fun authError(error: Exception?): String = when ((error as? FirebaseAuthException)?.errorCode) {
        "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> "credentials"
        "ERROR_EMAIL_ALREADY_IN_USE" -> "email_used"
        "ERROR_NETWORK_REQUEST_FAILED" -> "network"
        "ERROR_TOO_MANY_REQUESTS" -> "too_many"
        else -> "auth_failed"
    }

    private fun loadProfile(user: FirebaseUser): UserProfile = prefs.getString(profileKey(user.uid), null)
        ?.let { runCatching { json.decodeFromString<UserProfile>(it) }.getOrNull() }
        ?: UserProfile(name = user.displayName.orEmpty().ifBlank { user.email?.substringBefore('@').orEmpty() })

    private fun saveProfileLocal(uid: String, value: UserProfile) =
        prefs.edit().putString(profileKey(uid), json.encodeToString(value)).apply()

    private fun saveLast(uid: String, value: String) = prefs.edit().putString(lastKey(uid), value).apply()
    private fun profileKey(uid: String) = "profile_$uid"
    private fun lastKey(uid: String) = "last_$uid"

    fun close() {
        listener?.remove()
        auth.removeAuthStateListener(authListener)
        if (store.onDataChanged != null) store.onDataChanged = null
        scope.cancel()
    }
}
