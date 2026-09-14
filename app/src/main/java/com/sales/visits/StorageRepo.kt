package com.sales.visits

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.io.File

/**
 * Attachment file storage (plan 6.3). Files a user picks are copied into the app's private storage
 * (filesDir/attachments) and their metadata ([Attachment]) rides along in the synced snapshot.
 *
 * Cross-device file sync is FREE and optional via the user's own Google Drive (see [DriveSync]):
 * when Drive is connected the bytes upload to the user's Drive and the returned fileId is kept on the
 * attachment, so another device can download it. When Drive is not connected, files stay local and
 * the metadata still syncs — everything degrades gracefully, at no cost.
 */
class StorageRepo(private val appContext: Context, private val store: Store) {

    private val main = Handler(Looper.getMainLooper())

    private fun attDir(): File = File(appContext.filesDir, "attachments").apply { mkdirs() }

    /** Imports a picked document into local storage, records metadata, then tries a Drive upload. */
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

    /** Uploads local-only attachments to the user's Google Drive (no-op if Drive isn't connected). */
    fun uploadPending() {
        if (!DriveSync.isConnected(appContext)) return
        val pending = store.attachments.filter { !it.uploaded && it.localPath.isNotBlank() }
        if (pending.isEmpty()) return
        Thread {
            pending.forEach { a ->
                val f = File(a.localPath)
                if (!f.exists()) return@forEach
                val fileId = DriveSync.upload(appContext, f, "${a.id}-${a.name}", a.mime)
                if (fileId != null) main.post { store.markAttachmentUploaded(a.id, fileId) }
            }
        }.start()
    }

    /** Ensures a local copy exists (downloading from Drive if needed), then calls [onReady]. */
    fun open(a: Attachment, onReady: (File) -> Unit, onError: (Exception) -> Unit) {
        val local = a.localPath.takeIf { it.isNotBlank() }?.let { File(it) }
        if (local != null && local.exists()) { onReady(local); return }
        if (a.storagePath.isBlank() || !DriveSync.isConnected(appContext)) {
            onError(IllegalStateException("file_on_other_device")); return
        }
        val dest = File(attDir(), "${a.id}-${a.name}")
        Thread {
            val ok = DriveSync.download(appContext, a.storagePath, dest)
            main.post {
                if (ok) { store.upsertAttachment(a.copy(localPath = dest.absolutePath)); onReady(dest) }
                else onError(IllegalStateException("download_failed"))
            }
        }.start()
    }
}
