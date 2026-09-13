package com.sales.visits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.launch

// ------------------------------- shared dialog shell -------------------------------

@Composable
fun AppDialog(widthDp: Int = 430, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalSales.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.width(widthDp.dp).clip(RoundedCornerShape(20.dp)),
            color = c.surface,
        ) {
            content()
        }
    }
}

// ------------------------------- confirm -------------------------------

@Composable
fun ConfirmDialog(
    title: String,
    desc: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalL.current
    val c = LocalSales.current
    AppDialog(widthDp = 360, onDismiss = onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(Modifier.height(8.dp))
            Text(desc, fontSize = 13.sp, color = c.muted)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(t["cancel"], color = c.muted) }
                Spacer(Modifier.width(6.dp))
                Button(onClick = onConfirm) { Text(confirmText) }
            }
        }
    }
}

// ------------------------------- segmented choices -------------------------------

@Composable
fun SelectChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val c = LocalSales.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            val active = value == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) c.ink else c.sunk)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(label, color = if (active) c.onInk else c.ink2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ------------------------------- date field + mini calendar -------------------------------

@Composable
fun DateField(value: String, onValueChange: (String) -> Unit, label: String? = null) {
    val c = LocalSales.current
    val t = LocalL.current
    var showPicker by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value, onValueChange,
                label = { label?.let { Text(t[it]) } },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showPicker = !showPicker }) {
                Icon(AppIcons.Calendar, null, tint = c.ink2)
            }
        }
        if (showPicker) {
            Spacer(Modifier.height(8.dp))
            MiniCalendar(selected = value, onSelect = { onValueChange(it); showPicker = false })
        }
    }
}

@Composable
fun MiniCalendar(selected: String, onSelect: (String) -> Unit) {
    val c = LocalSales.current
    val en = I18n.en
    val dayShort =
        if (en) listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
        else listOf("أحد", "إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")
    var anchor by remember { mutableStateOf(parseIso(selected) ?: LocalDate.now()) }
    val month = anchor.monthValue
    val year = anchor.year
    val first = LocalDate.of(year, month, 1)
    val offset = first.dayOfWeek.value % 7
    val daysInMonth = first.lengthOfMonth()

    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(c.sunk).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { anchor = anchor.minusMonths(1) }) {
                Icon(AppIcons.ArrowBack, null, tint = c.ink2, modifier = Modifier.size(18.dp))
            }
            Text(
                "${monthName(anchor)} $year",
                color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { anchor = anchor.plusMonths(1) }) {
                Icon(AppIcons.ArrowForward, null, tint = c.ink2, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            dayShort.forEach { d ->
                Text(
                    d, color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth()) {
            var day = 1
            repeat(6) { week ->
                if (day > daysInMonth) return@repeat
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val cellDay =
                            if (week == 0 && col < offset) null
                            else if (day <= daysInMonth) day++
                            else null
                        val iso = if (cellDay != null) LocalDate.of(year, month, cellDay).toString() else ""
                        val active = cellDay != null && iso == selected
                        Box(
                            Modifier.weight(1f).padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (cellDay != null) {
                                Box(
                                    Modifier.size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (active) c.ink else Color.Transparent)
                                        .clickable { onSelect(iso) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        cellDay.toString(),
                                        color = if (active) c.onInk else c.ink2,
                                        fontSize = 12.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}