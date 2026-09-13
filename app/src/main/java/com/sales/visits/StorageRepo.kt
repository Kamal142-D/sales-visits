package com.sales.visits

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Attachment file storage (plan 6.3) — FREE, on-device only. Files a user picks are copied into the
 * app's private storage (filesDir/attachments) and their metadata ([Attachment]) rides along in the
 * synced snapshot, so every device sees the list of files even though the bytes stay local.
 *
 * No cloud blob store is used: Firebase Storage needs the paid Blaze plan, so we deliberately keep
 * attachments local (no cost, works offline). Cross-device file transfer can be added later on top of
 * any blob store without changing this metadata contract.
 */
class StorageRepo(private val appContext: Context, private val store: Store) {

    private fun attDir(): File = File(appContext.filesDir, "attachments").apply { mkdirs() }

    /** Imports a picked document into local storage and records its metadata. */
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
        return att
    }

    /** Opens a locally-stored attachment. Fails if the file lives on another device (local-only). */
    fun open(a: Attachment, onReady: (File) -> Unit, onError: (Exception) -> Unit) {
        val local = a.localPath.takeIf { it.isNotBlank() }?.let { File(it) }
        if (local != null && local.exists()) onReady(local)
        else onError(IllegalStateException("file_on_other_device"))
    }
}
