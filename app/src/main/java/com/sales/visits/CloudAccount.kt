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
                    store.restoreCloudSnapshot(remote)
                    saveLast(next.uid, remote)
                }
            }
            syncState = if (snapshot.metadata.hasPendingWrites()) "syncing" else "synced"
        }
    }

    private fun resolveFirstSync(uid: String, remote: String?) {
        val local = store.cloudSnapshot()
        if (remote.isNullOrBlank()) {
            upload(local)
            return
        }
        val last = prefs.getString(lastKey(uid), null)
        val resolved = when {
            local == last -> remote
            remote == last -> local
            else -> store.mergeCloudSnapshot(remote) ?: local
        }
        if (resolved != local) store.restoreCloudSnapshot(resolved)
        saveLast(uid, resolved)
        if (resolved != remote) upload(resolved) else syncState = "synced"
    }

    private fun queueUpload() {
        if (user == null || !firstSyncDone) return
        uploadJob?.cancel()
        uploadJob = scope.launch {
            delay(500)
            upload(store.cloudSnapshot())
        }
    }

    private fun upload(snapshot: String) {
        val current = user ?: return
        syncState = "syncing"
        // ponytail: one Firestore document is capped at 1 MiB; split into collections when real account data approaches it.
        val data = mapOf(
            "snapshot" to snapshot,
            "profile" to json.encodeToString(profile),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        stateRef(current.uid).set(data, SetOptions.merge())
            .addOnSuccessListener {
                saveLast(current.uid, snapshot)
                syncState = "synced"
            }
            .addOnFailureListener { syncState = "offline" }
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
