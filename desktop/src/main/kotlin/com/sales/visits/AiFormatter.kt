package com.sales.visits

import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The two professional versions produced from a raw visit note. */
data class FormattedNote(val ar: String, val en: String)

/** Both versions as one block of text, ready to drop into the visit's notes field. */
fun FormattedNote.combined(): String = listOf(ar, en).filter { it.isNotBlank() }.joinToString("\n\n")

/**
 * Turns a salesperson's raw visit notes into a clean, professional report entry in both
 * Arabic and English, using the user's own Gemini API key (bring-your-own-key).
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

    /**
     * @throws IllegalArgumentException when the key or text is blank
     * @throws Exception on network/HTTP/parse failure (message is safe to surface)
     */
    suspend fun format(apiKey: String, model: String, rawText: String): FormattedNote {
        require(rawText.isNotBlank()) { "empty_text" }
        val parts = JSONArray().put(JSONObject().put("text", PROMPT_TEXT + rawText.trim()))
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

    /** Parses a natural-language command into a structured [CommandIntent] (plan 4.4/6.5). */
    suspend fun parseCommand(apiKey: String, model: String, text: String, today: String): CommandIntent {
        require(text.isNotBlank()) { "empty_text" }
        val prompt = """Today is $today. Parse the salesperson's command into JSON. Do not execute anything.
"action" is one of: ADD_TASK, RESCHEDULE_TASK, SEARCH, UNKNOWN.
Fields: "customer" (name if any), "text" (task wording for ADD_TASK), "date" (absolute yyyy-MM-dd resolved from any relative expression using today), "time" (HH:mm if any), "query" (search text or which task to reschedule).
Command: $text"""
        return withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                    put("responseSchema", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("action", JSONObject().put("type", "STRING"))
                            listOf("customer", "text", "date", "time", "query").forEach { put(it, JSONObject().put("type", "STRING")) }
                        })
                        put("required", JSONArray().put("action"))
                    })
                })
            }
            val j = postForJson(apiKey, model, body)
            val act = runCatching { CommandAction.valueOf(j.optString("action").trim().uppercase()) }.getOrDefault(CommandAction.UNKNOWN)
            CommandIntent(act, j.optString("customer").trim(), j.optString("text").trim(), j.optString("date").trim(), j.optString("time").trim(), j.optString("query").trim())
        }
    }

    private fun postForJson(apiKey: String, model: String, body: JSONObject): JSONObject {
        val key = apiKey.trim()
        require(key.isNotBlank()) { "missing_key" }
        val url = URL("$ENDPOINT/${model.trim()}:generateContent?key=$key")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20000; readTimeout = 90000; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                throw Exception(geminiError(err, conn.responseCode))
            }
            val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val jsonText = root.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
            if (jsonText.isBlank()) throw Exception("empty_response")
            return JSONObject(jsonText)
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