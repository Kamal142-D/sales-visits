package com.sales.visits

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The two professional versions produced from a raw visit note. */
data class FormattedNote(val ar: String, val en: String)

/**
 * Turns a salesperson's raw, informal visit notes into a clean, professional
 * report entry in both Arabic and English, using the user's own Gemini API key
 * (bring-your-own-key — the key lives only in this device's settings).
 *
 * Currently backed by Google's Gemini API; kept small and provider-agnostic at the
 * call site so other providers can be added later without touching the callers.
 */
object AiFormatter {
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

    private const val PROMPT = """You are a professional sales assistant. Reformat the salesperson's raw visit notes into a clear, well-structured, professional visit report entry.
Rules:
- Preserve every fact. Do NOT invent, add, or assume any detail that is not present.
- Fix grammar, spelling, and structure. Keep it concise and business-appropriate.
- Do not include the customer's name, phone, or address unless they already appear in the notes.
Produce two versions of the SAME content:
- "ar": professional Modern Standard Arabic.
- "en": professional English.

Raw visit notes:
"""

    /**
     * @throws IllegalArgumentException when the key or text is blank
     * @throws Exception on network/HTTP/parse failure (message is safe to surface)
     */
    suspend fun format(apiKey: String, model: String, rawText: String): FormattedNote =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            val text = rawText.trim()
            require(key.isNotBlank()) { "missing_key" }
            require(text.isNotBlank()) { "empty_text" }

            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", PROMPT + text)))
                }))
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
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) {
                    val err = runCatching {
                        conn.errorStream?.bufferedReader()?.use { it.readText() }
                    }.getOrNull().orEmpty()
                    throw Exception(geminiError(err, conn.responseCode))
                }
                val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val jsonText = root
                    .optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                    ?.optString("text").orEmpty()
                if (jsonText.isBlank()) throw Exception("empty_response")
                val parsed = JSONObject(jsonText)
                FormattedNote(
                    ar = parsed.optString("ar").trim(),
                    en = parsed.optString("en").trim(),
                )
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
