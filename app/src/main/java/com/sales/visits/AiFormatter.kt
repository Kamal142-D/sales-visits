package com.sales.visits

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A follow-up action the AI inferred from the visit, to be added as a task. */
data class SuggestedTask(val title: String, val client: String = "")

/** The two professional versions produced from a raw visit note, plus any follow-up tasks. */
data class FormattedNote(val ar: String, val en: String, val tasks: List<SuggestedTask> = emptyList())

/** Both versions as one block of text, ready to drop into the visit's notes field. */
fun FormattedNote.combined(): String = listOf(ar, en).filter { it.isNotBlank() }.joinToString("\n\n")

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
        """You are a professional sales assistant. The attached audio is a salesperson describing a sales visit, spoken in any language or dialect (commonly Arabic, including Gulf/Saudi and Egyptian).
Listen to it, understand the content, then write a clear, well-structured, professional visit report entry.
Rules:
- Preserve every fact you hear. Do NOT invent, add, or assume any detail that was not said.
- Fix grammar and structure. Keep it concise and business-appropriate.
Produce two versions of the SAME content: "ar" in professional Modern Standard Arabic, "en" in professional English."""

    /**
     * Appended to either prompt. Asks the model to also infer the salesperson's next-step follow-up
     * tasks, using our current stock/prices to decide between "make a quotation" and "ask purchasing".
     */
    private fun tasksInstruction(inventory: String, taskLangName: String): String =
        """

Then decide whether the salesperson has any FOLLOW-UP ACTIONS to do next with this customer, and return them as "tasks".
Only add a task when the notes clearly imply a next step. Examples of signals:
- The customer is interested in a specific product → check it against OUR STOCK below. If it is in stock AND has a known price, add a task to prepare/send a price quotation. If it is out of stock or its price is unknown, add a task to email the purchasing manager asking for its price and lead/delivery time.
- The customer asked for a product brochure/catalog → add a task to send the brochure to the customer.
- The customer asked for a print sample / sample of the product → add a task to bring/get a sample for the customer.
- Any other explicit request or promised next step → add a matching task.
If there is no clear next step, return an empty "tasks" list. Do NOT invent tasks.
Each task "title" must be a short imperative action written in $taskLangName. Keep it specific (mention the product/customer when known).

OUR STOCK (name | availability | price):
$inventory"""

    /**
     * @throws IllegalArgumentException when the key or text is blank
     * @throws Exception on network/HTTP/parse failure (message is safe to surface)
     */
    suspend fun format(
        apiKey: String, model: String, rawText: String,
        inventory: String = "(no stock records)", taskLangEnglish: Boolean = true,
    ): FormattedNote {
        require(rawText.isNotBlank()) { "empty_text" }
        val lang = if (taskLangEnglish) "English" else "Arabic"
        val parts = JSONArray().put(JSONObject().put("text", PROMPT_TEXT + rawText.trim() + tasksInstruction(inventory, lang)))
        return request(apiKey, model, parts)
    }

    /**
     * Sends a voice recording for the model to understand and format directly.
     * @throws IllegalArgumentException when the key is blank or audio is empty
     * @throws Exception on network/HTTP/parse failure
     */
    suspend fun formatAudio(
        apiKey: String, model: String, audio: ByteArray, mimeType: String,
        inventory: String = "(no stock records)", taskLangEnglish: Boolean = false,
    ): FormattedNote {
        require(audio.isNotEmpty()) { "empty_audio" }
        val lang = if (taskLangEnglish) "English" else "Arabic"
        val parts = JSONArray()
            .put(JSONObject().put("text", PROMPT_AUDIO + tasksInstruction(inventory, lang)))
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
                            put("tasks", JSONObject().apply {
                                put("type", "ARRAY")
                                put("items", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("title", JSONObject().put("type", "STRING"))
                                        put("client", JSONObject().put("type", "STRING"))
                                    })
                                    put("required", JSONArray().put("title"))
                                })
                            })
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
                val tasks = mutableListOf<SuggestedTask>()
                parsed.optJSONArray("tasks")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val title = o.optString("title").trim()
                        if (title.isNotBlank()) tasks.add(SuggestedTask(title, o.optString("client").trim()))
                    }
                }
                FormattedNote(ar = parsed.optString("ar").trim(), en = parsed.optString("en").trim(), tasks = tasks)
            } finally {
                conn.disconnect()
            }
        }

    /** A pre-visit brief (plan 4.1) generated from recorded context only. */
    data class PrevisitBrief(
        val summary: String,
        val goal: String,
        val questions: List<String>,
        val commitments: List<String>,
        val watchOut: List<String>,
    )

    /**
     * Produces a pre-visit brief from the assembled [context] (see [PrevisitContext]). The model is
     * told to use ONLY the context and never invent facts. Output text is in the chosen language.
     */
    suspend fun prepareVisit(apiKey: String, model: String, context: String, english: Boolean): PrevisitBrief {
        require(context.isNotBlank()) { "empty_context" }
        val lang = if (english) "English" else "Arabic"
        val prompt = """You are a sales assistant preparing a salesperson for an upcoming visit.
Use ONLY the context below. Do NOT invent facts; if something is not in the context, treat it as unknown and, where useful, turn it into a question to ask.
Write ALL output text in $lang. Return JSON with:
- "summary": 2-3 sentence recap of where things stand.
- "goal": one suggested, concrete objective for this visit.
- "questions": array of the most important missing/clarifying questions to ask.
- "commitments": array of past promises or open items to follow through on (empty if none).
- "watchOut": array of risks or things to verify (empty if none).

CONTEXT:
$context"""
        return withContext(Dispatchers.IO) {
            val parts = JSONArray().put(JSONObject().put("text", prompt))
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("responseMimeType", "application/json")
                    put("responseSchema", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("summary", JSONObject().put("type", "STRING"))
                            put("goal", JSONObject().put("type", "STRING"))
                            fun strArray() = JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING"))
                            put("questions", strArray())
                            put("commitments", strArray())
                            put("watchOut", strArray())
                        })
                        put("required", JSONArray().put("summary").put("goal"))
                    })
                })
            }
            val json = postForJson(apiKey, model, body)
            fun arr(name: String): List<String> {
                val out = ArrayList<String>()
                json.optJSONArray(name)?.let { for (i in 0 until it.length()) it.optString(i).trim().takeIf { s -> s.isNotBlank() }?.let(out::add) }
                return out
            }
            PrevisitBrief(
                summary = json.optString("summary").trim(),
                goal = json.optString("goal").trim(),
                questions = arr("questions"), commitments = arr("commitments"), watchOut = arr("watchOut"),
            )
        }
    }

    /** A drafted message (plan 4.3): an email subject + body, or (for a chat message) just a body. */
    data class DraftResult(val subject: String, val body: String)

    private fun draftGoal(type: DraftType): String = when (type) {
        DraftType.POST_VISIT -> "a short, warm follow-up recapping a sales visit and proposing a next step"
        DraftType.QUOTE_FOLLOWUP -> "a polite follow-up on a price quote we already sent"
        DraftType.MEETING_REQUEST -> "a brief message requesting to schedule a meeting"
        DraftType.PRODUCT_INFO -> "a message sharing the product information the customer asked about"
        DraftType.PROCUREMENT_PRICE -> "an internal email to our purchasing/procurement team asking for a product's price and expected lead/delivery time"
    }

    /** Drafts a message from [context]. Guardrails: never promise unconfirmed discount/stock/delivery,
     *  never claim an attachment, and ask (don't invent) when a price/product detail is unknown. */
    suspend fun draftMessage(apiKey: String, model: String, type: DraftType, tone: DraftTone, context: String, english: Boolean): DraftResult {
        require(context.isNotBlank()) { "empty_context" }
        val lang = if (english) "English" else "Arabic"
        val toneWord = when (tone) { DraftTone.FRIENDLY -> "friendly and warm"; DraftTone.FORMAL -> "formal and professional"; DraftTone.CONCISE -> "very concise" }
        val prompt = """You are a sales assistant drafting ${draftGoal(type)}.
Tone: $toneWord. Write ALL text in $lang.
Use ONLY the context below. Strict rules:
- Do NOT promise any discount, stock availability, or delivery/lead time unless it explicitly appears in the context.
- Do NOT say anything is "attached" — there is no attachment.
- If a needed price or product detail is unknown, either ask for it or leave a clear [placeholder] — never invent a value.
Return JSON with "subject" (a short email subject; empty string for an internal or chat message if not needed) and "body" (the message text, ready to edit and send).

CONTEXT:
$context"""
        return withContext(Dispatchers.IO) {
            val parts = JSONArray().put(JSONObject().put("text", prompt))
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                    put("responseMimeType", "application/json")
                    put("responseSchema", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("subject", JSONObject().put("type", "STRING"))
                            put("body", JSONObject().put("type", "STRING"))
                        })
                        put("required", JSONArray().put("body"))
                    })
                })
            }
            val json = postForJson(apiKey, model, body)
            DraftResult(subject = json.optString("subject").trim(), body = json.optString("body").trim())
        }
    }

    /** Structured facts extracted from a visit note (plan 4.2). Empty = not explicitly stated. */
    data class VisitExtract(
        val need: String = "",
        val problem: String = "",
        val product: String = "",
        val budgetStatus: String = "",   // UNKNOWN | HAS_BUDGET | NO_BUDGET or ""
        val closeDate: String = "",      // yyyy-MM-dd or ""
        val competitor: String = "",
        val objection: String = "",
        val nextStep: String = "",
    ) {
        fun isEmpty() = listOf(need, problem, product, budgetStatus, closeDate, competitor, objection, nextStep).all { it.isBlank() }
    }

    /** Extracts structured deal facts from a visit note. Fills a field ONLY when explicitly present. */
    suspend fun extractVisit(apiKey: String, model: String, noteText: String): VisitExtract {
        require(noteText.isNotBlank()) { "empty_text" }
        val prompt = """Extract structured sales facts from the visit note below.
Fill a field ONLY if it is explicitly stated. If something is not clearly stated, leave that field an empty string. Do NOT infer or guess.
Fields: need, problem, product (product/model of interest), budgetStatus (exactly one of: UNKNOWN, HAS_BUDGET, NO_BUDGET), closeDate (expected purchase date as yyyy-MM-dd, else empty), competitor, objection, nextStep.

VISIT NOTE:
$noteText"""
        return withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                    put("responseSchema", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            listOf("need", "problem", "product", "budgetStatus", "closeDate", "competitor", "objection", "nextStep")
                                .forEach { put(it, JSONObject().put("type", "STRING")) }
                        })
                    })
                })
            }
            val j = postForJson(apiKey, model, body)
            fun s(k: String) = j.optString(k).trim()
            val budget = s("budgetStatus").uppercase().takeIf { it in setOf("UNKNOWN", "HAS_BUDGET", "NO_BUDGET") }.orEmpty()
            VisitExtract(s("need"), s("problem"), s("product"), budget, s("closeDate"), s("competitor"), s("objection"), s("nextStep"))
        }
    }

    /** Parses a natural-language command into a structured [CommandIntent] (plan 4.4).
     *  The model ONLY classifies and extracts; the app validates/resolves/executes. Relative dates are
     *  resolved against [today]. */
    suspend fun parseCommand(apiKey: String, model: String, text: String, today: String): CommandIntent {
        require(text.isNotBlank()) { "empty_text" }
        val prompt = """Today is $today. Parse the salesperson's command into JSON. Do not execute anything.
"action" must be one of: ADD_TASK (create a follow-up/task), RESCHEDULE_TASK (move a task to another date), SEARCH (find a customer/deal/task), UNKNOWN.
Fields:
- "customer": the customer/person name if mentioned, else "".
- "text": the task/action wording for ADD_TASK, else "".
- "date": an absolute date yyyy-MM-dd resolved from any relative expression (e.g. "Thursday", "tomorrow") using today's date; else "".
- "time": HH:mm 24h if a time is mentioned, else "".
- "query": the search text for SEARCH, or a short phrase identifying which task to reschedule.
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

    /** Summarizes ALREADY-COMPUTED findings into a short narrative (plan 4.5). The numbers/records are
     *  computed by the app and passed in as [findings]; the model must not invent or recompute them. */
    suspend fun summarizeFindings(apiKey: String, model: String, question: String, findings: String, english: Boolean): String {
        val lang = if (english) "English" else "Arabic"
        val prompt = """You are a sales coach. Summarize the findings below in 2-4 short sentences in $lang.
Use ONLY these findings — do NOT invent or recompute numbers, and do NOT assert loss reasons, win probability, or product fit that isn't given. Suggest a concrete next action if useful.

QUESTION: $question
FINDINGS (computed by the app):
$findings"""
        return withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                put("generationConfig", JSONObject().put("temperature", 0.3))
            }
            postForText(apiKey, model, body)
        }
    }

    /** Voice assistant (talk-to-your-data): answers the rep's spoken question from locally-computed
     *  facts only, in 1-3 short spoken sentences. The app computes numbers; the model only phrases. */
    suspend fun assistant(apiKey: String, model: String, question: String, facts: String, english: Boolean): String {
        val lang = if (english) "English" else "Egyptian Arabic"
        val prompt = """You are VisitFlow's voice assistant for a field sales rep. Answer the rep's spoken question in $lang in 1-3 short, natural spoken sentences. This is read aloud, so no bullet points, no markdown, no lists.
Use ONLY the facts below, which the app just computed from the rep's own data. Do NOT invent names, numbers, dates, or outcomes not in the facts. If the facts don't cover it, say briefly that you don't have that. Never promise anything to a customer.

REP QUESTION: $question

FACTS (computed by the app now):
$facts"""
        return withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                put("generationConfig", JSONObject().put("temperature", 0.4))
            }
            postForText(apiKey, model, body)
        }
    }

    /** POST that returns the model's raw text output (for prose, not JSON). */
    private fun postForText(apiKey: String, model: String, body: JSONObject): String {
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
            return root.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty().trim()
        } finally {
            conn.disconnect()
        }
    }

    /** Shared POST that returns the parsed JSON object from the model's first candidate part. */
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
