package com.sales.visits

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.Locale

private data class ChatMsg(val role: String, val text: String)   // role: "user" | "assistant" | "result"

/**
 * The Assistant tab — an OpenAI-powered agent that answers about the rep's data and can DO one action
 * per turn (add / reschedule a follow-up) after the rep confirms. Grounded in locally-computed facts.
 * Voice optional (mic to talk, replies read aloud). Uses the user's own OpenAI key (Settings).
 */
@Composable
fun AssistantScreen(store: Store) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val langTag = if (t.en) "en-US" else "ar"

    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<CommandIntent?>(null) }
    var listening by remember { mutableStateOf(false) }
    var speakReplies by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val tts = remember {
        var e: TextToSpeech? = null
        e = TextToSpeech(ctx.applicationContext) { s -> if (s == TextToSpeech.SUCCESS) e?.language = Locale.forLanguageTag(langTag) }
        e
    }
    DisposableEffect(Unit) { onDispose { runCatching { tts.stop(); tts.shutdown() } } }
    fun speak(s: String) { if (speakReplies) runCatching { tts.language = Locale.forLanguageTag(langTag); tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "a") } }

    fun resolveCustomer(name: String): Customer? {
        if (name.isBlank()) return null
        store.customerFor(name)?.let { return it }
        return store.customers.filter { it.name.contains(name, true) }.singleOrNull()
    }

    fun send(text: String) {
        val q = text.trim()
        if (q.isBlank() || loading) return
        if (store.openAiKey.isBlank()) { messages.add(ChatMsg("result", t["agent_need_key"])); return }
        messages.add(ChatMsg("user", q)); input = ""; loading = true; pending = null
        val history = messages.filter { it.role == "user" || it.role == "assistant" }.map { AiAgent.Msg(if (it.role == "user") "user" else "assistant", it.text) }
        scope.launch {
            try {
                val facts = assistantFacts(store, t.en)
                val turn = AiAgent.run(store.openAiKey, store.openAiModel, history, facts, todayIso(), t.en)
                messages.add(ChatMsg("assistant", turn.reply)); speak(turn.reply)
                if (turn.intent.action == CommandAction.ADD_TASK || turn.intent.action == CommandAction.RESCHEDULE_TASK) pending = turn.intent
            } catch (e: Exception) {
                messages.add(ChatMsg("result", "${t["ai_failed"]}: ${agentError(e.message, t)}"))
            } finally { loading = false }
        }
    }

    // Speech-to-text (optional).
    val recognizer = remember { if (SpeechRecognizer.isRecognitionAvailable(ctx)) SpeechRecognizer.createSpeechRecognizer(ctx) else null }
    DisposableEffect(Unit) { onDispose { runCatching { recognizer?.destroy() } } }
    remember(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(e: Int) { listening = false }
            override fun onResults(r: Bundle?) {
                listening = false
                r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { send(it) }
            }
            override fun onPartialResults(p: Bundle?) { p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { input = it } }
            override fun onEvent(e: Int, p: Bundle?) {}
        }); recognizer
    }
    fun startListening() {
        val rec = recognizer ?: return
        speakReplies = true   // if the rep talks, read replies back
        input = ""; listening = true
        rec.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
        })
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g -> if (g) startListening() }
    fun onMic() {
        if (listening) { runCatching { recognizer?.stopListening() }; listening = false; return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t["assistant_tab"], fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink, modifier = Modifier.weight(1f))
            if (messages.isNotEmpty()) TextButton(onClick = { messages.clear(); pending = null }) { Text(t["agent_clear"], color = c.muted) }
        }

        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Mic, null, tint = c.ink2, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(t["agent_intro"], color = c.ink2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                // Tappable example prompts.
                listOf(t["agent_ex1"], t["agent_ex2"], t["agent_ex3"]).forEach { ex ->
                    Surface(
                        onClick = { send(ex) },
                        shape = RoundedCornerShape(14.dp), color = c.surface,
                        border = BorderStroke(1.dp, c.edge),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("💬", fontSize = 15.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(ex, color = c.ink, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { m -> ChatBubble(m) }
                if (loading) item { Text(t["agent_thinking"], color = c.muted, fontSize = 12.5.sp, modifier = Modifier.padding(6.dp)) }
                pending?.let { intent -> item { ActionCard(store, intent, ::resolveCustomer, onDone = { msg -> messages.add(ChatMsg("result", msg)); pending = null }, onCancel = { pending = null }) } }
            }
        }

        // Input bar — one rounded pill, lifted clear of the floating bottom nav.
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 86.dp)) {
            Surface(
                shape = RoundedCornerShape(26.dp), color = c.surface,
                border = BorderStroke(1.dp, if (listening) c.ink else c.edge),
                shadowElevation = if (c.dark) 0.dp else 6.dp, modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(if (listening) c.lost else c.sunk).clickable { onMic() }, contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Mic, t["voice_tap_to_talk"], tint = if (listening) c.onInk else c.ink2, modifier = Modifier.size(20.dp))
                    }
                    Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        if (input.isEmpty()) Text(t["agent_placeholder"], color = c.faint, fontSize = 15.sp)
                        BasicTextField(
                            value = input, onValueChange = { input = it },
                            textStyle = TextStyle(color = c.ink, fontSize = 15.sp),
                            cursorBrush = SolidColor(c.ink),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send(input) }),
                            maxLines = 4, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val ready = input.isNotBlank()
                    Box(Modifier.size(42.dp).clip(CircleShape).background(if (ready) c.ink else c.sunk).clickable { send(input) }, contentAlignment = Alignment.Center) {
                        Icon(if (t.en) AppIcons.ArrowForward else AppIcons.ArrowBack, t["send"], tint = if (ready) c.onInk else c.faint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMsg) {
    val c = LocalSales.current
    val mine = m.role == "user"
    val isResult = m.role == "result"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) c.ink else if (isResult) c.ok.copy(alpha = 0.15f) else c.surface,
            border = if (mine) null else androidx.compose.foundation.BorderStroke(1.dp, c.edge),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(m.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (mine) c.onInk else if (isResult) c.ok else c.ink, fontSize = 14.5.sp, lineHeight = 20.sp,
                fontWeight = if (isResult) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun ActionCard(store: Store, intent: CommandIntent, resolve: (String) -> Customer?, onDone: (String) -> Unit, onCancel: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    Surface(shape = RoundedCornerShape(16.dp), color = c.sunk, border = androidx.compose.foundation.BorderStroke(1.dp, c.ink), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (intent.action == CommandAction.ADD_TASK) {
                Text(t["cmd_add_task"], color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(intent.text.ifBlank { "—" }, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                val meta = listOfNotNull(
                    intent.customer.takeIf { it.isNotBlank() }?.let { "@$it" },
                    intent.date.takeIf { it.isNotBlank() }?.let { fullDay(it) },
                    intent.time.takeIf { it.isNotBlank() }?.let { fmtTime(it) },
                ).joinToString(" · ")
                if (meta.isNotBlank()) { Spacer(Modifier.height(3.dp)); Text(meta, color = c.muted, fontSize = 12.5.sp) }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConfirmButton(t["cmd_confirm"], enabled = intent.text.isNotBlank(), modifier = Modifier.weight(1f)) {
                        val date = intent.date.ifBlank { todayIso() }
                        if (taskExists(store.tasks, intent.customer, intent.text, date)) onDone(t["cmd_dup"])
                        else { store.addTask(intent.text, intent.customer, date, intent.time, source = TaskSource.AI); onDone(t["cmd_added"]) }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onCancel) { Text(t["cancel"], color = c.muted) }
                }
            } else {
                val q = intent.query.ifBlank { intent.text }.ifBlank { intent.customer }
                val match = store.tasks.firstOrNull {
                    it.statusEnum() != TaskStatus.DONE && it.statusEnum() != TaskStatus.CANCELLED &&
                        (it.action.contains(q, true) || it.client.contains(q, true))
                }
                if (match == null) Text(t["cmd_task_not_found"], color = c.muted, fontSize = 13.sp)
                else {
                    Text(t["cmd_reschedule"], color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(match.action.ifBlank { match.client }, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${fullDay(match.date)} → ${if (intent.date.isNotBlank()) fullDay(intent.date) else "—"}", color = c.muted, fontSize = 12.5.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConfirmButton(t["cmd_confirm"], enabled = intent.date.isNotBlank(), modifier = Modifier.weight(1f)) {
                            store.rescheduleTask(match.id, intent.date); onDone(t["cmd_moved"])
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onCancel) { Text(t["cancel"], color = c.muted) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalSales.current
    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) c.ink else c.sunk,
        modifier = modifier.height(48.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = if (enabled) c.onInk else c.faint, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

private fun agentError(msg: String?, t: L): String = when (msg) {
    "invalid_openai_key" -> t["agent_bad_key"]
    "openai_rate_limited" -> t["agent_rate"]
    "missing_openai_key" -> t["agent_need_key"]
    else -> msg ?: "error"
}
