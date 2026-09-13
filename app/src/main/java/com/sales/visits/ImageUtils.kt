package com.sales.visits

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** Copies a picked image into the app's private storage and returns its file path (persistent). */
fun copyImageToFiles(ctx: Context, uri: Uri): String? = runCatching {
    val dir = File(ctx.filesDir, "visit_images").apply { mkdirs() }
    val file = File(dir, "img-${System.currentTimeMillis()}-${(1000..9999).random()}.jpg")
    ctx.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
        ?: error("cannot open image")
    file.absolutePath
}.getOrNull()

/** Loads a downscaled bitmap from a file path (kept small to avoid out-of-memory on thumbnails). */
fun loadImageBitmap(path: String, maxPx: Int = 600): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

/** Deletes an attached image file from storage. */
fun deleteImageFile(path: String) {
    runCatching { File(path).delete() }
}
