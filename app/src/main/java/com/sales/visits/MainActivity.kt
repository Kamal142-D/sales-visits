@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.sales.visits

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.firebase.FirebaseApp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        check(mapsRouteUri(listOf("First stop", "Last stop"))?.getQueryParameter("waypoints") == "First stop")
        checkCloudMerge()
        val store = Store(applicationContext)
        setContent { App(store) }
    }
}

private fun mapsRouteUri(stops: List<String>): Uri? {
    // ponytail: Maps URLs support nine waypoints; add a Routes API only when larger daily routes are real.
    val route = stops.filter { it.isNotBlank() }.take(10)
    val destination = route.lastOrNull() ?: return null
    return Uri.parse("https://www.google.com/maps/dir/?api=1").buildUpon()
        .appendQueryParameter("destination", destination)
        .appendQueryParameter("travelmode", "driving")
        .apply {
            if (route.size > 1) appendQueryParameter("waypoints", route.dropLast(1).joinToString("|"))
        }
        .build()
}

private fun uid(): String =
    System.currentTimeMillis().toString(36) + (1000..9999).random().toString(36)

private fun nowHm(): String {
    val t = LocalTime.now(); return "%02d:%02d".format(t.hour, t.minute)
}

@Composable
fun App(store: Store) {
    val dark = when (store.theme) {
        "light" -> false; "dark" -> true; else -> isSystemInDarkTheme()
    }
    val en = store.lang == "en"
    val view = LocalView.current
    SideEffect {
        val bars = WindowCompat.getInsetsController((view.context as Activity).window, view)
        bars.isAppearanceLightStatusBars = !dark
        bars.isAppearanceLightNavigationBars = !dark
    }
    SalesTheme(dark, en, store.palette) {
        val c = LocalSales.current
        CompositionLocalProvider(
            LocalLayoutDirection provides if (en) LayoutDirection.Ltr else LayoutDirection.Rtl,
            LocalL provides if (en) EN else AR,
        ) {
            val ctx = LocalContext.current
            val cloud = remember { FirebaseApp.initializeApp(ctx)?.let { CloudAccount(ctx, store) } }
            DisposableEffect(cloud) { onDispose { cloud?.close() } }
            var notificationGranted by remember { mutableStateOf(ReminderScheduler.notificationsEnabled(ctx)) }
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> notificationGranted = granted }
            var tab by remember { mutableStateOf(0) }
            var showSettings by remember { mutableStateOf(false) }
            var showProfileEditor by remember { mutableStateOf(false) }
            var showCustomers by remember { mutableStateOf(false) }
            var customerEditorOpen by remember { mutableStateOf(false) }
            var editingCustomer by remember { mutableStateOf<Customer?>(null) }
            var editorOpen by remember { mutableStateOf(false) }
            var editing by remember { mutableStateOf<Visit?>(null) }

            Box(Modifier.fillMaxSize().background(c.bg)) {
                when {
                    showProfileEditor && cloud != null -> ProfileEditorScreen(cloud) { showProfileEditor = false }
                    editorOpen -> VisitEditor(store, editing) { editorOpen = false }
                    customerEditorOpen -> CustomerEditor(store, editingCustomer) { customerEditorOpen = false }
                    showCustomers -> CustomersScreen(
                        store,
                        onBack = { showCustomers = false },
                        onAdd = { editingCustomer = null; customerEditorOpen = true },
                        onEdit = { editingCustomer = it; customerEditorOpen = true },
                    )
                    showSettings -> SettingsScreen(store) { showSettings = false }
                    tab == 0 -> VisitsScreen(
                        store,
                        onSettings = { showSettings = true },
                        onCustomers = { showCustomers = true },
                        onEdit = { editing = it; editorOpen = true },
                    )
                    tab == 1 -> TodayScreen(
                        store,
                        notificationsEnabled = notificationGranted,
                        onEnableNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onEdit = { editing = it; editorOpen = true },
                    )
                    tab == 2 -> ReportScreen(store)
                    tab == 3 -> InsightsScreen(store)
                    else -> ProfileScreen(
                        store = store,
                        account = cloud,
                        onSettings = { showSettings = true },
                        onEdit = { showProfileEditor = true },
                    )
                }

                if (!showSettings && !showProfileEditor && !editorOpen && !showCustomers && !customerEditorOpen) {
                    if (tab == 0) {
                        Fab(
                            onClick = { editing = null; editorOpen = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(end = 18.dp, bottom = 88.dp),
                        )
                    }
                    NavPill(
                        tab = tab, onTab = { tab = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 12.dp),
                    )
                }
            }
        }
    }
}

/* ---------------- shared bits ---------------- */

@Composable
private fun OutcomeDot(outcome: Outcome, size: Int = 8) {
    val c = LocalSales.current
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(outcome.color(c))
    )
}

@Composable
private fun Header(title: String, subtitle: String?, mark: Boolean, action: (@Composable () -> Unit)? = null) {
    val c = LocalSales.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mark) {
                    Icon(AppIcons.Place, null, tint = c.ink, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
            }
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, fontSize = 13.5.sp, color = c.muted, fontWeight = FontWeight.Medium)
            }
        }
        if (action != null) action()
    }
}

/** A screen with a frosted-glass top header: content scrolls and blurs behind the fixed header. */
@Composable
private fun FrostedScaffold(header: @Composable () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    val c = LocalSales.current
    val haze = remember { HazeState() }
    var headerH by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().haze(haze).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(with(density) { headerH.toDp() }))
            body()
        }
        Box(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .onGloballyPositioned { headerH = it.size.height },
        ) {
            // Progressive blur: several layers of increasing blur radius, each confined
            // to a band, so the blur STRENGTH ramps from strong (top) to zero (bottom).
            BlurBand(haze, 7.dp, 0.55f, 1.00f)
            BlurBand(haze, 15.dp, 0.38f, 0.75f)
            BlurBand(haze, 25.dp, 0.22f, 0.52f)
            BlurBand(haze, 38.dp, 0.10f, 0.33f)
            // subtle tint scrim, also fading out
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to c.bg.copy(alpha = 0.5f),
                        0.55f to c.bg.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                    )
                )
            )
            Column {
                header()
                Spacer(Modifier.height(64.dp)) // long fade zone below the text
            }
        }
    }
}

/** One masked blur layer for the progressive-blur header. */
@Composable
private fun BoxScope.BlurBand(haze: HazeState, blur: Dp, fadeStart: Float, fadeEnd: Float) {
    Box(
        Modifier.matchParentSize()
            .hazeChild(haze, style = HazeStyle(tint = Color.Transparent, blurRadius = blur, noiseFactor = 0f))
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        fadeStart to Color.Black,
                        fadeEnd to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

@Composable
private fun Fab(onClick: () -> Unit, modifier: Modifier) {
    val c = LocalSales.current
    Box(
        modifier
            .size(56.dp).clip(CircleShape).background(c.ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Add, "زيارة جديدة", tint = c.onInk, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun NavPill(tab: Int, onTab: (Int) -> Unit, modifier: Modifier) {
    val c = LocalSales.current
    val t = LocalL.current
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = c.navBg,
        shadowElevation = if (c.dark) 0.dp else 10.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            NavItem(t["visits"], AppIcons.Visits, tab == 0) { onTab(0) }
            NavItem(t["today"], AppIcons.Map, tab == 1) { onTab(1) }
            NavItem(t["report_tab"], AppIcons.Report, tab == 2) { onTab(2) }
            NavItem(t["insights_tab"], AppIcons.Insights, tab == 3) { onTab(3) }
            NavItem(t["profile"], AppIcons.Person, tab == 4) { onTab(4) }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    val c = LocalSales.current
    val fg = if (on) c.navFg else c.navFgDim
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (on) c.navActiveBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, desc: String?) {
    val c = LocalSales.current
    Column(
        Modifier.fillMaxWidth().padding(top = 90.dp, start = 34.dp, end = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = c.faint, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink2)
        if (desc != null) {
            Spacer(Modifier.height(5.dp))
            Text(desc, fontSize = 13.5.sp, color = c.muted)
        }
    }
}

/* ---------------- visits ---------------- */

@Composable
private fun VisitsScreen(store: Store, onSettings: () -> Unit, onCustomers: () -> Unit, onEdit: (Visit) -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val visits = store.visits
    FrostedScaffold(header = {
        Header(
            t["visits"],
            null,
            mark = false,
        ) {
            Row {
                IconButton(onClick = onCustomers) {
                    Icon(AppIcons.People, t["customers"], tint = c.ink)
                }
                IconButton(onClick = onSettings) {
                    Icon(AppIcons.Settings, t["settings"], tint = c.ink)
                }
            }
        }
    }) {
        if (visits.isEmpty()) {
            EmptyState(AppIcons.Plan, t["empty_visits_title"], t["empty_visits_desc"])
        } else {
            val sorted = visits.sortedByDescending { it.date + it.time }
            val left = sorted.filterIndexed { i, _ -> i % 2 == 0 }
            val right = sorted.filterIndexed { i, _ -> i % 2 == 1 }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    left.forEach { VisitCard(it) { onEdit(it) } }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    right.forEach { VisitCard(it) { onEdit(it) } }
                }
            }
        }
        Spacer(Modifier.height(150.dp))
    }
}

@Composable
private fun ColumnScope.NoteBlock(label: String, text: String) {
    val c = LocalSales.current
    Spacer(Modifier.height(10.dp))
    Text(label, fontSize = 11.sp, color = c.faint, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(2.dp))
    Text(text, fontSize = 14.sp, color = c.muted, lineHeight = 21.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun VisitCard(v: Visit, onClick: () -> Unit) {
    val c = LocalSales.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
        shadowElevation = if (c.dark) 0.dp else 2.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            // Easlo-style card: day name, company name, then the notes body. Nothing else.
            Text(cardDayShort(v.date), fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text(v.client, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, lineHeight = 22.sp)
            if (v.notesAr.isNotBlank() || v.notesEn.isNotBlank()) {
                if (v.notesAr.isNotBlank()) NoteBlock("عربي", v.notesAr)
                if (v.notesEn.isNotBlank()) NoteBlock("English", v.notesEn)
            } else if (v.notes.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(v.notes, fontSize = 14.sp, color = c.muted, lineHeight = 21.sp, maxLines = 9, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/* ---------------- customers ---------------- */

@Composable
private fun CustomersScreen(store: Store, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Customer) -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    var query by remember { mutableStateOf("") }
    BackHandler(onBack = onBack)
    val filtered = store.customers.filter { customer ->
        query.isBlank() || listOf(customer.name, customer.contact, customer.phone)
            .any { it.contains(query.trim(), ignoreCase = true) }
    }

    FrostedScaffold(header = {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"], tint = c.ink)
            }
            Text(t["customers"], fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink, modifier = Modifier.weight(1f))
            IconButton(onClick = onAdd) { Icon(AppIcons.PersonAdd, t["add_customer"], tint = c.ink) }
        }
    }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Input(query, { query = it }, t["search_customers"], Modifier.weight(1f))
        }
        if (filtered.isEmpty()) {
            EmptyState(
                if (query.isBlank()) AppIcons.People else AppIcons.Search,
                if (query.isBlank()) t["empty_customers"] else t["no_results"],
                if (query.isBlank()) t["empty_customers_desc"] else null,
            )
        } else {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filtered.forEach { customer ->
                    CustomerRow(customer, store.visitsFor(customer), onClick = { onEdit(customer) })
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun CustomerRow(customer: Customer, visits: List<Visit>, onClick: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val last = visits.firstOrNull()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp), color = c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
                Text(customer.name.trim().take(1).uppercase(), color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(customer.name, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val detail = when {
                    last != null -> "${visits.size} ${t["visit_count"]} · ${cardDay(last.date)}"
                    customer.contact.isNotBlank() -> customer.contact
                    else -> t["no_visits"]
                }
                Text(detail, color = c.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (customer.phone.isNotBlank()) {
                IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + customer.phone))) }) {
                    Icon(AppIcons.Phone, t["call"], tint = c.ink2, modifier = Modifier.size(20.dp))
                }
            }
            if (customer.address.isNotBlank()) {
                IconButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, mapsRouteUri(listOf(customer.address))))
                }) {
                    Icon(AppIcons.Directions, t["navigate"], tint = c.ink2, modifier = Modifier.size(20.dp))
                }
            } else if (customer.phone.isBlank()) {
                Icon(AppIcons.ArrowBack, null, tint = c.faint)
            }
        }
    }
}

private class CustomerForm(customer: Customer?) {
    val id = customer?.id ?: uid()
    var name by mutableStateOf(customer?.name ?: "")
    var contact by mutableStateOf(customer?.contact ?: "")
    var phone by mutableStateOf(customer?.phone ?: "")
    var address by mutableStateOf(customer?.address ?: "")
    var notes by mutableStateOf(customer?.notes ?: "")
    val createdAt = customer?.createdAt?.ifBlank { todayIso() } ?: todayIso()

    fun toCustomer() = Customer(
        id = id, name = name, contact = contact, phone = phone,
        address = address, notes = notes, createdAt = createdAt,
    )
}

@Composable
private fun CustomerEditor(store: Store, editing: Customer?, onClose: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val form = remember(editing?.id ?: "new-customer") { CustomerForm(editing) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun done() {
        if (form.name.isNotBlank()) store.upsertCustomer(form.toCustomer())
        onClose()
    }
    BackHandler { done() }
    val history = editing?.let(store::visitsFor).orEmpty()

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { done() }
            Spacer(Modifier.width(12.dp))
            Text(
                if (editing == null) t["new_customer"] else t["customer_details"],
                color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, modifier = Modifier.weight(1f),
            )
            if (editing != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(AppIcons.Delete, t["delete_customer"], tint = c.lost)
                }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            Field(t["customer_name"], form.name, { form.name = it }, t["client_name_hint"])
            Field(t["contact_person"], form.contact, { form.contact = it }, t["contact_hint"])
            LabeledBlock(t["phone"]) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Input(form.phone, { form.phone = it }, t["phone_hint"], Modifier.weight(1f), KeyboardType.Phone)
                    if (form.phone.isNotBlank()) {
                        Box(
                            Modifier.height(52.dp).clip(RoundedCornerShape(12.dp)).background(c.ink)
                                .clickable { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + form.phone.trim()))) }
                                .padding(horizontal = 16.dp), contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Phone, t["call"], tint = c.onInk, modifier = Modifier.size(19.dp)) }
                    }
                }
            }
            Field(t["address"], form.address, { form.address = it }, t["address_hint"])
            MultiField(t["customer_notes"], form.notes, { form.notes = it }, t["customer_notes_hint"])

            if (editing != null) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(history.size.toString(), t["stat_visits"])
                    Stat(history.count { it.outcomeEnum() == Outcome.SUCCESS }.toString(), t["stat_success"])
                }
                Spacer(Modifier.height(10.dp))
                Text(t["visit_history"], color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (history.isEmpty()) {
                    Text(t["no_visits"], color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 14.dp))
                } else {
                    history.forEach { visit ->
                        Surface(
                            shape = RoundedCornerShape(14.dp), color = c.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutcomeDot(visit.outcomeEnum())
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${visit.typeEnum().label(t.en)} · ${visit.outcomeEnum().label(t.en)}",
                                        color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(fullDay(visit.date), color = c.muted, fontSize = 12.sp)
                                }
                                val note = visit.notesFor(t.en)
                                if (note.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(note, color = c.muted, fontSize = 13.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                if (visit.next.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("${t["next_step"]}: ${visit.next}", color = c.ink2, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(50.dp))
        }
    }

    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = { TextButton(onClick = { store.deleteCustomer(editing.id); confirmDelete = false; onClose() }) { Text(t["delete"], color = c.lost) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t["cancel"], color = c.muted) } },
            title = { Text(t["delete_customer_q"]) },
            text = { Text(t["delete_customer_desc"]) },
            containerColor = c.surface,
        )
    }
}

/* ---------------- editor + customer-info sheet ---------------- */

private class VisitForm(v: Visit?) {
    var client by mutableStateOf(v?.client ?: "")
    var contact by mutableStateOf(v?.contact ?: "")
    var phone by mutableStateOf(v?.phone ?: "")
    var address by mutableStateOf(v?.address ?: "")
    var date by mutableStateOf(v?.date ?: todayIso())
    var time by mutableStateOf(v?.time?.ifBlank { nowHm() } ?: nowHm())
    var type by mutableStateOf(v?.typeEnum() ?: VisitType.FOLLOW)
    var outcome by mutableStateOf(v?.outcomeEnum() ?: Outcome.NONE)
    var notes by mutableStateOf(v?.notes ?: "")
    var notesAr by mutableStateOf(v?.notesAr ?: "")
    var notesEn by mutableStateOf(v?.notesEn ?: "")
    var next by mutableStateOf(v?.next ?: "")
    var nextDate by mutableStateOf(v?.nextDate ?: "")
    fun toVisit(id: String) = Visit(
        id = id, client = client.trim(), contact = contact.trim(), phone = phone.trim(),
        address = address.trim(), date = date, time = time, type = type.name, outcome = outcome.name,
        notes = notes.trim(), notesAr = notesAr.trim(), notesEn = notesEn.trim(),
        next = next.trim(), nextDate = nextDate,
    )
}

@Composable
private fun CircleBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val c = LocalSales.current
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (c.dark) c.surface else Color.White)
            .border(1.dp, c.edge, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = c.ink, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun EditorField(value: String, onChange: (String) -> Unit, hint: String, textSize: TextUnit, weight: FontWeight, minLines: Int) {
    val c = LocalSales.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(color = c.ink, fontSize = textSize, fontWeight = weight, fontFamily = LocalAppFont.current, lineHeight = textSize * 1.45f),
        cursorBrush = SolidColor(c.ink),
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        decorationBox = { inner ->
            if (value.isEmpty()) Text(hint, color = c.faint, fontSize = textSize, fontWeight = weight, lineHeight = textSize * 1.45f)
            inner()
        },
    )
}

@Composable
private fun VisitEditor(store: Store, editing: Visit?, onClose: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val form = remember(editing?.id ?: "new") { VisitForm(editing) }
    var infoOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(ctx) }
    // 0 = idle, 1 = recording, 2 = sending the recording to the AI
    var recState by remember { mutableStateOf(0) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    fun processRecording(file: java.io.File) {
        if (store.apiKey.isBlank()) {
            recState = 0; file.delete()
            Toast.makeText(ctx, t["need_key"], Toast.LENGTH_LONG).show(); return
        }
        recState = 2
        scope.launch {
            try {
                val r = AiFormatter.formatAudio(store.apiKey, store.aiModel, file.readBytes(), "audio/aac")
                form.notesAr = r.ar; form.notesEn = r.en
            } catch (e: Exception) {
                Toast.makeText(ctx, "${t["ai_failed"]}: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                recState = 0; file.delete()
            }
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (recorder.start()) recState = 1 else Toast.makeText(ctx, t["ai_failed"], Toast.LENGTH_SHORT).show()
        } else Toast.makeText(ctx, t["mic_denied"], Toast.LENGTH_LONG).show()
    }
    fun onMic() {
        when (recState) {
            1 -> {
                val f = recorder.stop()
                recState = 0
                if (f != null) processRecording(f)
                else Toast.makeText(ctx, t["rec_failed"], Toast.LENGTH_SHORT).show()
            }
            0 -> if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                if (recorder.start()) recState = 1 else Toast.makeText(ctx, t["ai_failed"], Toast.LENGTH_SHORT).show()
            } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun done() {
        if (form.client.isNotBlank()) {
            val id = editing?.id ?: uid()
            val raw = form.notes.trim()
            val changed = raw != (editing?.notes ?: "").trim()
            // Re-format from scratch when the raw note changed, so stale versions never linger.
            var visit = form.toVisit(id)
            if (changed) visit = visit.copy(notesAr = "", notesEn = "")
            store.upsert(visit)
            // AI formats the note into both languages automatically (needs a key).
            if (raw.isNotBlank() && store.apiKey.isNotBlank() &&
                (changed || (visit.notesAr.isBlank() && visit.notesEn.isBlank()))
            ) store.autoFormat(id, raw)
        }
        onClose()
    }
    BackHandler { done() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { done() }
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (c.dark) c.surface else Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
                onClick = { showDate = true },
            ) {
                Text(fullDay(form.date), Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    color = c.ink2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            CircleBtn(AppIcons.Person, t["customer_info"]) { infoOpen = true }
            Spacer(Modifier.width(8.dp))
            Box {
                CircleBtn(AppIcons.More, t["more"]) { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(t["customer_info"]) }, onClick = { menuOpen = false; infoOpen = true })
                    if (editing != null) DropdownMenuItem(
                        text = { Text(t["delete_visit"], color = c.lost) },
                        onClick = { menuOpen = false; store.delete(editing.id); onClose() },
                    )
                }
            }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(6.dp))
            EditorField(form.client, { form.client = it }, t["client_name_hint"], 24.sp, FontWeight.ExtraBold, 1)
            Spacer(Modifier.height(10.dp))
            EditorField(form.notes, { form.notes = it }, t["notes_hint"], 16.sp, FontWeight.Normal, 6)

            if (recState == 2) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = c.ink, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(t["polishing"], fontSize = 13.sp, color = c.muted)
                }
            }
            if (form.notesAr.isNotBlank() || form.notesEn.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(t["ai_result"], fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold)
                // Same plain, in-place writing style as the note field, so you can just edit the text.
                Spacer(Modifier.height(12.dp))
                Text("عربي", fontSize = 11.sp, color = c.faint, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                EditorField(form.notesAr, { form.notesAr = it }, "", 16.sp, FontWeight.Normal, 2)
                Spacer(Modifier.height(14.dp))
                Text("English", fontSize = 11.sp, color = c.faint, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                EditorField(form.notesEn, { form.notesEn = it }, "", 16.sp, FontWeight.Normal, 2)
            }

            Spacer(Modifier.height(18.dp))
            Text(t["quick_result"], fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Outcome.values().filterNot { it == Outcome.NONE }.forEach { outcome ->
                    ChoiceChip(outcome.label(t.en), form.outcome == outcome, outcome.color(c)) { form.outcome = outcome }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(t["quick_next"], fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(t["template_quote"], t["template_call"], t["template_follow"]).forEach { template ->
                    ChoiceChip(template, form.next == template) { form.next = if (form.next == template) "" else template }
                }
            }
            Spacer(Modifier.height(120.dp))
        }
    }
        // Voice recording — same circular style as the add-visit FAB; sits bottom-end,
        // which resolves to the right in English (LTR) and the left in Arabic (RTL).
        // Tap to record, tap again to stop and send the audio to the AI.
        Box(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                .padding(end = 22.dp, bottom = 26.dp)
                .size(56.dp).clip(CircleShape)
                .background(if (recState == 1) c.lost else c.ink)
                .clickable(enabled = recState != 2) { onMic() },
            contentAlignment = Alignment.Center,
        ) {
            when (recState) {
                2 -> CircularProgressIndicator(Modifier.size(24.dp), color = c.onInk, strokeWidth = 2.dp)
                1 -> Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(c.onInk))
                else -> Icon(AppIcons.Mic, t["voice_input"], tint = c.onInk, modifier = Modifier.size(26.dp))
            }
        }
    }

    if (infoOpen) InfoSheet(form, editing != null,
        onDelete = { editing?.let { store.delete(it.id) }; onClose() },
        onDismiss = { infoOpen = false })
    if (showDate) DatePick(form.date) { form.date = it; showDate = false }
}

@Composable
private fun InfoSheet(form: VisitForm, editing: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showTime by remember { mutableStateOf(false) }
    var showNextDate by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.bg) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(t["customer_info"], fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(bottom = 14.dp))

            Field(t["contact_person"], form.contact, { form.contact = it }, t["contact_hint"])

            LabeledBlock(t["phone"]) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Input(form.phone, { form.phone = it }, t["phone_hint"], Modifier.weight(1f), KeyboardType.Phone)
                    if (form.phone.isNotBlank()) {
                        Box(
                            Modifier.height(52.dp).clip(RoundedCornerShape(12.dp)).background(c.ink)
                                .clickable { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + form.phone.trim()))) }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.Phone, null, tint = c.onInk, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t["call"], color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Field(t["address"], form.address, { form.address = it }, t["address_hint"])
            PickerField(t["time"], fmtTime(form.time), Modifier.fillMaxWidth()) { showTime = true }

            LabeledBlock(t["visit_type"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VisitType.values().forEach { vt -> ChoiceChip(vt.label(t.en), form.type == vt) { form.type = vt } }
                }
            }
            LabeledBlock(t["outcome"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Outcome.values().forEach { o -> ChoiceChip(o.label(t.en), form.outcome == o, o.color(c)) { form.outcome = o } }
                }
            }

            Field(t["next_step"], form.next, { form.next = it }, t["next_hint"])
            PickerField(t["follow_date"], if (form.nextDate.isBlank()) t["none"] else fullDay(form.nextDate), Modifier.fillMaxWidth()) { showNextDate = true }

            if (editing) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Delete, null, tint = c.lost, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t["delete_visit"], color = c.lost, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showTime) TimePick(form.time) { form.time = it; showTime = false }
    if (showNextDate) DatePick(form.nextDate.ifBlank { todayIso() }) { form.nextDate = it; showNextDate = false }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, selColor: Color? = null, onClick: () -> Unit) {
    val c = LocalSales.current
    val bg = if (selected) (selColor ?: c.ink) else c.sunk
    // contrast the label against the actual chip colour (fixes white-on-light in dark mode)
    val fg = if (selected) (if (bg.luminance() > 0.5f) Color(0xFF0B0B0B) else Color.White) else c.ink2
    Surface(shape = RoundedCornerShape(11.dp), color = bg, onClick = onClick, modifier = Modifier.heightIn(min = 44.dp)) {
        Text(text, Modifier.padding(horizontal = 15.dp, vertical = 10.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun LabeledBlock(label: String, content: @Composable () -> Unit) {
    val c = LocalSales.current
    Column(Modifier.fillMaxWidth().padding(bottom = 15.dp)) {
        Text(label, fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 7.dp))
        content()
    }
}

@Composable
private fun Input(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, kb: KeyboardType = KeyboardType.Text) {
    val c = LocalSales.current
    TextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(),
        placeholder = { Text(hint, color = c.faint) },
        singleLine = kb != KeyboardType.Text || true,
        keyboardOptions = KeyboardOptions(keyboardType = kb),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.surface, unfocusedContainerColor = c.sunk,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = c.ink, unfocusedTextColor = c.ink, cursorColor = c.ink,
        ),
    )
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, hint: String) {
    LabeledBlock(label) { Input(value, onChange, hint) }
}

@Composable
private fun MultiField(label: String, value: String, onChange: (String) -> Unit, hint: String) {
    val c = LocalSales.current
    LabeledBlock(label) {
        TextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
            placeholder = { Text(hint, color = c.faint) },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = c.surface, unfocusedContainerColor = c.sunk,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = c.ink, unfocusedTextColor = c.ink, cursorColor = c.ink,
            ),
        )
    }
}

@Composable
private fun PickerField(label: String, shown: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalSales.current
    Column(modifier) {
        Text(label, fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 7.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = c.sunk, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(shown, Modifier.padding(horizontal = 14.dp, vertical = 15.dp), color = c.ink, fontSize = 15.sp)
        }
    }
}

@Composable
private fun DatePick(initialIso: String, onPicked: (String) -> Unit) {
    val c = LocalSales.current
    val init = parseIso(initialIso)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = init)
    DatePickerDialog(
        onDismissRequest = { onPicked(initialIso) },
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms != null) {
                    val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    onPicked(d.toString())
                } else onPicked(initialIso)
            }) { Text(LocalL.current["done"], color = c.ink) }
        },
    ) { DatePicker(state = state) }
}

@Composable
private fun TimePick(initial: String, onPicked: (String) -> Unit) {
    val c = LocalSales.current
    val parts = initial.split(":")
    val state = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 12,
        initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = false,
    )
    Dialog(onDismissRequest = { onPicked(initial) }) {
        Surface(shape = RoundedCornerShape(24.dp), color = c.surface) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onPicked("%02d:%02d".format(state.hour, state.minute)) }) {
                    Text(LocalL.current["done"], color = c.ink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/* ---------------- report ---------------- */

@Composable
private fun ReportScreen(store: Store) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var offset by remember { mutableStateOf(0) }
    val vs = weekVisits(store.visits, offset)
    val clients = vs.map { it.client }.toSet().size
    val succ = vs.count { it.outcomeEnum() == Outcome.SUCCESS }

    FrostedScaffold(header = { Header(t["report_title"], weekLabel(offset), mark = false) }) {
        // week toggle
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = c.sunk, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(3.dp)) {
                    SegBtn(t["this_week"], offset == 0, Modifier.weight(1f)) { offset = 0 }
                    SegBtn(t["last_week"], offset == 1, Modifier.weight(1f)) { offset = 1 }
                }
            }
        }
        Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(vs.size.toString(), t["stat_visits"])
                Stat(clients.toString(), t["stat_clients"])
                Stat(succ.toString(), t["stat_success"])
            }
        }
        if (vs.isEmpty()) {
            EmptyState(AppIcons.Inbox, t["empty_week"], null)
        } else {
            val maxT = VisitType.values().maxOf { vt -> vs.count { it.typeEnum() == vt } }.coerceAtLeast(1)
            Card {
                SectionTitle(t["by_type"])
                VisitType.values().forEach { vt ->
                    val n = vs.count { it.typeEnum() == vt }
                    if (n > 0) BarRow(vt.label(t.en), n, maxT, c.ink)
                }
            }
            Card {
                SectionTitle(t["week_details"])
                var lastD: String? = null
                vs.forEach { v ->
                    if (v.date != lastD) {
                        Text(fullDay(v.date), fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        lastD = v.date
                    }
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(v.outcomeEnum().color(c)))
                        Spacer(Modifier.width(9.dp))
                        Text(buildString {
                            append(v.client); append(" — "); append(v.typeEnum().label(t.en))
                            val note = v.notesFor(t.en)
                            if (note.isNotBlank()) append(" · $note")
                        }, fontSize = 14.sp, color = c.ink2, lineHeight = 20.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.ink)
                    .clickable {
                        clip.setText(AnnotatedString(reportText(store.visits, offset)))
                    }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Copy, null, tint = c.onInk, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp)); Text(t["copy"], color = c.onInk, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, c.edge, RoundedCornerShape(14.dp)).background(c.surface)
                    .clickable {
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, reportText(store.visits, offset))
                        }
                        ctx.startActivity(Intent.createChooser(i, t["share_report"]))
                    }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Share, null, tint = c.ink, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp)); Text(t["share"], color = c.ink, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(150.dp))
    }
}

@Composable
private fun SegBtn(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalSales.current
    Surface(
        modifier = modifier, shape = RoundedCornerShape(9.dp),
        color = if (on) c.surface else Color.Transparent,
        shadowElevation = if (on && !c.dark) 1.dp else 0.dp,
        onClick = onClick,
    ) {
        Text(text, Modifier.padding(vertical = 9.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (on) c.ink else c.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/* ---------------- insights ---------------- */

@Composable
private fun InsightsScreen(store: Store) {
    val c = LocalSales.current
    val t = LocalL.current
    val visits = store.visits
    FrostedScaffold(header = { Header(t["insights_tab"], t["insights_sub"], mark = false) }) {
        if (visits.isEmpty()) {
            EmptyState(AppIcons.Chart, t["empty_insights"], null)
        } else {
            val byClient = visits.groupingBy { it.client }.eachCount()
            Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(visits.size.toString(), t["total"])
                    Stat(byClient.size.toString(), t["stat_clients"])
                    Stat(visits.count { it.outcomeEnum() == Outcome.SUCCESS }.toString(), t["stat_success"])
                }
            }
            val maxO = Outcome.values().maxOf { o -> visits.count { it.outcomeEnum() == o } }.coerceAtLeast(1)
            Card {
                SectionTitle(t["by_outcome"])
                Outcome.values().forEach { o ->
                    val n = visits.count { it.outcomeEnum() == o }
                    if (n > 0) BarRow(o.label(t.en), n, maxO, o.color(c))
                }
            }
            val top = byClient.entries.sortedByDescending { it.value }.take(5)
            if (top.isNotEmpty()) {
                val maxC = top.first().value
                Card {
                    SectionTitle(t["top_clients"])
                    top.forEach { BarRow(it.key, it.value, maxC, c.ink) }
                }
            }
            val ups = visits.filter { it.next.isNotBlank() && it.nextDate.isNotBlank() && it.nextDate >= todayIso() }
                .sortedBy { it.nextDate }.take(6)
            if (ups.isNotEmpty()) {
                Card {
                    SectionTitle(t["upcoming"])
                    ups.forEach { v ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(c.ink))
                            Spacer(Modifier.width(9.dp))
                            Text("${v.client} — ${v.next} · ${fullDay(v.nextDate)}", fontSize = 14.sp, color = c.ink2, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(150.dp))
    }
}

/* ---------------- today (roadmap) ---------------- */

@Composable
private fun TodayScreen(
    store: Store,
    notificationsEnabled: Boolean,
    onEnableNotifications: () -> Unit,
    onEdit: (Visit) -> Unit,
) {
    val c = LocalSales.current
    val t = LocalL.current
    val dates = remember { (0..6).map { java.time.LocalDate.now().plusDays(it.toLong()) } }
    var selectedDate by remember { mutableStateOf(todayIso()) }
    val items = store.planFor(selectedDate)
    val due = if (selectedDate == todayIso()) store.dueFollowUps()
    else store.visits.filter { it.next.isNotBlank() && it.nextDate == selectedDate }
    val done = items.count { it.done }
    val remaining = items.size - done
    val progress = if (items.isEmpty()) 0f else done.toFloat() / items.size
    val ctx = LocalContext.current
    val routeStops = items.filterNot { it.done }.map { item ->
        store.customerFor(item.client)?.address?.ifBlank { item.client } ?: item.client
    }
    var newClient by remember { mutableStateOf("") }

    FrostedScaffold(header = {
        Header(if (selectedDate == todayIso()) t["today_title"] else t["today_plan"], fullDay(selectedDate), mark = false) {
            IconButton(onClick = onEnableNotifications) {
                Icon(
                    if (notificationsEnabled) AppIcons.Notifications else AppIcons.NotificationsOff,
                    t["reminders"], tint = if (notificationsEnabled) c.ink else c.muted,
                )
            }
        }
    }) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dates.forEach { date ->
                val iso = date.toString()
                val selected = iso == selectedDate
                Surface(
                    onClick = { selectedDate = iso },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) c.ink else c.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) c.ink else c.edge),
                    modifier = Modifier.width(62.dp).height(72.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(dayName(date), maxLines = 1, fontSize = 10.sp, color = if (selected) c.onInk else c.muted)
                        Spacer(Modifier.height(4.dp))
                        Text(date.dayOfMonth.toString(), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) c.onInk else c.ink)
                    }
                }
            }
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (selectedDate == todayIso()) t["today_progress"] else t["day_progress"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("$done / ${items.size}", color = c.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)),
                color = c.ink, trackColor = c.sunk,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(done.toString(), t["completed"])
                Stat(remaining.toString(), t["remaining"])
                Stat(due.size.toString(), t["followups_short"])
            }
        }

        if (!notificationsEnabled) {
            Surface(
                onClick = onEnableNotifications,
                shape = RoundedCornerShape(16.dp), color = c.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.NotificationsOff, null, tint = c.ink2, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t["enable_reminders"], color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Text(t["enable_reminders_desc"], color = c.muted, fontSize = 12.5.sp)
                    }
                    Icon(AppIcons.ArrowBack, null, tint = c.faint)
                }
            }
        }

        if (due.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text(t["due_followups"], color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                due.forEach { visit -> DueFollowUpRow(visit) { onEdit(visit) } }
            }
        }

        Text(
            t["today_plan"], color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Input(newClient, { newClient = it }, t["add_client_hint"], Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(c.ink)
                    .clickable { store.addPlan(newClient, selectedDate); newClient = "" },
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Add, t["add_client_hint"], tint = c.onInk, modifier = Modifier.size(24.dp)) }
        }

        if (items.isEmpty()) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Map, null, tint = c.muted, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (selectedDate == todayIso()) t["empty_today_title"] else t["empty_day_title"], color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(t["empty_today_desc"], color = c.muted, fontSize = 12.5.sp)
                    }
                }
            }
        } else {
            Text(
                "$done / ${items.size} ${t["stops_done"]}",
                fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 6.dp),
            )
            if (routeStops.isNotEmpty()) {
                Button(
                    onClick = {
                        mapsRouteUri(routeStops)?.let { uri ->
                            if (routeStops.size > 10) Toast.makeText(ctx, t["route_limit"], Toast.LENGTH_LONG).show()
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.onInk),
                ) {
                    Icon(AppIcons.Directions, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("${t["open_route"]} · ${routeStops.size}", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
            }
            Column(Modifier.padding(horizontal = 18.dp)) {
                items.forEachIndexed { i, item ->
                    val destination = store.customerFor(item.client)?.address?.ifBlank { item.client } ?: item.client
                    RoadStop(
                        index = i + 1, item = item, first = i == 0, last = i == items.lastIndex,
                        onToggle = { store.togglePlan(item.id) }, onDelete = { store.deletePlan(item.id) },
                        onNavigate = { mapsRouteUri(listOf(destination))?.let { ctx.startActivity(Intent(Intent.ACTION_VIEW, it)) } },
                    )
                }
            }
        }
        Spacer(Modifier.height(150.dp))
    }
}

@Composable
private fun DueFollowUpRow(visit: Visit, onClick: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val overdue = visit.nextDate < todayIso()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp), color = c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Notifications, null, tint = c.ink2, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(visit.client, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(visit.next, color = c.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                if (overdue) t["overdue"] else t["today"],
                color = if (overdue) c.lost else c.ink2, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RoadStop(index: Int, item: PlanItem, first: Boolean, last: Boolean, onToggle: () -> Unit, onDelete: () -> Unit, onNavigate: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    Row(Modifier.height(IntrinsicSize.Min)) {
        // rail: connecting line + node
        Box(Modifier.width(40.dp).fillMaxHeight()) {
            if (!first) Box(Modifier.align(Alignment.TopCenter).width(2.5.dp).fillMaxHeight(0.5f).background(c.faint))
            if (!last) Box(Modifier.align(Alignment.BottomCenter).width(2.5.dp).fillMaxHeight(0.5f).background(c.faint))
            Box(
                Modifier.align(Alignment.Center).size(30.dp).clip(CircleShape)
                    .background(if (item.done) c.ink else c.surface)
                    .border(2.dp, if (item.done) c.ink else c.muted, CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (item.done) Icon(AppIcons.Check, null, tint = c.onInk, modifier = Modifier.size(17.dp))
                else Text("$index", color = c.ink2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            shape = RoundedCornerShape(16.dp), color = c.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
            shadowElevation = if (c.dark) 0.dp else 2.dp,
            onClick = onToggle,
        ) {
            Row(Modifier.padding(start = 16.dp, end = 6.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.client, modifier = Modifier.weight(1f),
                    color = if (item.done) c.muted else c.ink,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onNavigate) { Icon(AppIcons.Directions, t["navigate"], tint = c.ink2, modifier = Modifier.size(19.dp)) }
                IconButton(onClick = onDelete) { Icon(AppIcons.Close, "حذف", tint = c.faint, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

/* ---------------- account + profile ---------------- */

@Composable
private fun ProfileScreen(store: Store, account: CloudAccount?, onSettings: () -> Unit, onEdit: () -> Unit) {
    val t = LocalL.current
    FrostedScaffold(header = { Header(t["profile"], t["profile_sub"], mark = false) }) {
        if (account == null) {
            GroupCard {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(AppIcons.Person, null, tint = LocalSales.current.faint, modifier = Modifier.size(46.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(t["firebase_pending"], color = LocalSales.current.ink, fontWeight = FontWeight.Bold)
                }
            }
        } else if (account.user == null) {
            SignInCard(account)
        } else {
            SignedInProfile(store, account, onSettings, onEdit)
        }
        Spacer(Modifier.height(118.dp))
    }
}

@Composable
private fun SignInCard(account: CloudAccount) {
    val c = LocalSales.current
    val t = LocalL.current
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    GroupCard {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (creating) t["create_account"] else t["sign_in"],
                color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = LocalDisplayFont.current,
            )
            Spacer(Modifier.height(5.dp))
            Text(t["account_desc"], color = c.muted, fontSize = 13.5.sp)
            Spacer(Modifier.height(20.dp))
            if (creating) {
                AccountField(t["full_name"], name, { name = it }, KeyboardType.Text)
                Spacer(Modifier.height(10.dp))
            }
            AccountField(t["email"], email, { email = it }, KeyboardType.Email)
            Spacer(Modifier.height(10.dp))
            AccountField(t["password"], password, { password = it }, KeyboardType.Password, password = true)
            val message = accountMessage(account.errorCode, t)
            if (message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(message, color = if (account.errorCode == "reset_sent") c.ok else c.lost, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = {
                    if (creating) account.createAccount(name, email, password)
                    else account.signIn(email, password)
                },
                enabled = !account.busy,
                color = c.ink, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (account.busy) CircularProgressIndicator(Modifier.size(20.dp), color = c.onInk, strokeWidth = 2.dp)
                    else Text(if (creating) t["create_account"] else t["sign_in"], color = c.onInk, fontWeight = FontWeight.Bold)
                }
            }
            if (!creating) {
                TextButton(onClick = { account.resetPassword(email) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(t["forgot_password"], color = c.muted)
                }
            }
            TextButton(
                onClick = { creating = !creating },
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
            ) {
                Text(if (creating) t["have_account"] else t["need_account"], color = c.ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AccountField(label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType, password: Boolean = false) {
    val c = LocalSales.current
    TextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.sunk, unfocusedContainerColor = c.sunk,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = c.ink, unfocusedTextColor = c.ink, cursorColor = c.ink,
            focusedLabelColor = c.muted, unfocusedLabelColor = c.muted,
        ),
    )
}

@Composable
private fun SignedInProfile(store: Store, account: CloudAccount, onSettings: () -> Unit, onEdit: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val profile = account.profile
    val email = account.user?.email.orEmpty()
    val name = profile.name.ifBlank { email.substringBefore('@') }
    val role = listOf(profile.jobTitle, profile.company).filter { it.isNotBlank() }.joinToString(" · ")
    val success = store.visits.count { it.outcomeEnum() == Outcome.SUCCESS }

    GroupCard {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                Text(profileInitials(name), color = c.onInk, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(12.dp))
            Text(name, color = c.ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current)
            if (role.isNotBlank()) Text(role, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
            Text(email, color = c.muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(store.visits.size.toString(), t["stat_visits"])
                Stat(store.customers.size.toString(), t["stat_clients"])
                Stat(success.toString(), t["stat_success"])
            }
        }
    }

    GroupCard {
        SettingsRow(AppIcons.Person, t["edit_profile"]) { onEdit() }
        HorizontalDivider(color = c.edge)
        SettingsRow(AppIcons.Update, t["cloud_sync"], value = t["sync_${account.syncState}"]) {}
        HorizontalDivider(color = c.edge)
        SettingsRow(AppIcons.Settings, t["settings"]) { onSettings() }
    }

    GroupCard {
        Text(t["appearance"], fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChip(t["theme_auto"], store.theme == "auto", Modifier.weight(1f)) { store.chooseTheme("auto") }
            ThemeChip(t["theme_light"], store.theme == "light", Modifier.weight(1f)) { store.chooseTheme("light") }
            ThemeChip(t["theme_dark"], store.theme == "dark", Modifier.weight(1f)) { store.chooseTheme("dark") }
        }
        HorizontalDivider(color = c.edge)
        Text(t["language"], fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChip(t["lang_auto"], store.langMode == "auto", Modifier.weight(1f)) { store.chooseLang("auto") }
            ThemeChip(t["lang_ar"], store.langMode == "ar", Modifier.weight(1f)) { store.chooseLang("ar") }
            ThemeChip(t["lang_en"], store.langMode == "en", Modifier.weight(1f)) { store.chooseLang("en") }
        }
    }

    GroupCard { SettingsRow(AppIcons.ArrowBack, t["sign_out"], danger = true) { account.signOut() } }
}

@Composable
private fun ProfileEditorScreen(account: CloudAccount, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val original = account.profile
    var name by remember(original) { mutableStateOf(original.name) }
    var job by remember(original) { mutableStateOf(original.jobTitle) }
    var company by remember(original) { mutableStateOf(original.company) }
    var phone by remember(original) { mutableStateOf(original.phone) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["cancel"], tint = c.ink) }
            Text(t["edit_profile"], color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    account.saveProfile(UserProfile(name, job, company, phone))
                    onBack()
                }
            }) { Text(t["done"], color = c.ink, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Field(t["full_name"], name, { name = it }, t["full_name"])
            Field(t["job_title"], job, { job = it }, t["job_title_hint"])
            Field(t["company"], company, { company = it }, t["company_hint"])
            LabeledBlock(t["phone"]) { Input(phone, { phone = it }, t["phone_hint"], kb = KeyboardType.Phone) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun profileInitials(name: String): String = name.trim().split(Regex("\\s+"))
    .filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "V" }

private fun accountMessage(code: String?, t: L): String = when (code) {
    "name" -> t["error_name"]
    "email" -> t["error_email"]
    "password" -> t["error_password"]
    "credentials" -> t["error_credentials"]
    "email_used" -> t["error_email_used"]
    "network" -> t["error_network"]
    "too_many" -> t["error_too_many"]
    "reset_sent" -> t["reset_sent"]
    "auth_failed" -> t["error_auth"]
    else -> ""
}

/* ---------------- settings ---------------- */

@Composable
private fun SettingsScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 22.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.Close, t["done"], tint = c.ink) }
            Text(t["settings"], fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
        }

        GroupCard {
            Text(t["appearance"], fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip(t["theme_auto"], store.theme == "auto", Modifier.weight(1f)) { store.chooseTheme("auto") }
                ThemeChip(t["theme_light"], store.theme == "light", Modifier.weight(1f)) { store.chooseTheme("light") }
                ThemeChip(t["theme_dark"], store.theme == "dark", Modifier.weight(1f)) { store.chooseTheme("dark") }
            }
            HorizontalDivider(color = c.edge)
            Text(t["color_style"], fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip(t["palette_classic"], store.palette == "classic", Modifier.weight(1f)) { store.choosePalette("classic") }
                ThemeChip(t["palette_warm"], store.palette == "warm", Modifier.weight(1f)) { store.choosePalette("warm") }
            }
        }

        GroupCard {
            Text(t["language"], fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip(t["lang_auto"], store.langMode == "auto", Modifier.weight(1f)) { store.chooseLang("auto") }
                ThemeChip(t["lang_ar"], store.langMode == "ar", Modifier.weight(1f)) { store.chooseLang("ar") }
                ThemeChip(t["lang_en"], store.langMode == "en", Modifier.weight(1f)) { store.chooseLang("en") }
            }
        }

        AiSection(store)

        BackupSection(store)

        UpdateSection()

        GroupCard {
            SettingsRow(AppIcons.Delete, t["delete_all"], danger = true) { confirmClear = true }
        }

        GroupCard {
            SettingsRow(AppIcons.Info, t["about"], value = t["version"]) {}
        }
        Spacer(Modifier.height(40.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = { TextButton(onClick = { store.clearAll(); confirmClear = false }) { Text(t["delete"], color = c.lost) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(t["cancel"], color = c.muted) } },
            title = { Text(t["delete_all_q"]) },
            text = { Text(t["delete_all_desc"]) },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun AiSection(store: Store) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(store.apiKey) }
    var testing by remember { mutableStateOf(false) }

    GroupCard {
        Text(t["ai_formatting"], fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        Text(t["ai_desc"], fontSize = 12.5.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(t["api_key"], fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 7.dp))
            TextField(
                value = key,
                onValueChange = { key = it; store.chooseApiKey(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(t["api_key_hint"], color = c.faint) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = c.surface, unfocusedContainerColor = c.sunk,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = c.ink, unfocusedTextColor = c.ink, cursorColor = c.ink,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                t["get_key"],
                fontSize = 12.5.sp, color = c.lead, fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }
                },
            )
        }
        HorizontalDivider(color = c.edge, modifier = Modifier.padding(top = 14.dp))
        SettingsRow(AppIcons.Check, if (testing) t["ai_testing"] else t["ai_test"]) {
            if (testing) return@SettingsRow
            if (store.apiKey.isBlank()) { Toast.makeText(ctx, t["need_key"], Toast.LENGTH_LONG).show(); return@SettingsRow }
            testing = true
            scope.launch {
                val ok = runCatching { AiFormatter.format(store.apiKey, store.aiModel, "Sample visit note for a connection test.") }.isSuccess
                testing = false
                Toast.makeText(ctx, if (ok) t["ai_test_ok"] else t["ai_failed"], Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun BackupSection(store: Store) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val saved = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.exportBackup()) }
                    ?: error("Unable to open backup destination")
            }.isSuccess
            Toast.makeText(ctx, if (saved) t["backup_saved"] else t["backup_failed"], Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val restored = runCatching {
                val raw = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Unable to read backup")
                store.restoreBackup(raw)
            }.getOrDefault(false)
            Toast.makeText(ctx, if (restored) t["backup_restored"] else t["backup_failed"], Toast.LENGTH_SHORT).show()
        }
    }

    GroupCard {
        Text(t["backup"], fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        Text(t["backup_desc"], fontSize = 12.5.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        SettingsRow(AppIcons.Upload, t["export_backup"]) {
            exportLauncher.launch("sales-visits-${todayIso()}.json")
        }
        HorizontalDivider(color = c.edge)
        SettingsRow(AppIcons.Download, t["import_backup"]) {
            importLauncher.launch(arrayOf("application/json", "text/plain"))
        }
    }
}

@Composable
private fun UpdateSection() {
    val t = LocalL.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var busy by remember { mutableStateOf(false) }

    GroupCard {
        val label = if (info != null) "${t["install_update"]} (${info!!.versionName})" else t["check_update"]
        val value = status.ifBlank { "v" + AppUpdater.currentVersionName(ctx) }
        SettingsRow(AppIcons.Update, label, value = value) {
            if (busy) return@SettingsRow
            val i = info
            if (i == null) {
                busy = true; status = t["checking"]
                scope.launch {
                    try {
                        val r = AppUpdater.check(ctx)
                        if (r == null) status = t["up_to_date"]
                        else { info = r; status = "${t["update_available"]} v${r.versionName}" }
                    } catch (e: Exception) {
                        status = t["update_failed"]
                    } finally { busy = false }
                }
            } else {
                if (!AppUpdater.canInstall(ctx)) { ctx.startActivity(AppUpdater.installPermissionIntent(ctx)); return@SettingsRow }
                busy = true; status = t["downloading"]
                scope.launch {
                    try {
                        val f = AppUpdater.download(ctx, i) { p -> status = "${t["downloading"]} ${(p * 100).toInt()}%" }
                        AppUpdater.install(ctx, f)
                        status = ""
                    } catch (e: Exception) {
                        status = t["update_failed"]
                    } finally { busy = false }
                }
            }
        }
    }
}

@Composable
private fun ThemeChip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalSales.current
    Surface(shape = RoundedCornerShape(11.dp), color = if (on) c.ink else c.sunk, onClick = onClick, modifier = modifier) {
        Text(text, Modifier.padding(vertical = 11.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (on) c.onInk else c.ink2, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, value: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    val c = LocalSales.current
    val tint = if (danger) c.lost else c.ink2
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (danger) c.lost else c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = c.muted, fontSize = 13.sp)
        else if (!danger) Icon(AppIcons.ArrowBack, null, tint = c.faint, modifier = Modifier.size(20.dp))
    }
}

/* ---------------- small shared ---------------- */

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalSales.current
    Surface(
        shape = RoundedCornerShape(16.dp), color = c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalSales.current
    Surface(
        shape = RoundedCornerShape(16.dp), color = c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
    ) { Column(content = content) }
}

@Composable
private fun SectionTitle(t: String) {
    val c = LocalSales.current
    Text(t, fontSize = 13.sp, color = c.muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun RowScope.Stat(n: String, label: String) {
    val c = LocalSales.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
        Text(n, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = c.muted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BarRow(label: String, value: Int, max: Int, color: Color) {
    val c = LocalSales.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = c.ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(82.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(99.dp)).background(c.sunk)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(value.toFloat() / max).clip(RoundedCornerShape(99.dp)).background(color))
        }
        Spacer(Modifier.width(10.dp))
        Text(value.toString(), fontSize = 14.sp, color = c.ink2, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
    }
}
