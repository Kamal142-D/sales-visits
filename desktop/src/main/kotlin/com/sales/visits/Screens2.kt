package com.sales.visits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.awt.FileDialog
import java.io.File
import java.net.URI
import java.time.LocalDate
import kotlinx.coroutines.launch

// ------------------------------- customers -------------------------------

@Composable
fun CustomersScreen(store: Store) {
    val t = LocalL.current
    val c = LocalSales.current
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<String?>(null) }

    val q = search.trim().lowercase()
    val filtered = store.customers.filter { cus ->
        q.isBlank() ||
            cus.name.lowercase().contains(q) ||
            cus.phone.lowercase().contains(q) ||
            cus.contact.lowercase().contains(q)
    }

    Column(Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        ScreenHeader(
            t["customers"],
            subtitle = t["profile_sub"],
            trailing = {
                IconButton(onClick = { showAdd = true }) {
                    Icon(AppIcons.PersonAdd, null, tint = c.ink, modifier = Modifier.size(26.dp))
                }
            },
        )
        OutlinedTextField(
            search, { search = it },
            label = { Text(t["search_customers"]) },
            singleLine = true,
            leadingIcon = { Icon(AppIcons.Search, null, tint = c.muted) },
            modifier = Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 14.dp),
        )

        if (store.customers.isEmpty()) {
            EmptyState(t["empty_customers"] + "\n" + t["empty_customers_desc"], AppIcons.People)
        } else if (filtered.isEmpty()) {
            EmptyState(t["no_results"], AppIcons.Search)
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 14.dp)) {
                items(filtered, key = { it.id }) { cus ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.surface)
                            .clickable { detailId = cus.id }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(c.sunk),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(AppIcons.Person, null, tint = c.ink2, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cus.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Row {
                                if (cus.industry.isNotBlank()) {
                                    Text(cus.industry, color = c.muted, fontSize = 12.sp)
                                    Spacer(Modifier.width(10.dp))
                                }
                                if (cus.phone.isNotBlank()) {
                                    Text(cus.phone, color = c.muted, fontSize = 12.sp)
                                }
                            }
                        }
                        Text("${store.visitsFor(cus).size} ${t["visit_count"]}", color = c.faint, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showAdd) CustomerEditorDialog(store, null) { showAdd = false }
    if (detailId != null) CustomerDetailDialog(store, detailId!!) { detailId = null }
}

// ------------------------------- report -------------------------------

@Composable
fun ReportScreen(store: Store) {
    val t = LocalL.current
    val c = LocalSales.current
    var week by remember { mutableStateOf(0) }
    val clipboard = LocalClipboardManager.current
    var notice by remember { mutableStateOf<String?>(null) }

    val vs = weekVisits(store.visits, week)
    val clients = vs.map { it.client }.filter { it.isNotBlank() }.toSet()
    val succ = vs.count { it.outcomeEnum() == Outcome.SUCCESS }

    Column(Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        ScreenHeader(
            t["report_title"],
            subtitle = weekLabel(week),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { week = 1 }) { Text(t["last_week"], color = if (week == 1) c.ink else c.muted) }
                    Spacer(Modifier.width(2.dp))
                    TextButton(onClick = { week = 0 }) { Text(t["this_week"], color = if (week == 0) c.ink else c.muted) }
                }
            },
        )

        Row(Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("${vs.size}", t["stat_visits"], c)
            StatBox("${clients.size}", t["stat_clients"], c)
            StatBox("$succ", t["stat_success"], c)
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
            Text(t["by_type"], fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(Modifier.height(6.dp))
            VisitType.entries.forEach { type ->
                val n = vs.count { it.typeEnum() == type }
                if (n > 0) StatBar(type.label(I18n.en), n, vs.size, c.ink)
            }
            if (vs.isEmpty()) Text(t["empty_week"], color = c.muted, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 26.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t["week_details"], fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(reportText(store.visits, week)))
                    notice = t["copied"]
                }) { Text(t["copy"]) }
                TextButton(onClick = {
                    val path = saveTextFile("visitflow-report.txt", reportText(store.visits, week))
                    if (path != null) notice = t["backup_saved"]
                }) { Text(t["share_report"]) }
            }
            Box(Modifier.fillMaxWidth().heightIn(max = 170.dp).verticalScroll(rememberScrollState())) {
                Text(reportText(store.visits, week), color = c.ink2, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(end = 14.dp))
            }
            if (notice != null) {
                Spacer(Modifier.height(6.dp))
                Text(notice!!, color = c.ok, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RowScope.StatBox(value: String, label: String, c: SalesColors) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.surface).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = c.muted)
    }
}

@Composable
private fun StatBar(label: String, value: Int, total: Int, color: Color) {
    val c = LocalSales.current
    val frac = if (total == 0) 0f else value.toFloat() / total
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.ink2, fontSize = 12.sp, modifier = Modifier.width(130.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(6.dp)).background(c.sunk)) {
            if (frac > 0f)
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(6.dp)).background(color))
        }
        Spacer(Modifier.width(10.dp))
        Text("$value", color = c.ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
    }
}

// ------------------------------- insights -------------------------------

@Composable
fun InsightsScreen(store: Store) {
    val t = LocalL.current
    val c = LocalSales.current
    val en = I18n.en
    val all = store.visits
    val total = all.size
    val due = store.dueFollowUps()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader(t["insights_tab"], subtitle = t["insights_sub"])

        Row(Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("$total", t["total"], c)
            StatBox("${all.map { it.client }.toSet().size}", t["stat_clients"], c)
            StatBox("${all.count { it.outcomeEnum() == Outcome.SUCCESS }}", t["stat_success"], c)
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
            Text(t["by_outcome"], fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Outcome.entries.forEach { o ->
                val n = all.count { it.outcomeEnum() == o }
                if (n > 0) StatBar(o.label(en), n, total, o.color(c))
            }
            if (total == 0) Text(t["empty_insights"], color = c.muted, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
            Text(t["visits_by_month"], fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(Modifier.height(10.dp))
            val months = (5 downTo 0).map { n -> LocalDate.now().minusMonths(n.toLong()).withDayOfMonth(1) }
            val counts = months.map { m -> all.count { it.date.startsWith(m.toString().take(7)) } }
            val maxC = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.Bottom) {
                counts.forEachIndexed { i, n ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.width(20.dp)
                                .height(Dp(24f + 66f * n / maxC))
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(if (n > 0) c.ink else c.sunk),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(monthName(months[i]).take(4), color = c.muted, fontSize = 9.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
            Text(t["top_clients"], fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(Modifier.height(6.dp))
            val top = all.groupBy { it.client }.mapValues { it.value.size }
                .toList().sortedByDescending { it.second }.take(5)
            if (top.isEmpty()) Text(t["no_visits"], color = c.muted, fontSize = 13.sp)
            else {
                val topMax = top.first().second
                top.forEach { (client, n) -> StatBar(client, n, topMax, c.ink) }
            }
        }

        if (due.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
                Text(t["upcoming"], fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Spacer(Modifier.height(6.dp))
                due.take(10).forEach { v ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(v.client, color = c.ink2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(v.next, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.6f))
                        Text(cardDay(v.nextDate), color = if (v.nextDate < todayIso()) c.lost else c.muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ------------------------------- settings -------------------------------

@Composable
fun SettingsScreen(store: Store, cloud: CloudSync) {
    val t = LocalL.current
    val c = LocalSales.current
    var testing by remember { mutableStateOf(false) }
    var aiMsg by remember { mutableStateOf<String?>(null) }
    var backupMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader(t["settings"])

        Spacer(Modifier.height(16.dp))
        SettingsCard(t["cloud_sync"]) {
            if (cloud.signedIn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cloud.email ?: "", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(t["sync_${cloud.syncState}"], color = c.muted, fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { cloud.signOut() }) { Text(t["sign_out"]) }
                }
            } else {
                var creating by remember { mutableStateOf(false) }
                var name by remember { mutableStateOf("") }
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                Text(t["account_desc"], color = c.muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                if (creating) {
                    OutlinedTextField(name, { name = it }, label = { Text(t["full_name"]) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(email, { email = it }, label = { Text(t["email"]) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(password, { password = it }, label = { Text(t["password"]) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                val msg = cloudMessage(cloud.errorCode, t)
                if (msg.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(msg, color = c.lost, fontSize = 12.sp) }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = !cloud.busy, onClick = {
                        if (creating) cloud.createAccount(name, email, password) else cloud.signIn(email, password)
                    }) { Text(if (cloud.busy) "…" else if (creating) t["create_account"] else t["sign_in"]) }
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = { creating = !creating }) { Text(if (creating) t["have_account"] else t["need_account"]) }
                }
            }
        }

        SettingsCard(t["appearance"]) {
            SectionLabel(t["theme_auto"])
            Spacer(Modifier.height(6.dp))
            SelectChips(
                listOf("auto" to t["theme_auto"], "light" to t["theme_light"], "dark" to t["theme_dark"]),
                store.theme, { store.chooseTheme(it) },
            )
            Spacer(Modifier.height(14.dp))
            SectionLabel(t["color_style"])
            Spacer(Modifier.height(6.dp))
            SelectChips(listOf("classic" to t["palette_classic"], "warm" to t["palette_warm"]), store.palette) { store.choosePalette(it) }
            Spacer(Modifier.height(14.dp))
            SectionLabel(t["language"])
            Spacer(Modifier.height(6.dp))
            SelectChips(
                listOf("auto" to t["lang_auto"], "ar" to t["lang_ar"], "en" to t["lang_en"]),
                store.langMode, { store.chooseLang(it) },
            )
            Spacer(Modifier.height(14.dp))
            SectionLabel(t["week_start"])
            Spacer(Modifier.height(6.dp))
            SelectChips(
                listOf("SATURDAY" to t["day_sat"], "SUNDAY" to t["day_sun"], "MONDAY" to t["day_mon"]),
                store.weekStartDay, { store.chooseWeekStart(it) },
            )
        }

        SettingsCard(t["ai_formatting"]) {
            Text(t["ai_desc"], color = c.muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                store.apiKey, { store.chooseApiKey(it) },
                label = { Text(t["api_key_hint"]) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                store.aiModel, { store.chooseAiModel(it) },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    if (store.apiKey.isBlank()) { aiMsg = t["need_key"]; return@Button }
                    testing = true; aiMsg = null
                    scope.launch {
                        val ok = runCatching { AiFormatter.format(store.apiKey, store.aiModel, "زيارة ناجحة") }.isSuccess
                        testing = false
                        aiMsg = if (ok) t["ai_test_ok"] else t["ai_failed"]
                    }
                }) {
                    if (testing) Text(t["ai_testing"]) else Text(t["ai_test"])
                }
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = {
                    runCatching { Desktop.getDesktop().browse(URI("https://aistudio.google.com/apikey")) }
                }) { Text(t["get_key"]) }
            }
            if (aiMsg != null) {
                Spacer(Modifier.height(8.dp))
                Text(aiMsg!!, color = c.ok, fontSize = 12.sp)
            }
        }

        SettingsCard(t["backup"]) {
            Text(t["backup_desc"], color = c.muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row {
                OutlinedButton(onClick = {
                    val path = saveTextFile("visitflow-backup.json", store.exportBackup())
                    if (path != null) backupMsg = t["backup_saved"] else backupMsg = null
                }) { Text(t["export_backup"]) }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = {
                    val file = loadFile() ?: return@OutlinedButton
                    val raw = runCatching { File(file).readText() }.getOrNull() ?: ""
                    val ok = store.restoreBackup(raw)
                    backupMsg = if (ok) t["backup_restored"] else t["backup_failed"]
                }) { Text(t["import_backup"]) }
            }
            if (backupMsg != null) {
                Spacer(Modifier.height(8.dp))
                Text(backupMsg!!, color = c.ok, fontSize = 12.sp)
            }
        }

        SettingsCard(t["about"]) {
            Text("VisitFlow · 1.7", color = c.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Windows desktop edition", color = c.muted, fontSize = 12.sp)
        }
    }
}

private fun cloudMessage(code: String?, t: L): String = when (code) {
    null, "" -> ""
    "bad_login" -> t["err_bad_login"]
    "email_exists" -> t["err_email_exists"]
    "weak_password" -> t["err_weak_password"]
    "provider_disabled" -> t["err_provider_disabled"]
    "bad_key" -> t["err_bad_key"]
    "sync_error" -> t["team_error"]
    else -> code   // the raw Firebase message, for diagnosis
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalSales.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp)
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .padding(18.dp),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

// ------------------------------- file helpers -------------------------------

private fun saveTextFile(defaultName: String, text: String): String? {
    val d = FileDialog(null as java.awt.Frame?, "Save", FileDialog.SAVE)
    d.file = defaultName
    d.isVisible = true
    val name = d.file ?: return null
    return runCatching {
        val f = File(d.directory, name)
        f.writeText(text)
        f.absolutePath
    }.getOrNull()
}

private fun loadFile(): String? {
    val d = FileDialog(null as java.awt.Frame?, "Open", FileDialog.LOAD)
    d.isVisible = true
    val name = d.file ?: return null
    return File(d.directory, name).absolutePath
}
// ------------------------------- opportunities & tasks (plan 6.5) -------------------------------

private fun oppStageLabelDesktop(stage: String, en: Boolean): String = when (stage.trim().uppercase()) {
    "NEW" -> if (en) "New" else "جديدة"
    "QUALIFYING", "FOLLOW" -> if (en) "Qualifying" else "تأهيل"
    "NEED" -> if (en) "Needs" else "فهم الاحتياج"
    "SOLUTION" -> if (en) "Solution / Demo" else "عرض الحل / تجربة"
    "QUOTE" -> if (en) "Quote" else "عرض سعر"
    "NEGOTIATION" -> if (en) "Negotiation" else "تفاوض / اعتماد"
    "WON" -> if (en) "Won" else "تم البيع"
    "LOST" -> if (en) "Lost" else "لم تتم"
    "POSTPONED" -> if (en) "Postponed" else "مؤجلة"
    else -> if (en) "New" else "جديدة"
}

private val OPP_STAGE_ORDER = listOf("NEW", "QUALIFYING", "NEED", "SOLUTION", "QUOTE", "NEGOTIATION", "WON", "LOST", "POSTPONED")

private fun moneyStr(v: Double): String =
    if (v == v.toLong().toDouble()) "%,d".format(v.toLong()) else "%,.2f".format(v)

@Composable
fun OpportunitiesScreen(store: Store) {
    val t = LocalL.current
    val c = LocalSales.current
    val en = I18n.en
    val opps = store.opportunities
    Column(Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        ScreenHeader(t["opportunities"])
        if (opps.isEmpty()) {
            EmptyState(t["empty_opps_title"], AppIcons.Chart)
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 14.dp)) {
                OPP_STAGE_ORDER.forEach { stageKey ->
                    val group = opps.filter { it.stage.trim().uppercase().let { s -> if (s == "FOLLOW") "QUALIFYING" else s } == stageKey }
                    if (group.isNotEmpty()) {
                        item(key = "h_$stageKey") {
                            Text(
                                "${oppStageLabelDesktop(stageKey, en)} · ${group.size}",
                                color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                            )
                        }
                        items(group, key = { it.id }) { o ->
                            Surface(
                                color = c.surface, shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(o.title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        val sub = listOf(o.customerName, o.nextStep).filter { it.isNotBlank() }.joinToString(" · ")
                                        if (sub.isNotBlank()) Text(sub, color = c.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (o.value > 0) Text(
                                        "${moneyStr(o.value)}${if (o.currency.isNotBlank()) " " + o.currency else ""}",
                                        color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold,
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

@Composable
fun TasksScreen(store: Store) {
    val t = LocalL.current
    val c = LocalSales.current
    val shown = store.tasks
        .filter { it.status.trim().uppercase() != "CANCELLED" }
        .sortedWith(compareBy({ it.done }, { it.date }))
    Column(Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        ScreenHeader(t["tasks_tab"])
        AssistantBar(store)
        if (shown.isEmpty()) {
            EmptyState(t["empty_tasks_title"], AppIcons.Check)
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 14.dp)) {
                items(shown, key = { it.id }) { item ->
                    Surface(
                        color = if (item.done) c.sunk else c.surface, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(24.dp).clip(RoundedCornerShape(7.dp))
                                    .background(if (item.done) c.ink else c.sunk)
                                    .clickable { store.toggleTask(item.id) },
                                contentAlignment = Alignment.Center,
                            ) { if (item.done) Icon(AppIcons.Check, null, tint = c.onInk, modifier = Modifier.size(14.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                val title = if (item.client.isNotBlank()) "@${item.client}${if (item.action.isNotBlank()) ": ${item.action}" else ""}" else item.action
                                Text(title, color = if (item.done) c.muted else c.ink, fontSize = 14.sp)
                                if (item.date.isNotBlank()) Text(fullDay(item.date), color = c.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------- quotes & weekly review (plan 6.5) -------------------------------

private val QUOTE_STATUS_ORDER = listOf("DRAFT", "SENT", "ACCEPTED", "REJECTED", "EXPIRED", "CANCELLED")

@Composable
fun QuotesScreen(store: Store) {
    val t = LocalL.current
    val c = LocalSales.current
    val en = I18n.en
    val quotes = store.quotes
    Column(Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        ScreenHeader(t["quotes"])
        if (quotes.isEmpty()) {
            EmptyState(t["no_quotes"], AppIcons.Copy)
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 14.dp)) {
                QUOTE_STATUS_ORDER.forEach { st ->
                    val group = quotes.filter { it.status.trim().uppercase().ifBlank { "DRAFT" } == st }
                    if (group.isNotEmpty()) {
                        item(key = "qh_$st") {
                            Text(quoteStatusEnum(st).label(en) + " · " + group.size, color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                        }
                        items(group, key = { it.id }) { q ->
                            Surface(color = c.surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(q.number.ifBlank { t["quote"] } + (if (q.version > 1) " · v${q.version}" else ""), color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                        if (q.customerName.isNotBlank()) Text(q.customerName, color = c.muted, fontSize = 12.5.sp)
                                    }
                                    Text("${moneyStr(QuoteMath.total(q))}${if (q.currency.isNotBlank()) " " + q.currency else ""}", color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyReviewScreen(store: Store) {
    val t = LocalL.current
    val c = LocalSales.current
    val today = LocalDate.now()
    val range = Analytics.DateRange(today.minusDays(6).toString(), today.toString())
    val prev = Analytics.DateRange(today.minusDays(13).toString(), today.minusDays(7).toString())
    fun metrics(r: Analytics.DateRange) = Analytics.compute(r, store.customers, store.visits, store.activities, store.quotes, store.opportunities, store.orders, store.tasks, "SAR")
    val m = metrics(range); val pm = metrics(prev)
    val overdue = DataQueries.overdueTasks(store.tasks, today.toString()).size
    val quotesNoFu = DataQueries.sentQuotesNoFollowup(store.quotes, store.activities).size
    val oppsNoDM = DataQueries.oppsNoDecisionMaker(store.opportunities, store.customers).size
    val oppsNoNext = DataQueries.oppsNoNextStep(store.opportunities).size
    val focus = WeeklyReview.focus(overdue, quotesNoFu, oppsNoDM, oppsNoNext)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
        ScreenHeader(t["weekly_review"], subtitle = "${fullDay(range.start)} — ${fullDay(range.end)}")
        Column(Modifier.padding(horizontal = 26.dp, vertical = 12.dp)) {
            Surface(color = c.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(t["wr_happened"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    WrMetric(t["m_won"], m.won, pm.won); WrMetric(t["m_lost"], m.lost, pm.lost)
                    WrMetric(t["m_interactions"], m.completedInteractions, pm.completedInteractions)
                    WrMetric(t["m_quotes_sent"], m.quotesSent, pm.quotesSent)
                    WrMetric(t["m_new_prospects"], m.newProspects, pm.newProspects)
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = c.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(t["wr_attention"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    val flagged = listOf(t["q_overdue"] to overdue, t["q_quotes_nofu"] to quotesNoFu, t["q_opps_nodm"] to oppsNoDM, t["q_opps_nonext"] to oppsNoNext).filter { it.second > 0 }
                    if (flagged.isEmpty()) Text(t["ask_on_track"], color = c.muted, fontSize = 13.5.sp)
                    else flagged.forEach { (label, n) -> Text("•  $label — $n", color = c.ink2, fontSize = 13.5.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = c.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(t["wr_focus"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(t[focus.lowercase()], color = c.ink2, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun WrMetric(label: String, value: Int, prev: Int) {
    val c = LocalSales.current
    val diff = value - prev
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.ink2, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Text(value.toString(), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(8.dp))
        val (txt, col) = when { diff > 0 -> "▲$diff" to c.ok; diff < 0 -> "▼${-diff}" to c.lost; else -> "=" to c.muted }
        Text(txt, color = col, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------- desktop AI assistant (plan 6.5 / 4.4) -------------------------------

@Composable
private fun AssistantBar(store: Store) {
    val t = LocalL.current
    val c = LocalSales.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var intent by remember { mutableStateOf<CommandIntent?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    fun run() {
        if (input.isBlank()) return
        if (store.apiKey.isBlank()) { result = t["need_key"]; return }
        loading = true; result = null; intent = null
        scope.launch {
            try { intent = AiFormatter.parseCommand(store.apiKey, store.aiModel, input.trim(), todayIso()) }
            catch (e: Exception) { result = "${t["ai_failed"]}: ${e.message}" } finally { loading = false }
        }
    }
    fun resolveCustomer(name: String): Customer? {
        if (name.isBlank()) return null
        store.customerFor(name)?.let { return it }
        return store.customers.filter { it.name.contains(name, true) }.singleOrNull()
    }

    Column(Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it },
                label = { Text(t["assistant_placeholder"]) },
                singleLine = true, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { run() }, enabled = !loading) { Text(t["cmd_confirm"]) }
        }
        if (loading) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = c.ink, strokeWidth = 2.dp)
        }
        result?.let { Text(it, color = c.muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp)) }

        val i = intent
        if (i != null && result == null) {
            Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Column(Modifier.padding(12.dp)) {
                    when (i.action) {
                        CommandAction.ADD_TASK -> {
                            Text("${t["cmd_add_task"]}: ${i.text.ifBlank { "—" }}", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            val meta = listOf(i.customer.takeIf { it.isNotBlank() }?.let { "@$it" }, i.date.takeIf { it.isNotBlank() }?.let { fullDay(it) }).filterNotNull().joinToString(" · ")
                            if (meta.isNotBlank()) Text(meta, color = c.muted, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                val date = i.date.ifBlank { todayIso() }
                                result = if (taskExists(store.tasks, i.customer, i.text, date)) t["cmd_dup"]
                                else { store.addTask(i.text, i.customer, date, i.time); t["cmd_added"] }
                                intent = null; input = ""
                            }, enabled = i.text.isNotBlank()) { Text(t["cmd_confirm"]) }
                        }
                        CommandAction.RESCHEDULE_TASK -> {
                            val qy = i.query.ifBlank { i.text }
                            val match = store.tasks.firstOrNull { it.statusEnum() != TaskStatus.DONE && it.statusEnum() != TaskStatus.CANCELLED && (it.action.contains(qy, true) || it.client.contains(qy, true)) }
                            if (match == null) Text(t["cmd_task_not_found"], color = c.muted, fontSize = 13.sp)
                            else {
                                Text("${match.action.ifBlank { match.client }} → ${if (i.date.isNotBlank()) fullDay(i.date) else "—"}", color = c.ink, fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { store.rescheduleTask(match.id, i.date); result = t["cmd_moved"]; intent = null; input = "" }, enabled = i.date.isNotBlank()) { Text(t["cmd_confirm"]) }
                            }
                        }
                        CommandAction.SEARCH -> {
                            val qy = i.query.ifBlank { i.customer }.ifBlank { i.text }
                            val custs = store.customers.filter { it.name.contains(qy, true) }.take(6)
                            val opps = store.opportunities.filter { it.title.contains(qy, true) || it.customerName.contains(qy, true) }.take(6)
                            if (custs.isEmpty() && opps.isEmpty()) Text(t["no_results"], color = c.muted, fontSize = 13.sp)
                            else {
                                custs.forEach { Text("👤 ${it.name}", color = c.ink2, fontSize = 13.sp) }
                                opps.forEach { Text("📈 ${it.title} · ${it.customerName}", color = c.ink2, fontSize = 13.sp) }
                            }
                        }
                        CommandAction.UNKNOWN -> Text(t["cmd_unknown"], color = c.muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
