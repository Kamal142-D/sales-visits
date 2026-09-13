package com.sales.visits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.awt.FileDialog
import java.io.File
import java.net.URI
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ------------------------------- visits -------------------------------

@Composable
fun VisitsScreen(store: Store) {
    val t = LocalL.current
    val c = LocalSales.current
    val en = I18n.en
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<String?>(null) }

    val q = search.trim().lowercase()
    val filtered = store.visits.filter { v ->
        q.isBlank() ||
            v.client.lowercase().contains(q) ||
            v.contact.lowercase().contains(q) ||
            v.phone.lowercase().contains(q) ||
            v.notes.lowercase().contains(q) ||
            v.notesFor(en).lowercase().contains(q)
    }.sortedByDescending { it.date + it.time }

    Column(Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        ScreenHeader(
            t["visits"],
            subtitle = visitsSubtitle(store.visits.size, store.countThisWeek(), en),
            trailing = {
                IconButton(onClick = { showAdd = true }) {
                    Icon(AppIcons.Add, null, tint = c.ink, modifier = Modifier.size(26.dp))
                }
            },
        )
        OutlinedTextField(
            search, { search = it },
            label = { Text(t["search_visits"]) },
            singleLine = true,
            leadingIcon = { Icon(AppIcons.Search, null, tint = c.muted) },
            modifier = Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 14.dp),
        )

        if (store.visits.isEmpty()) {
            EmptyState(t["empty_visits_title"] + "\n" + t["empty_visits_desc"], AppIcons.Inbox)
        } else if (filtered.isEmpty()) {
            EmptyState(t["no_results"], AppIcons.Search)
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 14.dp)) {
                items(filtered, key = { it.id }) { v ->
                    VisitCard(v, onClick = { detailId = v.id })
                }
            }
        }
    }

    if (showAdd) VisitEditorDialog(store, null) { showAdd = false }
    if (detailId != null) VisitDetailDialog(store, detailId!!) { detailId = null }
}

@Composable
private fun VisitCard(v: Visit, onClick: () -> Unit) {
    val c = LocalSales.current
    val en = I18n.en
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    v.client.ifBlank { "—" },
                    color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(cardDayShort(v.date), color = c.muted, fontSize = 12.sp)
                if (v.time.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(fmtTime(v.time), color = c.muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(v.typeEnum().label(en), color = c.ink2, bg = c.sunk)
                Spacer(Modifier.width(6.dp))
                Chip(v.outcomeEnum().label(en), color = c.onInk, bg = v.outcomeEnum().color(c))
                if (v.next.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(v.next, color = c.faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            if (v.notesFor(en).isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                MarkdownText(v.notesFor(en), c.muted, baseSize = 12.sp, maxLines = 2)
            }
        }
        Spacer(Modifier.width(10.dp))
        Icon(AppIcons.Plane, null, tint = c.faint, modifier = Modifier.size(18.dp))
    }
}

// ------------------------------- today plan -------------------------------

@Composable
fun TodayScreen(store: Store) {
    val t = LocalL.current
    val c = LocalSales.current
    var selectedDate by remember { mutableStateOf(todayIso()) }
    var addText by remember { mutableStateOf("") }
    val items = store.planFor(selectedDate).sortedBy { it.id }
    val done = items.count { it.done }
    val frac = if (items.isEmpty()) 0f else done.toFloat() / items.size
    val isToday = selectedDate == todayIso()

    Column(Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        ScreenHeader(
            if (isToday) t["today_title"] else t["day_progress"],
            subtitle = t["today_sub"],
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedDate = LocalDate.parse(selectedDate).minusDays(1).toString() }) {
                        Icon(AppIcons.ArrowBack, null, tint = c.ink2, modifier = Modifier.size(20.dp))
                    }
                    Text(cardDay(selectedDate), color = c.ink2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { selectedDate = LocalDate.parse(selectedDate).plusDays(1).toString() }) {
                        Icon(AppIcons.ArrowForward, null, tint = c.ink2, modifier = Modifier.size(20.dp))
                    }
                }
            },
        )

        Row(Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.sunk)) {
                if (frac > 0f)
                    Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(5.dp)).background(c.ink))
            }
            Spacer(Modifier.width(12.dp))
            Text("$done / ${items.size}", color = c.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 12.dp)) {
            OutlinedTextField(
                addText, { addText = it },
                label = { Text(t["add_client_hint"]) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        if (addText.isNotBlank()) { store.addPlan(addText, selectedDate); addText = "" }
                    }) { Icon(AppIcons.Add, null, tint = c.ink2) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (items.isEmpty()) {
            EmptyState(t["empty_today_title"] + "\n" + t["empty_today_desc"], AppIcons.Plan)
        } else {
            Column(Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 10.dp)) {
                items.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                            .background(c.surface).clickable { store.togglePlan(item.id) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = item.done, onCheckedChange = { store.togglePlan(item.id) })
                        Text(
                            item.client, color = if (item.done) c.faint else c.ink2, fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (item.done) TextDecoration.LineThrough else null,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { store.deletePlan(item.id) }) {
                            Icon(AppIcons.Delete, null, tint = c.muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}