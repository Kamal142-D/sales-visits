package com.sales.visits

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.io.File

/**
 * Attachment file storage (plan 6.3). Metadata lives in the synced snapshot (see [Attachment]); the
 * bytes go to Firebase Storage under users/{uid}/attachments/{id}. Uploads are deferred: a picked
 * file is saved locally first and uploaded when possible, so it's never lost if offline.
 *
 * NOTE: requires Firebase Storage enabled on the project (Blaze plan). Until then, files stay local
 * (uploaded=false) and the metadata still syncs, so this layer degrades gracefully.
 */
class StorageRepo(private val appContext: Context, private val store: Store) {
    private val auth = FirebaseAuth.getInstance()
    private val storage by lazy { FirebaseStorage.getInstance() }

    private fun attDir(): File = File(appContext.filesDir, "attachments").apply { mkdirs() }

    /** Imports a picked document into local storage and records its metadata, then tries to upload. */
    fun addFromUri(uri: Uri, recordType: String, recordId: String): Attachment? {
        val id = store.newAttachmentId()
        var name = "file"; var size = 0L
        runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val ni = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = cur.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = cur.getString(ni) ?: name
                    if (si >= 0) size = cur.getLong(si)
                }
            }
        }
        val dest = File(attDir(), "$id-$name")
        val ok = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
        }.isSuccess
        if (!ok) return null
        val mime = appContext.contentResolver.getType(uri).orEmpty()
        val att = Attachment(
            id = id, recordType = recordType, recordId = recordId, name = name, mime = mime,
            size = if (size > 0) size else dest.length(), localPath = dest.absolutePath, uploaded = false, createdAt = todayIso(),
        )
        store.upsertAttachment(att)
        uploadPending()
        return att
    }

    /** Uploads any local-only attachments (deferred/offline retry). No-op if signed out. */
    fun uploadPending() {
        val uid = auth.currentUser?.uid ?: return
        store.attachments.filter { !it.uploaded && it.localPath.isNotBlank() }.forEach { a ->
            val f = File(a.localPath)
            if (!f.exists()) return@forEach
            val path = "users/$uid/attachments/${a.id}"
            runCatching {
                storage.reference.child(path).putFile(Uri.fromFile(f))
                    .addOnSuccessListener { store.markAttachmentUploaded(a.id, path) }
            }
        }
    }

    /** Ensures a local copy exists (downloading from storage if needed), then calls [onReady]. */
    fun open(a: Attachment, onReady: (File) -> Unit, onError: (Exception) -> Unit) {
        val local = a.localPath.takeIf { it.isNotBlank() }?.let { File(it) }
        if (local != null && local.exists()) { onReady(local); return }
        val uid = auth.currentUser?.uid
        val path = a.storagePath.ifBlank { if (uid != null) "users/$uid/attachments/${a.id}" else "" }
        if (path.isBlank()) { onError(IllegalStateException("no_source")); return }
        val dest = File(attDir(), "${a.id}-${a.name}")
        runCatching {
            storage.reference.child(path).getFile(dest)
                .addOnSuccessListener { store.upsertAttachment(a.copy(localPath = dest.absolutePath)); onReady(dest) }
                .addOnFailureListener(onError)
        }.onFailure { onError(it as? Exception ?: Exception(it)) }
    }
}
