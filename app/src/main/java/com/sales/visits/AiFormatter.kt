package com.sales.visits

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The two professional versions produced from a raw visit note. */
data class FormattedNote(val ar: String, val en: String)

/**
 * Turns a salesperson's raw visit notes — typed text OR a voice recording — into a clean,
 * professional report entry in both Arabic and English, using the user's own Gemini API key
 * (bring-your-own-key — the key lives only in this device's settings).
 *
 * For audio, the recording is sent as-is to Gemini (a multimodal model): it understands the
 * speech in any language or dialect, then formats and translates it. No on-device transcription.
 *
 * Kept small and provider-agnostic at the call site so other providers can be added later.
 */
object AiFormatter {
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

    private const val PROMPT_TEXT =
        """You are a professional sales assistant. Reformat the salesperson's raw visit notes into a clear, well-structured, professional visit report entry.
Rules:
- Preserve every fact. Do NOT invent, add, or assume any detail that is not present.
- Fix grammar, spelling, and structure. Keep it concise and business-appropriate.
Produce two versions of the SAME content: "ar" in professional Modern Standard Arabic, "en" in professional English.

Raw visit notes:
"""

    private const val PROMPT_AUDIO =
        """You are a professional sales assistant. The attached audio is a salesperson describing a sales visit, spoken in any language or dialect (often Egyptian Arabic).
Listen to it, understand the content, then write a clear, well-structured, professional visit report entry.
Rules:
- Preserve every fact you hear. Do NOT invent, add, or assume any detail that was not said.
- Fix grammar and structure. Keep it concise and business-appropriate.
Produce two versions of the SAME content: "ar" in professional Modern Standard Arabic, "en" in professional English."""

    /**
     * @throws IllegalArgumentException when the key or text is blank
     * @throws Exception on network/HTTP/parse failure (message is safe to surface)
     */
    suspend fun format(apiKey: String, model: String, rawText: String): FormattedNote {
        require(rawText.isNotBlank()) { "empty_text" }
        val parts = JSONArray().put(JSONObject().put("text", PROMPT_TEXT + rawText.trim()))
        return request(apiKey, model, parts)
    }

    /**
     * Sends a voice recording for the model to understand and format directly.
     * @throws IllegalArgumentException when the key is blank or audio is empty
     * @throws Exception on network/HTTP/parse failure
     */
    suspend fun formatAudio(apiKey: String, model: String, audio: ByteArray, mimeType: String): FormattedNote {
        require(audio.isNotEmpty()) { "empty_audio" }
        val parts = JSONArray()
            .put(JSONObject().put("text", PROMPT_AUDIO))
            .put(JSONObject().put("inline_data", JSONObject().apply {
                put("mime_type", mimeType)
                put("data", Base64.encodeToString(audio, Base64.NO_WRAP))
            }))
        return request(apiKey, model, parts)
    }

    private suspend fun request(apiKey: String, model: String, parts: JSONArray): FormattedNote =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            require(key.isNotBlank()) { "missing_key" }

            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("responseMimeType", "application/json")
                    put("responseSchema", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("ar", JSONObject().put("type", "STRING"))
                            put("en", JSONObject().put("type", "STRING"))
                        })
                        put("required", JSONArray().put("ar").put("en"))
                    })
                })
            }

            val url = URL("$ENDPOINT/${model.trim()}:generateContent?key=$key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 90000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) {
                    val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }
                        .getOrNull().orEmpty()
                    throw Exception(geminiError(err, conn.responseCode))
                }
                val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val jsonText = root
                    .optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                    ?.optString("text").orEmpty()
                if (jsonText.isBlank()) throw Exception("empty_response")
                val parsed = JSONObject(jsonText)
                FormattedNote(ar = parsed.optString("ar").trim(), en = parsed.optString("en").trim())
            } finally {
                conn.disconnect()
            }
        }

    /** Extracts a readable reason from a Gemini error body, falling back to the HTTP code. */
    private fun geminiError(body: String, code: Int): String {
        val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }
            .getOrNull().orEmpty()
        return msg.ifBlank { "HTTP $code" }
    }
}
