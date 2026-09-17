package com.sales.visits

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Voice assistant (talk to your data). On-device speech-to-text (SpeechRecognizer) captures the rep's
 * question; the app computes the facts locally (never invented); Gemini phrases a short spoken answer;
 * TextToSpeech reads it back. Free (Android STT/TTS) and grounded — no OpenAI, no realtime streaming.
 */

/** Compact, locally-computed facts about the rep's day/pipeline — the only thing the model may use. */
internal fun assistantFacts(store: Store, en: Boolean): String = buildString {
    val today = todayIso()
    appendLine("Today is $today.")
    val plan = store.planFor(today)
    val doneP = plan.count { it.done }
    appendLine("Today's route: ${plan.size} stops, $doneP done, ${plan.size - doneP} left.")
    plan.take(12).forEach { appendLine("- ${it.client.ifBlank { "(no name)" }}: ${it.action.ifBlank { "visit" }}${if (it.time.isNotBlank()) " at ${it.time}" else ""}${if (it.done) " [done]" else ""}") }
    val due = store.dueFollowUps()
    appendLine("Due follow-ups: ${due.size}.")
    due.take(10).forEach { appendLine("- ${it.client}: ${it.next} (due ${it.nextDate})") }
    val overdue = DataQueries.overdueTasks(store.tasks, today)
    appendLine("Overdue tasks: ${overdue.size}.")
    overdue.take(10).forEach { appendLine("- ${it.title}") }
    val openOpps = store.opportunities.filter { it.stageEnum().isActive }
    appendLine("Open opportunities: ${openOpps.size}.")
    openOpps.groupBy { it.currency.ifBlank { "—" } }.forEach { (cur, list) ->
        appendLine("- open value ${fmtMoney(list.sumOf { it.value })} $cur")
    }
    openOpps.sortedByDescending { it.value }.take(6).forEach { appendLine("- ${it.title} (${it.customerName}) ${fmtMoney(it.value)} ${it.currency} · stage ${it.stageEnum().label(en)}") }
    val quotesNoFu = DataQueries.sentQuotesNoFollowup(store.quotes, store.activities)
    appendLine("Sent quotes with no logged follow-up: ${quotesNoFu.size}.")
    val oppsNoNext = DataQueries.oppsNoNextStep(store.opportunities)
    appendLine("Active opportunities with no next step: ${oppsNoNext.size}.")
    val prospects = DataQueries.prospects(store.customers)
    appendLine("Prospects (not yet customers): ${prospects.size}.")
    appendLine("Total customers: ${store.customers.size}.")
}

/** Day facts PLUS records relevant to [question] (searched locally by keyword), so the assistant can
 *  answer detailed questions about a specific customer / deal / visit — from real data, never invented. */
internal fun assistantContext(store: Store, question: String, en: Boolean): String {
    val base = assistantFacts(store, en)
    val stop = setOf("the","and","for","with","what","when","who","how","my","me","about","is","are","على","في","عن","مين","إيه","ايه","كام","ايه","من","ال","اللي","الي","يا")
    val keys = question.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        .map { it.trim() }.filter { it.length >= 2 && it !in stop }.distinct()
    if (keys.isEmpty()) return base
    fun hit(text: String): Boolean { val l = text.lowercase(); return keys.any { l.contains(it) } }
    fun trim(s: String, n: Int = 260) = s.replace("\n", " ").trim().let { if (it.length > n) it.take(n) + "…" else it }

    val custs = store.customers.filter { hit(it.name + " " + it.contact + " " + it.city + " " + it.industry + " " + it.phone + " " + it.notes) }.take(4)
    val sb = StringBuilder()
    fun line(s: String) { sb.append(s); sb.append('\n') }
    custs.forEach { cu ->
        val where = listOf(cu.city, cu.industry).filter { it.isNotBlank() }.joinToString()
        line("CUSTOMER: " + cu.name + (if (where.isNotBlank()) " ($where)" else ""))
        if (cu.phone.isNotBlank()) line("  phone: " + cu.phone)
        if (cu.notes.isNotBlank()) line("  notes: " + trim(cu.notes))
        cu.contacts.take(4).forEach { cp ->
            line("  contact: " + cp.name + (if (cp.jobTitle.isNotBlank()) " — " + cp.jobTitle else "") + (if (cp.phone.isNotBlank()) " " + cp.phone else ""))
        }
        store.visits.filter { it.customerId == cu.id || it.client.trim().equals(cu.name.trim(), true) }.sortedByDescending { it.date }.take(3).forEach { v ->
            val note = v.notesAr.ifBlank { v.notesEn }.ifBlank { v.notes }
            line("  visit " + v.date + " [" + v.type + "/" + v.outcome + "]" + (if (v.next.isNotBlank()) " next: " + v.next else "") + (if (note.isNotBlank()) " — " + trim(note) else ""))
        }
        store.opportunities.filter { it.customerId == cu.id || it.customerName.trim().equals(cu.name.trim(), true) }.take(4).forEach { o ->
            line("  deal: " + o.title + " " + fmtMoney(o.value) + " " + o.currency + " · " + o.stageEnum().label(en) + (if (o.need.isNotBlank()) " · need: " + trim(o.need, 100) else "") + (if (o.nextStep.isNotBlank()) " · next: " + trim(o.nextStep, 100) else ""))
        }
        store.activitiesForCustomer(cu).sortedByDescending { it.date }.take(3).forEach { a ->
            line("  activity " + a.date + " [" + a.type + "]" + (if (a.summary.isNotBlank()) " — " + trim(a.summary, 140) else ""))
        }
    }
    // Deals / visits matching the question but whose customer wasn't already included.
    val coveredNames = custs.map { it.name.trim().lowercase() }.toSet()
    store.opportunities.filter { hit(it.title + " " + it.customerName + " " + it.need + " " + it.notes) && it.customerName.trim().lowercase() !in coveredNames }
        .sortedByDescending { it.value }.take(5).forEach { o ->
            line("DEAL: " + o.title + " (" + o.customerName + ") " + fmtMoney(o.value) + " " + o.currency + " · " + o.stageEnum().label(en) + (if (o.nextStep.isNotBlank()) " · next: " + trim(o.nextStep, 100) else ""))
        }
    store.visits.filter { hit(it.client + " " + it.notes + " " + it.notesAr + " " + it.notesEn + " " + it.next) && it.client.trim().lowercase() !in coveredNames }
        .sortedByDescending { it.date }.take(5).forEach { v ->
            val note = v.notesAr.ifBlank { v.notesEn }.ifBlank { v.notes }
            line("VISIT: " + v.client + " " + v.date + " [" + v.type + "/" + v.outcome + "]" + (if (note.isNotBlank()) " — " + trim(note) else ""))
        }
    return if (sb.isEmpty()) base else base + "\n\nRELEVANT RECORDS (matched to the question):\n" + sb.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantSheet(store: Store, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    val langTag = if (t.en) "en-US" else "ar"

    var phase by remember { mutableStateOf("idle") }   // idle | listening | thinking | speaking | error
    var heard by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var consentAsk by remember { mutableStateOf(false) }

    // TextToSpeech — created once, released on dispose.
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) engine?.language = Locale.forLanguageTag(langTag)
        }
        engine
    }
    DisposableEffect(Unit) { onDispose { runCatching { tts.stop(); tts.shutdown() } } }

    fun speak(text: String) {
        runCatching { tts.language = Locale.forLanguageTag(langTag) }
        phase = "speaking"
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer")
    }

    fun ask(question: String) {
        heard = question
        if (question.isBlank()) { phase = "idle"; return }
        when {
            store.apiKey.isBlank() -> { errorMsg = t["need_key"]; phase = "error"; return }
            !store.aiConsent -> { consentAsk = true; phase = "idle"; return }
        }
        phase = "thinking"; answer = ""; errorMsg = null
        scope.launch {
            try {
                val facts = assistantFacts(store, t.en)
                answer = AiFormatter.assistant(store.apiKey, store.aiModel, question, facts, t.en)
                speak(answer)
            } catch (e: Exception) {
                errorMsg = "${t["ai_failed"]}: ${e.message}"; phase = "error"
            }
        }
    }

    // SpeechRecognizer — created once; its listener drives the phase/transcript.
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(ctx)) SpeechRecognizer.createSpeechRecognizer(ctx) else null
    }
    DisposableEffect(Unit) { onDispose { runCatching { recognizer?.destroy() } } }
    remember(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { phase = "listening" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (phase == "listening") { errorMsg = t["voice_didnt_catch"]; phase = "error" }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                ask(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer
    }

    fun startListening() {
        if (recognizer == null) { errorMsg = t["voice_unavailable"]; phase = "error"; return }
        runCatching { tts.stop() }
        heard = ""; answer = ""; errorMsg = null; phase = "listening"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        runCatching { recognizer.startListening(intent) }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else { errorMsg = t["voice_need_mic"]; phase = "error" }
    }
    fun onMic() {
        if (phase == "listening") { runCatching { recognizer?.stopListening() }; return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t["voice_assistant"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            Text(t["voice_assistant_desc"], fontSize = 12.5.sp, color = c.muted, modifier = Modifier.padding(bottom = 18.dp))

            // Transcript / answer area
            if (heard.isNotBlank()) {
                Surface(shape = RoundedCornerShape(16.dp), color = c.sunk, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(t["voice_you_said"], color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(heard, color = c.ink, fontSize = 15.sp)
                    }
                }
            }
            if (answer.isNotBlank()) {
                Surface(shape = RoundedCornerShape(16.dp), color = c.surface, border = androidx.compose.foundation.BorderStroke(1.dp, c.edge), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(Modifier.padding(14.dp)) {
                        Text(answer, color = c.ink, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
                        if (phase != "speaking") IconButton(onClick = { speak(answer) }, modifier = Modifier.size(28.dp)) {
                            Icon(AppIcons.Mic, t["voice_replay"], tint = c.ink2, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (errorMsg != null) Text(errorMsg!!, color = c.lost, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))

            val status = when (phase) {
                "listening" -> t["voice_listening"]; "thinking" -> t["voice_thinking"]
                "speaking" -> t["voice_speaking"]; else -> t["voice_tap_to_talk"]
            }
            Text(status, color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 12.dp))

            // Mic button
            val active = phase == "listening"
            Box(
                Modifier.size(92.dp).clip(CircleShape)
                    .background(if (active) c.lost else c.ink)
                    .border(if (active) 4.dp else 0.dp, c.lost.copy(alpha = 0.3f), CircleShape)
                    .clickable { onMic() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Mic, t["voice_tap_to_talk"], tint = c.onInk, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(t["voice_examples"], color = c.faint, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }

    if (consentAsk) AlertDialog(
        onDismissRequest = { consentAsk = false },
        confirmButton = { TextButton(onClick = { store.grantAiConsent(); consentAsk = false; ask(heard) }) { Text(t["agree"]) } },
        dismissButton = { TextButton(onClick = { consentAsk = false }) { Text(t["cancel"], color = c.muted) } },
        title = { Text(t["ai_consent_title"]) }, text = { Text(t["ai_consent_desc"]) }, containerColor = c.surface,
    )
}
