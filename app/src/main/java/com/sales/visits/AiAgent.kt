package com.sales.visits

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The agentic Assistant (its own tab) — powered by OpenAI. It holds a short conversation and can
 * propose ONE action per turn (add / reschedule a follow-up); the app confirms and executes it via
 * Store, so the model interprets while the app stays the source of truth. Answers are grounded in
 * locally-computed [facts] (no invented numbers). Uses the user's own OpenAI key.
 */
object AiAgent {
    private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"

    data class Msg(val role: String, val content: String)   // role = "user" | "assistant"
    data class Turn(val reply: String, val intent: CommandIntent)

    suspend fun run(apiKey: String, model: String, history: List<Msg>, facts: String, today: String, english: Boolean): Turn =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            require(key.isNotBlank()) { "missing_openai_key" }
            val lang = if (english) "English" else "Egyptian Arabic"
            val system = """You are VisitFlow's assistant for a field sales rep — a normal conversational AI. Chat naturally in $lang, remember the conversation so far, and answer follow-up questions in context. Be helpful and concise, but it's fine to have a back-and-forth.
Answer from the CONTEXT below (the rep's own data: today's facts + records relevant to their question). Never invent customer names, numbers, dates, or outcomes that aren't there. If the data doesn't cover something, say so plainly and offer to help another way. Never promise anything to a customer.
You may ALSO propose ONE action for the rep when they clearly want it (the app confirms before running it):
 - "add_task": create a follow-up. Fields: customer, text (what to do), date (yyyy-MM-dd), time (HH:mm, optional).
 - "reschedule_task": move a follow-up. Fields: query (which task/customer), date (yyyy-MM-dd).
 - "none": just talk/answer, no action (use this for most messages).
Resolve relative dates ("tomorrow", "next Sunday") against today=$today.
Respond with ONLY a JSON object, no prose around it:
{"reply": "<your conversational message>", "action": {"type": "none|add_task|reschedule_task", "customer": "", "text": "", "date": "", "time": "", "query": ""}}

CONTEXT (the rep's data, computed now):
$facts"""

            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", system))
            history.takeLast(12).forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }

            val body = JSONObject()
                .put("model", model.trim().ifBlank { "gpt-4o-mini" })
                .put("messages", messages)
                .put("temperature", 0.3)
                .put("response_format", JSONObject().put("type", "json_object"))

            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 20000; readTimeout = 90000; doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $key")
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) {
                    val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                    throw Exception(openAiError(err, conn.responseCode))
                }
                val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val content = root.optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content").orEmpty().trim()
                parse(content)
            } finally {
                conn.disconnect()
            }
        }

    private fun parse(content: String): Turn {
        val obj = runCatching { JSONObject(content) }.getOrNull()
            ?: return Turn(content.ifBlank { "…" }, CommandIntent())
        val reply = obj.optString("reply").ifBlank { "…" }
        val a = obj.optJSONObject("action")
        val action = when (a?.optString("type")?.lowercase()) {
            "add_task" -> CommandAction.ADD_TASK
            "reschedule_task" -> CommandAction.RESCHEDULE_TASK
            else -> CommandAction.UNKNOWN
        }
        val intent = CommandIntent(
            action = action,
            customer = a?.optString("customer").orEmpty(),
            text = a?.optString("text").orEmpty(),
            date = a?.optString("date").orEmpty(),
            time = a?.optString("time").orEmpty(),
            query = a?.optString("query").orEmpty(),
        )
        return Turn(reply, intent)
    }

    private fun openAiError(body: String, code: Int): String {
        val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
        return when {
            code == 401 -> "invalid_openai_key"
            code == 429 -> "openai_rate_limited"
            !msg.isNullOrBlank() -> msg
            else -> "HTTP $code"
        }
    }
}
