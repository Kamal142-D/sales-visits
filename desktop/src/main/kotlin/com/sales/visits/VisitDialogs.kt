package com.sales.visits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ------------------------------- visit editor -------------------------------

@Composable
fun VisitEditorDialog(store: Store, initial: Visit?, onClose: () -> Unit) {
    val t = LocalL.current
    val c = LocalSales.current
    val en = I18n.en
    val id = initial?.id
        ?: (System.currentTimeMillis().toString(36) + (1000..9999).random().toString(36))
    var client by remember { mutableStateOf(initial?.client ?: "") }
    var contact by remember { mutableStateOf(initial?.contact ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: todayIso()) }
    var time by remember { mutableStateOf(initial?.time ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "FIRST_VISIT") }
    var outcome by remember { mutableStateOf(initial?.outcome ?: "NONE") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var next by remember { mutableStateOf(initial?.next ?: "") }
    var nextDate by remember { mutableStateOf(initial?.nextDate ?: "") }
    var checklist by remember { mutableStateOf(initial?.checklist ?: emptyList()) }
    var newItem by remember { mutableStateOf("") }
    var suggestionsOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val matches = remember(client) {
        if (client.isBlank()) emptyList()
        else store.customers.map { it.name }
            .filter { it.trim().startsWith(client.trim(), ignoreCase = true) && it.trim() != client.trim() }
            .distinct().take(5)
    }
    val typeOptions = VisitType.entries.map { it.name to it.label(en) }
    val outcomeOptions = Outcome.entries.map { it.name to it.label(en) }

    AppDialog(widthDp = 640, onDismiss = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text(t["visits"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
            Spacer(Modifier.height(14.dp))

            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    client, { client = it; suggestionsOpen = true },
                    label = { Text(t["client_name_hint"]) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = suggestionsOpen && matches.isNotEmpty(), onDismissRequest = { suggestionsOpen = false }) {
                    matches.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { client = it; suggestionsOpen = false }) }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    contact, { contact = it },
                    label = { Text(t["contact_hint"]) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    phone, { phone = it },
                    label = { Text(t["phone_hint"]) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                address, { address = it },
                label = { Text(t["address_hint"]) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    date, { date = it },
                    label = { Text(t["follow_date"]) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    time, { time = it },
                    label = { Text(t["time"]) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            DateField(value = date, onValueChange = { date = it }, label = null)

            Spacer(Modifier.height(14.dp))
            SectionLabel(t["visit_type"])
            Spacer(Modifier.height(6.dp))
            SelectChips(typeOptions, type) { type = it }

            Spacer(Modifier.height(14.dp))
            SectionLabel(t["outcome"])
            Spacer(Modifier.height(6.dp))
            SelectChips(outcomeOptions, outcome) { outcome = it }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                notes, { notes = it },
                label = { Text(t["notes_hint"]) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
            )

            Spacer(Modifier.height(14.dp))
            SectionLabel(t["checklist"])
            Spacer(Modifier.height(6.dp))
            checklist.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.done, onCheckedChange = { ch ->
                        checklist = checklist.mapIndexed { j, it -> if (j == i) it.copy(done = ch) else it }
                    })
                    Text(item.text, color = c.ink2, fontSize = 13.sp, modifier = Modifier.weight(1f),
                        textDecoration = if (item.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                    IconButton(onClick = { checklist = checklist.filterIndexed { j, _ -> j != i } }) {
                        Icon(AppIcons.Delete, null, tint = c.muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    newItem, { newItem = it },
                    label = { Text(t["checklist_hint"]) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    if (newItem.isNotBlank()) {
                        checklist = checklist + ChecklistItem(newItem.trim(), false)
                        newItem = ""
                    }
                }) { Text(t["done"]) }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                next, { next = it },
                label = { Text(t["next_hint"]) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            DateField(value = nextDate, onValueChange = { nextDate = it })

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, color = c.lost, fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text(t["cancel"], color = c.muted) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val v = Visit(
                        id = id, client = client.trim(), contact = contact.trim(),
                        phone = phone.trim(), address = address.trim(), date = date, time = time.trim(),
                        type = type, outcome = outcome, notes = notes,
                        notesAr = initial?.notesAr ?: "", notesEn = initial?.notesEn ?: "",
                        next = next.trim(), nextDate = nextDate,
                        checklist = checklist.filter { it.text.isNotBlank() },
                        images = initial?.images ?: emptyList(),
                    )
                    if (v.client.isBlank()) error = t["error_name"] else { store.upsert(v); onClose() }
                }) { Text(t["done"]) }
            }
        }
    }
}

// ------------------------------- visit detail -------------------------------

@Composable
fun VisitDetailDialog(store: Store, visitId: String, onClose: () -> Unit) {
    val t = LocalL.current
    val c = LocalSales.current
    val en = I18n.en
    val clipboard = LocalClipboardManager.current
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var polishing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val visit = store.visits.firstOrNull { it.id == visitId } ?: return

    if (editing) { VisitEditorDialog(store, visit, onClose = onClose); return }
    if (deleting) {
        ConfirmDialog(
            title = t["delete_visit"], desc = t["delete_all_desc"],
            confirmText = t["delete"], onConfirm = { store.delete(visitId); onClose() },
            onDismiss = { deleting = false },
        )
        return
    }

    AppDialog(widthDp = 600, onDismiss = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(visit.client, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = c.ink,
                    modifier = Modifier.weight(1f))
                Chip(visit.typeEnum().label(en), color = c.ink2, bg = c.sunk)
                Spacer(Modifier.width(6.dp))
                Chip(visit.outcomeEnum().label(en), color = c.onInk, bg = visit.outcomeEnum().color(c))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${cardDay(visit.date)}${if (visit.time.isNotBlank()) " · ${fmtTime(visit.time)}" else ""}",
                fontSize = 13.sp, color = c.muted,
            )

            if (visit.contact.isNotBlank() || visit.phone.isNotBlank() || visit.address.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Column(Modifier.clip(RoundedCornerShape(12.dp)).background(c.sunk).padding(12.dp)) {
                    if (visit.contact.isNotBlank()) rowInfo(AppIcons.Person, visit.contact, c)
                    if (visit.phone.isNotBlank()) rowInfo(AppIcons.Phone, visit.phone, c)
                    if (visit.address.isNotBlank()) rowInfo(AppIcons.Place, visit.address, c)
                }
            }

            if (visit.notes.isNotBlank() || visit.notesFor(en).isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                SectionLabel(t["notes_hint"])
                Spacer(Modifier.height(4.dp))
                MarkdownText(visit.notesFor(en), c.ink2)
            }

            if (visit.checklist.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SectionLabel(t["checklist"])
                Spacer(Modifier.height(4.dp))
                visit.checklist.forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp))
                            .background(if (item.done) c.ink else c.sunk)) {
                            if (item.done) Icon(AppIcons.Check, null, tint = c.onInk, modifier = Modifier.size(12.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(item.text, color = c.ink2, fontSize = 13.sp,
                            textDecoration = if (item.done) TextDecoration.LineThrough else null)
                    }
                }
            }
            if (visit.next.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.sunk).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.ArrowForward, null, tint = c.ink2, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t["next_step"], color = c.muted, fontSize = 11.sp)
                        Text(visit.next, color = c.ink2, fontSize = 13.sp)
                    }
                    if (visit.nextDate.isNotBlank()) Text(cardDay(visit.nextDate), color = c.muted, fontSize = 12.sp)
                }
            }

            if (polishing) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(t["polishing"], color = c.muted, fontSize = 12.sp)
                }
            }
            if (notice != null) {
                Spacer(Modifier.height(10.dp))
                Text(notice!!, color = c.ok, fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    if (store.apiKey.isBlank()) notice = t["need_key"]
                    else {
                        polishing = true; notice = null
                        scope.launch {
                            val r = runCatching { AiFormatter.format(store.apiKey, store.aiModel, visit.notes) }
                            polishing = false
                            r.onSuccess { f ->
                                store.upsert(visit.copy(notesAr = f.ar, notesEn = f.en))
                                notice = t["ai_result"]
                            }.onFailure { notice = t["ai_failed"] }
                        }
                    }
                }) {
                    if (polishing) Text(t["polishing"]) else Text(t["polish"])
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(visit.notesFor(en)))
                    notice = t["copied"]
                }) { Text(t["copy"]) }
                TextButton(onClick = { editing = true }) { Text(editLabel(en)) }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { deleting = true }) { Text(t["delete_visit"]) }
            }
        }
    }
}

@Composable
private fun rowInfo(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, c: SalesColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.muted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = c.ink2, fontSize = 13.sp)
    }
}