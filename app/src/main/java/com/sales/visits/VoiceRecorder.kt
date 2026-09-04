package com.sales.visits

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records a short voice memo to an AAC file in the cache. The audio is sent as-is to the
 * AI, which understands the speech in any language and formats it — no on-device transcription.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    /** Starts recording; returns false if the mic/encoder could not be set up. */
    fun start(): Boolean = runCatching {
        val file = File(context.cacheDir, "visit-note-${System.currentTimeMillis()}.aac")
        val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioSamplingRate(16000)
        rec.setAudioEncodingBitRate(64000)
        rec.setOutputFile(file.absolutePath)
        rec.prepare()
        rec.start()
        recorder = rec
        output = file
    }.isSuccess

    /** Stops and returns the recorded file, or null if nothing usable was captured. */
    fun stop(): File? {
        val rec = recorder ?: return null
        val file = output
        val ok = runCatching { rec.stop() }.isSuccess
        runCatching { rec.release() }
        recorder = null
        output = null
        return if (ok && file != null && file.length() > 0L) file else { file?.delete(); null }
    }

    /** Aborts an in-progress recording and discards the file (e.g. the screen was closed). */
    fun cancel() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        output?.delete()
        output = null
    }
}
