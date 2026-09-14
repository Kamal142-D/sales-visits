@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.sales.visits

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.CalendarContract
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.lerp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

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

    // "Poured" theme switch: capture the current frame, flip the theme, then reveal the new one
    // through a circle growing from the toggle button.
    val scope = rememberCoroutineScope()
    val revealBitmap = remember { mutableStateOf<ImageBitmap?>(null) }
    val revealPivot = remember { mutableStateOf(Offset.Zero) }
    val revealRadius = remember { Animatable(0f) }
    val toggleTheme: (Offset) -> Unit = { pivot ->
        val activity = view.context as? Activity
        val w = activity?.window
        val width = view.width; val height = view.height
        if (w != null && width > 0 && height > 0 && revealBitmap.value == null) {
            val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val wasDark = dark
            runCatching {
                android.view.PixelCopy.request(w, bmp, { res ->
                    if (res == android.view.PixelCopy.SUCCESS) {
                        revealBitmap.value = bmp.asImageBitmap()
                        revealPivot.value = pivot
                        store.chooseTheme(if (wasDark) "light" else "dark")
                        scope.launch {
                            revealRadius.snapTo(0f)
                            val maxR = kotlin.math.hypot(
                                maxOf(pivot.x, width - pivot.x).toDouble(),
                                maxOf(pivot.y, height - pivot.y).toDouble(),
                            ).toFloat()
                            revealRadius.animateTo(maxR, tween(560, easing = FastOutSlowInEasing))
                            revealBitmap.value = null
                        }
                    }
                }, android.os.Handler(android.os.Looper.getMainLooper()))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    SalesTheme(dark, en, store.palette) {
        val c = LocalSales.current
        CompositionLocalProvider(
            LocalLayoutDirection provides if (en) LayoutDirection.Ltr else LayoutDirection.Rtl,
            LocalL provides if (en) EN else AR,
            LocalThemeReveal provides ThemeRevealHandle(dark, toggleTheme),
        ) {
            val ctx = LocalContext.current
            val cloud = remember { FirebaseApp.initializeApp(ctx)?.let { CloudAccount(ctx, store) } }
            DisposableEffect(cloud) { onDispose { cloud?.close() } }
            val team = remember(cloud) { if (cloud != null) CompanyRepository() else null }
            DisposableEffect(team) { onDispose { team?.close() } }
            var notificationGranted by remember { mutableStateOf(ReminderScheduler.notificationsEnabled(ctx)) }
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> notificationGranted = granted }
            var tab by remember { mutableStateOf(0) }
            var showSettings by remember { mutableStateOf(false) }
            var showProfileEditor by remember { mutableStateOf(false) }
            var showCard by remember { mutableStateOf(false) }
            var showCustomers by remember { mutableStateOf(false) }
            var showInsights by remember { mutableStateOf(false) }
            var showTools by remember { mutableStateOf(false) }
            var showTeam by remember { mutableStateOf(false) }
            var showOpps by remember { mutableStateOf(false) }
            var customerEditorOpen by remember { mutableStateOf(false) }
            var editingCustomer by remember { mutableStateOf<Customer?>(null) }
            var customerProfileOpen by remember { mutableStateOf(false) }
            var profileCustomer by remember { mutableStateOf<Customer?>(null) }
            var editorOpen by remember { mutableStateOf(false) }
            var editing by remember { mutableStateOf<Visit?>(null) }
            // How the editor was opened, so it animates in accordingly:
            //  - from a visit card  → scales/expands out of the card's position
            //  - from the + button  → circular ink-reveal (like the theme switch)
            var editorFromFab by remember { mutableStateOf(false) }
            var editorPivot by remember { mutableStateOf(Offset.Zero) }
            var fabCenter by remember { mutableStateOf(Offset.Zero) }
            var rootSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            var editorMounted by remember { mutableStateOf(false) }
            val editorAnim = remember { Animatable(0f) }
            LaunchedEffect(editorOpen) {
                if (editorOpen) {
                    editorMounted = true
                    editorAnim.snapTo(0f)
                    editorAnim.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
                } else if (editorMounted) {
                    editorAnim.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                    editorMounted = false
                }
            }

            Box(Modifier.fillMaxSize().background(c.bg).onGloballyPositioned { rootSize = it.size }) {
                when {
                    showCard && cloud != null && cloud.user != null ->
                        BusinessCardScreen(cloud.profile, cloud.user?.email.orEmpty()) { showCard = false }
                    showProfileEditor && cloud != null -> ProfileEditorScreen(store, cloud) { showProfileEditor = false }
                    customerEditorOpen -> CustomerEditor(store, editingCustomer) { customerEditorOpen = false }
                    customerProfileOpen && profileCustomer != null -> CustomerProfileScreen(
                        store, profileCustomer!!, team = team,
                        onEdit = { editingCustomer = it; customerEditorOpen = true },
                        onBack = { customerProfileOpen = false },
                    )
                    showCustomers -> CustomersScreen(
                        store,
                        onBack = { showCustomers = false },
                        onAdd = { editingCustomer = null; customerEditorOpen = true },
                        onEdit = { profileCustomer = it; customerProfileOpen = true },
                    )
                    showSettings -> SettingsScreen(store, cloud) { showSettings = false }
                    // These live inside the Tools hub, so they must render ABOVE ToolsScreen.
                    showInsights -> InsightsScreen(store) { showInsights = false }
                    showOpps -> OpportunitiesScreen(store) { showOpps = false }
                    showTeam && team != null && cloud?.user != null ->
                        TeamScreen(team, cloud) { showTeam = false }
                    showTools -> ToolsScreen(
                        store,
                        onInsights = { showInsights = true },
                        onOpps = { showOpps = true },
                        onTeam = if (team != null && cloud?.user != null) ({ showTeam = true }) else null,
                    ) { showTools = false }
                    else -> key(tab) {
                        when (tab) {
                            0 -> VisitsScreen(
                                store,
                                onEdit = { v, pivot -> editing = v; editorPivot = pivot; editorFromFab = false; editorOpen = true },
                            )
                            1 -> TodayScreen(
                                store,
                                notificationsEnabled = notificationGranted,
                                onEnableNotifications = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                onEdit = { editing = it; editorPivot = Offset.Zero; editorFromFab = false; editorOpen = true },
                            )
                            2 -> ReportScreen(store)
                            3 -> TasksScreen(store)
                            else -> ProfileScreen(
                                store = store,
                                account = cloud,
                                onSettings = { showSettings = true },
                                onEdit = { showProfileEditor = true },
                                onCustomers = { showCustomers = true },
                                onTools = { showTools = true },
                                onShareCard = { showCard = true },
                            )
                        }
                    }
                }

                if (!showSettings && !showProfileEditor && !showCard && !editorOpen && !editorMounted && !showCustomers && !customerEditorOpen && !customerProfileOpen && !showInsights && !showTools && !showTeam && !showOpps) {
                    if (tab == 0) {
                        Fab(
                            onClick = { editing = null; editorPivot = fabCenter; editorFromFab = true; editorOpen = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(end = 18.dp, bottom = 88.dp)
                                .onGloballyPositioned { fabCenter = it.boundsInWindow().center },
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

                // The visit editor, overlaid on top of the current tab so it can animate in/out.
                if (editorMounted) {
                    val p = editorAnim.value
                    val w = rootSize.width.toFloat().coerceAtLeast(1f)
                    val h = rootSize.height.toFloat().coerceAtLeast(1f)
                    val pv = if (editorPivot == Offset.Zero) Offset(w / 2f, h / 2f) else editorPivot
                    val mod = if (editorFromFab) {
                        // Circular ink-reveal growing from the + button.
                        val maxR = kotlin.math.hypot(maxOf(pv.x, w - pv.x), maxOf(pv.y, h - pv.y))
                        Modifier.fillMaxSize().graphicsLayer {
                            alpha = (p * 3f).coerceAtMost(1f)
                            clip = true
                            shape = CircleRevealShape(pv, maxR * p)
                        }
                    } else {
                        // Grow the whole editor out of the tapped card's position.
                        Modifier.fillMaxSize().graphicsLayer {
                            val s = 0.6f + 0.4f * p
                            scaleX = s; scaleY = s; alpha = p
                            transformOrigin = TransformOrigin(pv.x / w, pv.y / h)
                        }
                    }
                    Box(mod) { VisitEditor(store, editing) { editorOpen = false } }
                }
            }
        }
    }
        // Frozen old frame on top, with a growing transparent circle revealing the new theme.
        val revealFrame = revealBitmap.value
        if (revealFrame != null) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                drawImage(revealFrame)
                drawCircle(color = Color.Black, radius = revealRadius.value, center = revealPivot.value, blendMode = BlendMode.Clear)
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
    val collapse = LocalHeaderCollapse.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(
            start = 22.dp, end = 22.dp,
            top = lerp(18.dp, 10.dp, collapse), bottom = lerp(6.dp, 10.dp, collapse),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mark) {
                    Icon(AppIcons.Place, null, tint = c.ink, modifier = Modifier.size(lerp(26.dp, 20.dp, collapse)))
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, fontSize = lerp(30.sp, 20.sp, collapse), fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
            }
            // Subtitle fades and collapses away as the header shrinks.
            if (subtitle != null && collapse < 0.9f) {
                Spacer(Modifier.height(lerp(6.dp, 0.dp, collapse)))
                Text(
                    subtitle, fontSize = 13.5.sp, color = c.muted, fontWeight = FontWeight.Medium,
                    modifier = Modifier.graphicsLayer { alpha = (1f - collapse * 1.4f).coerceIn(0f, 1f) },
                )
            }
        }
        if (action != null) action()
    }
}

/** A screen with a frosted-glass top header: content scrolls and blurs behind the fixed header. */
@Composable
private fun FrostedScaffold(header: @Composable () -> Unit, staggerBody: Boolean = true, body: @Composable () -> Unit) {
    val c = LocalSales.current
    val haze = remember { HazeState() }
    var headerH by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    // Telegram/iOS-style collapse: the big title shrinks into a compact bar as the content scrolls.
    val scroll = rememberScrollState()
    val collapsePx = with(density) { 88.dp.toPx() }
    val collapse = (scroll.value / collapsePx).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().haze(haze).verticalScroll(scroll)) {
            Spacer(Modifier.height(with(density) { headerH.toDp() }))
            if (staggerBody) StaggeredContent(delayMillis = 70, content = body) else body()
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
                CompositionLocalProvider(LocalHeaderCollapse provides collapse) {
                    StaggeredContent(content = header)
                }
                Spacer(Modifier.height(lerp(64.dp, 20.dp, collapse))) // fade zone shrinks as it collapses
            }
        }
    }
}

@Composable
private fun StaggeredContent(delayMillis: Int = 0, content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            delayMillis = delayMillis,
            easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f),
        ),
        label = "staggered content reveal",
    )
    val distance = with(LocalDensity.current) { 16.dp.toPx() }
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val height = placeables.sumOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            val stagger = 0.12f
            val span = 1f + stagger * (placeables.size - 1).coerceAtLeast(0)
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                val itemProgress = (progress * span - index * stagger).coerceIn(0f, 1f)
                placeable.placeRelativeWithLayer(
                    x = 0,
                    y = y + ((1f - itemProgress) * distance).roundToInt(),
                ) {
                    alpha = itemProgress
                    // Cards grow into place (GL-style pop) each time the screen is entered.
                    val s = 0.90f + 0.10f * itemProgress
                    scaleX = s; scaleY = s
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.35f)
                }
                y += placeable.height
            }
        }
    }
}

/**
 * Per-item entrance pop: fades, rises and scales a single card into place, staggered by [index].
 * Replays every time the composable enters composition (e.g. returning to the Visits tab).
 */
@Composable
private fun PopIn(index: Int, content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = 360,
            delayMillis = (40 * index).coerceAtMost(480),  // cap so long lists stay snappy
            easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f),
        ),
        label = "pop-in",
    )
    val distance = with(LocalDensity.current) { 18.dp.toPx() }
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            val s = 0.90f + 0.10f * progress
            scaleX = s; scaleY = s
            translationY = (1f - progress) * distance
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.3f)
        }
    ) { content() }
}

/** A clip shape that reveals content through a circle of [radiusPx] centred at [center]. */
private class CircleRevealShape(val center: Offset, val radiusPx: Float) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val path = Path().apply {
            addOval(Rect(center = center, radius = radiusPx.coerceAtLeast(0.01f)))
        }
        return Outline.Generic(path)
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
    val tabs = listOf(
        t["visits"] to AppIcons.Home, t["today"] to AppIcons.Directions,
        t["report_tab"] to AppIcons.Report, t["tasks_tab"] to AppIcons.Plan,
        t["profile"] to AppIcons.Person,
    )
    val itemSize = 48.dp
    val gap = 2.dp
    // The active pill slides between tabs with a spring (Revolut-style).
    val indicatorOffset by animateDpAsState(
        targetValue = (itemSize + gap) * tab,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "nav indicator",
    )
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = c.navBg,
        shadowElevation = if (c.dark) 0.dp else 10.dp,
    ) {
        Box(Modifier.padding(6.dp)) {
            Box(
                Modifier.offset(x = indicatorOffset).size(itemSize).clip(CircleShape).background(c.navActiveBg),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavItem(label, icon, tab == i, itemSize) { onTab(i) }
                }
            }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, on: Boolean, size: Dp, onClick: () -> Unit) {
    val c = LocalSales.current
    val fg by androidx.compose.animation.animateColorAsState(if (on) c.navFg else c.navFgDim, tween(220), label = "nav tint")
    val scale by animateFloatAsState(if (on) 1.12f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f), label = "nav scale")
    Box(
        Modifier.size(size).clip(CircleShape).clickable(onClick = onClick).padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale })
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
private fun SearchableHeader(
    title: String, searching: Boolean, query: String,
    onQuery: (String) -> Unit, onOpen: () -> Unit, onClose: () -> Unit, hint: String,
) {
    val c = LocalSales.current
    val collapse = LocalHeaderCollapse.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(
            start = 22.dp, end = 14.dp,
            top = lerp(18.dp, 10.dp, collapse), bottom = lerp(6.dp, 10.dp, collapse),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = searching,
            transitionSpec = {
                (fadeIn(tween(220)) + expandHorizontally(tween(240), expandFrom = Alignment.End)) togetherWith
                    (fadeOut(tween(140)) + shrinkHorizontally(tween(200), shrinkTowards = Alignment.End))
            },
            label = "search",
            modifier = Modifier.fillMaxWidth(),
        ) { isSearch ->
            if (!isSearch) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = lerp(30.sp, 20.sp, collapse), fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink, modifier = Modifier.weight(1f))
                    Box(Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Search, hint, tint = c.ink, modifier = Modifier.size(23.dp))
                    }
                }
            } else {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = c.sunk, modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Search, null, tint = c.faint, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            BasicTextField(
                                value = query, onValueChange = onQuery,
                                modifier = Modifier.weight(1f).focusRequester(focus),
                                singleLine = true,
                                textStyle = TextStyle(color = c.ink, fontSize = 16.sp, fontFamily = LocalAppFont.current),
                                cursorBrush = SolidColor(c.ink),
                                decorationBox = { inner ->
                                    if (query.isEmpty()) Text(hint, color = c.faint, fontSize = 16.sp)
                                    inner()
                                },
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                        Icon(CloseXIcon, hint, tint = c.ink, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitsScreen(store: Store, onEdit: (Visit, Offset) -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val visits = store.visits
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    fun exitSelect() { selecting = false; selected.clear() }
    BackHandler(selecting) { exitSelect() }
    FrostedScaffold(header = {
        if (selecting) {
            Header(t["visits"], "${selected.size}", mark = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected.isNotEmpty()) IconButton(onClick = { store.deleteVisits(selected.toList()); exitSelect() }) {
                        Icon(AppIcons.Delete, t["delete"], tint = c.lost)
                    }
                    IconButton(onClick = { exitSelect() }) { Icon(CloseXIcon, t["done"], tint = c.ink) }
                }
            }
        } else {
            SearchableHeader(
                title = t["visits"], searching = searching, query = query,
                onQuery = { query = it }, onOpen = { searching = true },
                onClose = { searching = false; query = "" }, hint = t["search_visits"],
            )
        }
    }, staggerBody = false) {
        val q = query.trim().lowercase()
        val shown = if (q.isBlank()) visits else visits.filter {
            it.client.lowercase().contains(q) || it.notes.lowercase().contains(q) ||
                it.notesAr.lowercase().contains(q) || it.notesEn.lowercase().contains(q)
        }
        if (shown.isEmpty()) {
            PopIn(0) {
                EmptyState(
                    if (q.isBlank()) AppIcons.Plan else AppIcons.Search,
                    if (q.isBlank()) t["empty_visits_title"] else t["no_results"],
                    if (q.isBlank()) t["empty_visits_desc"] else null,
                )
            }
        } else {
            val sorted = shown.sortedByDescending { it.date + it.time }
            val left = sorted.filterIndexed { i, _ -> i % 2 == 0 }
            val right = sorted.filterIndexed { i, _ -> i % 2 == 1 }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val onCardClick: (Visit, Offset) -> Unit = { v, center ->
                    if (selecting) { if (v.id in selected) selected.remove(v.id) else selected.add(v.id) }
                    else onEdit(v, center)
                }
                val onCardLong: (Visit) -> Unit = { v -> if (!selecting) { selecting = true; selected.add(v.id) } }
                // Each card pops in on its own, staggered top-to-bottom across the two columns.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    left.forEachIndexed { k, v -> PopIn(k * 2) { VisitCard(v, selecting, v.id in selected, { onCardClick(v, it) }, { onCardLong(v) }) } }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    right.forEachIndexed { k, v -> PopIn(k * 2 + 1) { VisitCard(v, selecting, v.id in selected, { onCardClick(v, it) }, { onCardLong(v) }) } }
                }
            }
        }
        Spacer(Modifier.height(150.dp))
    }
}

@Composable
private fun VisitCard(v: Visit, selecting: Boolean = false, selectedNow: Boolean = false, onClick: (Offset) -> Unit, onLongClick: () -> Unit = {}) {
    val c = LocalSales.current
    val t = LocalL.current
    var center by remember { mutableStateOf(Offset.Zero) }
    Surface(
        modifier = Modifier
            .onGloballyPositioned { center = it.boundsInWindow().center }
            .combinedClickable(onClick = { onClick(center) }, onLongClick = onLongClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selectedNow) c.sunk else c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedNow) c.ink else c.edge),
        shadowElevation = if (c.dark) 0.dp else 2.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            if (selecting) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(if (selectedNow) c.ink else Color.Transparent)
                            .border(2.dp, if (selectedNow) c.ink else c.muted, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (selectedNow) Icon(CheckIcon, null, tint = c.onInk, modifier = Modifier.size(13.dp)) }
                }
            }
            // Easlo-style card: day name, company name, then the notes body. Nothing else.
            Text(cardDayShort(v.date), fontSize = 12.5.sp, color = c.muted, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text(v.client, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, lineHeight = 22.sp)
            val cardNote = v.notesFor(t.en)
            if (cardNote.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                // Compact preview: a few lines of flat text; open the visit to read it all.
                Text(
                    markdownToPlain(cardNote), color = c.muted, fontSize = 14.sp, lineHeight = 20.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
            }
            if (v.images.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    v.images.take(4).forEach { path ->
                        val bmp = remember(path) { loadImageBitmap(path, 320) }
                        if (bmp != null) {
                            Image(bmp, null, modifier = Modifier.size(66.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
            if (v.checklist.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                v.checklist.take(6).forEach { item ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (item.done) c.ink else Color.Transparent)
                                .border(1.5.dp, if (item.done) c.ink else c.edge, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (item.done) Icon(CheckIcon, null, tint = c.onInk, modifier = Modifier.size(11.dp)) }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item.text, fontSize = 13.sp, color = if (item.done) c.faint else c.ink2, lineHeight = 18.sp,
                            textDecoration = if (item.done) TextDecoration.LineThrough else null,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                    }
                }
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
    var relFilter by remember { mutableStateOf<RelationshipStatus?>(null) }
    BackHandler(onBack = onBack)
    val q = query.trim()
    val filtered = store.customers.filter { customer ->
        (relFilter == null || customer.relationshipStatus() == relFilter) &&
            (q.isBlank() || listOf(customer.name, customer.contact, customer.phone, customer.city, customer.industry)
                .any { it.contains(q, ignoreCase = true) })
    }

    FrostedScaffold(header = {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t["customers"], fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink, modifier = Modifier.weight(1f))
            IconButton(onClick = onAdd) { Icon(AppIcons.PersonAdd, t["add_customer"], tint = c.ink) }
        }
    }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Input(query, { query = it }, t["search_customers"], Modifier.weight(1f))
        }
        // Prospect-bank filter: All / Prospects / Customers / Archived.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceChip(t["all"], relFilter == null) { relFilter = null }
            RelationshipStatus.values().forEach { rs ->
                ChoiceChip(rs.label(t.en), relFilter == rs) { relFilter = rs }
            }
        }
        Spacer(Modifier.height(4.dp))
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
    // Numbers you can call: contacts that have a phone, else the customer's own number.
    val callable = customer.contacts.filter { it.phone.isNotBlank() }
        .ifEmpty { if (customer.phone.isNotBlank()) listOf(ContactPerson(customer.contact, "", customer.phone)) else emptyList() }
    val locUrl = customer.locationUrl.trim()
    var showCallChooser by remember { mutableStateOf(false) }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(customer.name, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    val rs = customer.relationshipStatus()
                    if (rs != RelationshipStatus.CUSTOMER) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(999.dp), color = c.sunk) {
                            Text(rs.label(t.en), Modifier.padding(horizontal = 7.dp, vertical = 2.dp), color = c.muted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val detail = when {
                    last != null -> "${visits.size} ${t["visit_count"]} · ${cardDay(last.date)}"
                    customer.contact.isNotBlank() -> customer.contact
                    else -> t["no_visits"]
                }
                Text(detail, color = c.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (callable.isNotEmpty()) {
                IconButton(onClick = {
                    if (callable.size == 1) runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + callable[0].phone.trim()))) }
                    else showCallChooser = true
                }) { Icon(AppIcons.Phone, t["call"], tint = c.ink2, modifier = Modifier.size(20.dp)) }
            }
            if (locUrl.isNotBlank() || customer.address.isNotBlank()) {
                IconButton(onClick = {
                    val uri = if (locUrl.isNotBlank()) Uri.parse(locUrl) else mapsRouteUri(listOf(customer.address))
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }) { Icon(AppIcons.Directions, t["navigate"], tint = c.ink2, modifier = Modifier.size(20.dp)) }
            }
            if (callable.isEmpty() && locUrl.isBlank() && customer.address.isBlank()) {
                Icon(if (t.en) AppIcons.ArrowForward else AppIcons.ArrowBack, null, tint = c.faint)
            }
        }
    }

    if (showCallChooser) {
        AlertDialog(
            onDismissRequest = { showCallChooser = false },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCallChooser = false }) { Text(t["cancel"], color = c.muted) } },
            title = { Text(t["choose_contact"]) },
            text = {
                Column {
                    callable.forEach { cp ->
                        Surface(
                            onClick = {
                                showCallChooser = false
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cp.phone.trim()))) }
                            },
                            color = Color.Transparent, modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.Phone, null, tint = c.ink2, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(cp.name.ifBlank { cp.phone }, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    if (cp.name.isNotBlank()) Text(cp.phone, color = c.muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            },
            containerColor = c.surface,
        )
    }
}

private class CustomerForm(customer: Customer?) {
    val id = customer?.id ?: uid()
    var name by mutableStateOf(customer?.name ?: "")
    var industry by mutableStateOf(customer?.industry ?: "")
    var phone by mutableStateOf(customer?.phone ?: "")
    var address by mutableStateOf(customer?.address ?: "")
    var locationUrl by mutableStateOf(customer?.locationUrl ?: "")
    var notes by mutableStateOf(customer?.notes ?: "")
    val createdAt = customer?.createdAt?.ifBlank { todayIso() } ?: todayIso()
    // Prospect-bank fields. A brand-new customer starts as a PROSPECT until they buy.
    var city by mutableStateOf(customer?.city ?: "")
    var region by mutableStateOf(customer?.region ?: "")
    var website by mutableStateOf(customer?.website ?: "")
    var source by mutableStateOf(customer?.source ?: "")
    var relationship by mutableStateOf(customer?.relationship?.ifBlank { "CUSTOMER" } ?: "PROSPECT")
    var equipment by mutableStateOf(customer?.equipment ?: "")
    var apps by mutableStateOf(customer?.apps ?: "")

    // Multiple contact people; seeded from the old single contact for existing customers.
    val contacts = mutableStateListOf<ContactPerson>().apply {
        val seed = customer?.contacts?.takeIf { it.isNotEmpty() }
            ?: customer?.contact?.takeIf { it.isNotBlank() }?.let { listOf(ContactPerson(it)) }
            ?: emptyList()
        addAll(seed)
    }

    fun toCustomer(): Customer {
        val clean = contacts.map {
            val phone = if (splitPhone(it.phone).second.isBlank()) "" else it.phone.trim()
            it.copy(name = it.name.trim(), email = it.email.trim(), phone = phone)
        }.filter { it.name.isNotBlank() || it.email.isNotBlank() || it.phone.isNotBlank() }
        return Customer(
            id = id, name = name, industry = industry, contact = clean.firstOrNull()?.name.orEmpty(),
            phone = clean.firstOrNull()?.phone?.takeIf { it.isNotBlank() } ?: phone,
            address = address, locationUrl = locationUrl, notes = notes, createdAt = createdAt, contacts = clean,
            city = city.trim(), region = region.trim(), website = website.trim(), source = source.trim(),
            relationship = relationship, equipment = equipment.trim(), apps = apps.trim(),
        )
    }
}

@Composable
private fun ActionSquare(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val c = LocalSales.current
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(c.ink).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = c.onInk, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun ContactsEditor(contacts: MutableList<ContactPerson>) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    LabeledBlock(t["contacts"]) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            contacts.forEachIndexed { i, cp ->
                Surface(shape = RoundedCornerShape(14.dp), color = c.sunk, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${t["contact_person"]} ${i + 1}", fontSize = 12.sp, color = c.muted, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Box(
                                Modifier.clip(CircleShape).clickable { contacts.removeAt(i) }.padding(4.dp),
                                contentAlignment = Alignment.Center,
                            ) { Icon(CloseXIcon, t["delete"], tint = c.faint, modifier = Modifier.size(16.dp)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Input(cp.name, { contacts[i] = contacts[i].copy(name = it) }, t["contact_hint"])
                        Spacer(Modifier.height(8.dp))
                        Input(cp.jobTitle, { contacts[i] = contacts[i].copy(jobTitle = it) }, t["job_title_hint"])
                        Spacer(Modifier.height(8.dp))
                        // Decision roles this person plays (multi-select).
                        Text(t["decision_role"], fontSize = 11.5.sp, color = c.muted, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DecisionRole.values().forEach { dr ->
                                val on = dr.name in cp.roles
                                ChoiceChip(dr.label(t.en), on) {
                                    val next = if (on) cp.roles - dr.name else cp.roles + dr.name
                                    contacts[i] = contacts[i].copy(roles = next)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Input(cp.email, { contacts[i] = contacts[i].copy(email = it) }, t["contact_email_hint"], kb = KeyboardType.Email) }
                            if (cp.email.isNotBlank()) ActionSquare(AppIcons.Email, t["email"]) {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + cp.email.trim()))) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                PhoneField(cp.phone) { contacts[i] = contacts[i].copy(phone = it) }
                            }
                            if (splitPhone(cp.phone).second.isNotBlank()) ActionSquare(AppIcons.Phone, t["call"]) {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cp.phone.trim()))) }
                            }
                        }
                    }
                }
            }
            Surface(onClick = { contacts.add(ContactPerson()) }, shape = RoundedCornerShape(12.dp), color = c.sunk, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Add, null, tint = c.ink2, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t["add_contact"], color = c.ink2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

/** Read-oriented customer profile: all details + purchases (orders & total) + visit history. */
@Composable
private fun CustomerProfileScreen(store: Store, customer: Customer, team: CompanyRepository? = null, onEdit: (Customer) -> Unit, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    // Stay live: re-read the customer from the store so edits reflect immediately.
    val cust = store.customers.firstOrNull { it.id == customer.id } ?: customer
    val history = store.visitsFor(cust)
    val orders = store.ordersForCustomer(cust)
    val activities = store.activitiesForCustomer(cust)
    val timeline = mergeTimeline(history, activities)
    // Totals are kept per-currency; SAR and USD are never summed into one number.
    val totals = store.purchasedByCurrency(cust)
    val primaryTotal = totals.entries.maxByOrNull { it.value }
    var orderSheet by remember { mutableStateOf(false) }
    var editingOrder by remember { mutableStateOf<Order?>(null) }
    var activitySheet by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<com.sales.visits.Activity?>(null) }
    val quotes = store.quotesForCustomer(cust)
    var quoteEditorOpen by remember { mutableStateOf(false) }
    var editingQuote by remember { mutableStateOf<Quote?>(null) }
    // Attachments (6.3) — metadata syncs; bytes upload when Firebase Storage is enabled.
    val storageRepo = remember { StorageRepo(ctx.applicationContext, store) }
    val attachments = store.attachmentsFor("CUSTOMER", cust.id)
    val attPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { storageRepo.addFromUri(it, "CUSTOMER", cust.id) }
    }
    // Pre-visit AI brief (plan 4.1)
    val scope = rememberCoroutineScope()
    var briefOpen by remember { mutableStateOf(false) }
    var briefLoading by remember { mutableStateOf(false) }
    var briefError by remember { mutableStateOf<String?>(null) }
    var brief by remember { mutableStateOf<AiFormatter.PrevisitBrief?>(null) }
    var briefJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var consentDialog by remember { mutableStateOf(false) }
    var consentThen by remember { mutableStateOf<() -> Unit>({}) }
    var oppPick by remember { mutableStateOf(false) }
    var composeOpen by remember { mutableStateOf(false) }
    fun requireAi(then: () -> Unit) {
        if (store.apiKey.isBlank()) { Toast.makeText(ctx, t["need_key"], Toast.LENGTH_LONG).show(); return }
        if (!store.aiConsent) { consentThen = then; consentDialog = true } else then()
    }
    val activeOpps = store.opportunities.filter {
        ((it.customerId.isNotBlank() && it.customerId == cust.id) || (it.customerId.isBlank() && it.customerName.trim().equals(cust.name.trim(), true))) && it.stageEnum().isActive
    }
    fun runBrief(opp: Opportunity?) {
        val ctxText = PrevisitContext.build(
            cust, opp, history, activities, quotes,
            store.tasks.filter { it.client.trim().equals(cust.name.trim(), true) && it.statusEnum() != TaskStatus.DONE && it.statusEnum() != TaskStatus.CANCELLED },
        )
        briefOpen = true; briefLoading = true; brief = null; briefError = null
        briefJob = scope.launch {
            try { brief = AiFormatter.prepareVisit(store.apiKey, store.aiModel, ctxText, t.en) }
            catch (e: Exception) { briefError = e.message ?: "error" }
            finally { briefLoading = false }
        }
    }
    fun startBrief() = requireAi {
        if (activeOpps.size > 1) oppPick = true else runBrief(activeOpps.firstOrNull())
    }
    val callable = cust.contacts.filter { it.phone.isNotBlank() }
        .ifEmpty { if (cust.phone.isNotBlank()) listOf(ContactPerson(cust.contact, "", cust.phone)) else emptyList() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
                Spacer(Modifier.weight(1f))
                CircleBtn(AppIcons.Settings, t["edit"]) { onEdit(cust) }
            }
            // Profile header
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                    Text(cust.name.trim().take(1).uppercase(), color = c.onInk, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(12.dp))
                Text(cust.name, color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(999.dp), color = c.ink) {
                        Text(cust.relationshipStatus().label(t.en), Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = c.onInk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    if (cust.industry.isNotBlank()) Surface(shape = RoundedCornerShape(999.dp), color = c.sunk) {
                        Text(cust.industry, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = c.ink2, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    val place = listOf(cust.city, cust.region).filter { it.isNotBlank() }.joinToString("، ")
                    if (place.isNotBlank()) Surface(shape = RoundedCornerShape(999.dp), color = c.sunk) {
                        Text(place, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = c.ink2, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // AI actions: prepare for the visit, or draft a message — both from recorded context only.
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(onClick = { startBrief() }, shape = RoundedCornerShape(16.dp), color = c.ink, modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Plan, null, tint = c.onInk, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t["previsit_prep"], color = c.onInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                Surface(onClick = { requireAi { composeOpen = true } }, shape = RoundedCornerShape(16.dp), color = c.surface, border = androidx.compose.foundation.BorderStroke(1.dp, c.ink), modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Email, null, tint = c.ink, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t["compose_ai"], color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            // Stats
            Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(history.size.toString(), t["stat_visits"])
                    Stat(orders.size.toString(), t["orders"])
                    Stat(if (primaryTotal != null) fmtMoney(primaryTotal.value) else "0", t["total_purchased"])
                }
            }

            // Business details (only the fields that are filled in).
            val details = listOf(
                t["website"] to cust.website, t["source"] to cust.source,
                t["equipment"] to cust.equipment, t["apps_field"] to cust.apps,
            ).filter { it.second.isNotBlank() }
            if (details.isNotEmpty()) Card {
                details.forEachIndexed { i, (label, value) ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Text(label, color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(value, color = c.ink, fontSize = 14.sp)
                }
            }

            // Purchases
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t["purchases"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        if (totals.isEmpty()) {
                            Text("${t["total_purchased"]}: 0", color = c.muted, fontSize = 12.5.sp)
                        } else {
                            // One line per currency — no cross-currency addition.
                            totals.entries.sortedByDescending { it.value }.forEach { (cur, amt) ->
                                Text("${fmtMoney(amt)} $cur", color = c.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Surface(onClick = { editingOrder = null; orderSheet = true }, shape = RoundedCornerShape(999.dp), color = c.ink) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Add, null, tint = c.onInk, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(t["add_order"], color = c.onInk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (orders.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(t["no_orders"], color = c.muted, fontSize = 13.sp)
                } else {
                    orders.forEach { o ->
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            onClick = { editingOrder = o; orderSheet = true },
                            shape = RoundedCornerShape(12.dp), color = c.sunk, modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(o.title.ifBlank { t["order"] }, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val sub = listOf(if (o.status == "ORDERED") t["order_ordered"] else t["order_delivered"], o.date).filter { it.isNotBlank() }.joinToString(" · ")
                                    Text(sub, color = c.muted, fontSize = 12.sp)
                                }
                                Text("${fmtMoney(o.amount)}${if (o.currency.isNotBlank()) " " + o.currency else ""}", color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            // Quotes
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t["quotes"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Surface(onClick = { editingQuote = null; quoteEditorOpen = true }, shape = RoundedCornerShape(999.dp), color = c.ink) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Add, null, tint = c.onInk, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(t["new_quote"], color = c.onInk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (quotes.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(t["no_quotes"], color = c.muted, fontSize = 13.sp)
                } else {
                    quotes.forEach { q ->
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            onClick = { editingQuote = q; quoteEditorOpen = true },
                            shape = RoundedCornerShape(12.dp), color = c.sunk, modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    val head = q.number.ifBlank { t["quote"] } + (if (q.version > 1) " · v${q.version}" else "")
                                    Text(head, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(q.statusEnum().label(t.en) + (if (q.orderId.isNotBlank()) " · ${t["order"]}" else ""), color = c.muted, fontSize = 12.sp)
                                }
                                Text("${fmtMoney(QuoteMath.total(q))}${if (q.currency.isNotBlank()) " " + q.currency else ""}", color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            // Attachments (6.3)
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t["attachments"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Surface(onClick = { runCatching { attPicker.launch(arrayOf("*/*")) } }, shape = RoundedCornerShape(999.dp), color = c.ink) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Add, null, tint = c.onInk, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(t["add_file"], color = c.onInk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (attachments.isEmpty()) {
                    Spacer(Modifier.height(10.dp)); Text(t["no_attachments"], color = c.muted, fontSize = 13.sp)
                } else attachments.forEach { a ->
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = c.sunk, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Inbox, null, tint = c.ink2, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (a.uploaded) t["att_uploaded"] else t["att_local"], color = c.muted, fontSize = 11.sp)
                            }
                            IconButton(onClick = { store.deleteAttachment(a.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(AppIcons.Delete, t["delete"], tint = c.faint, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Share to team (6.2) — only when signed into a company.
            if (team?.companyId != null) {
                val shared = team.isCustomerShared(cust.id)
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.People, null, tint = c.ink2, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t["share_to_team"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(if (shared) t["shared_with_team"] else t["share_to_team_desc"], color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        if (shared) {
                            TextButton(onClick = { team.deleteTeamCustomer(cust.id) }) { Text(t["unshare"], color = c.lost) }
                        } else {
                            TextButton(onClick = { team.shareCustomer(cust) }) { Text(t["share"], color = c.ink) }
                        }
                    }
                }
            }

            // Contacts
            if (cust.contacts.isNotEmpty()) {
                Text(t["contacts"], color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 4.dp))
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cust.contacts.forEach { cp ->
                        Surface(shape = RoundedCornerShape(14.dp), color = c.surface, border = androidx.compose.foundation.BorderStroke(1.dp, c.edge)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(cp.name.ifBlank { cust.name }, color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val line = listOf(cp.phone, cp.email).filter { it.isNotBlank() }.joinToString(" · ")
                                    if (line.isNotBlank()) Text(line, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (cp.email.isNotBlank()) IconButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + cp.email.trim()))) } }) {
                                    Icon(AppIcons.Email, t["email"], tint = c.ink2, modifier = Modifier.size(19.dp))
                                }
                                if (splitPhone(cp.phone).second.isNotBlank()) IconButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cp.phone.trim()))) } }) {
                                    Icon(AppIcons.Phone, t["call"], tint = c.ink2, modifier = Modifier.size(19.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Address + location
            if (cust.address.isNotBlank() || cust.locationUrl.isNotBlank()) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t["address"], color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(cust.address.ifBlank { t["location_url"] }, color = c.ink, fontSize = 14.sp)
                        }
                        if (cust.locationUrl.isNotBlank() || cust.address.isNotBlank()) IconButton(onClick = {
                            val uri = if (cust.locationUrl.isNotBlank()) Uri.parse(cust.locationUrl.trim()) else mapsRouteUri(listOf(cust.address))
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        }) { Icon(AppIcons.Directions, t["open_location"], tint = c.ink2, modifier = Modifier.size(20.dp)) }
                    }
                }
            }

            // Notes
            if (cust.notes.isNotBlank()) {
                Card {
                    Text(t["customer_notes"], color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(cust.notes, color = c.ink2, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }

            // Unified timeline: visits + logged activities, newest first (plan 3.1).
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t["timeline"], color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(onClick = { editingActivity = null; activitySheet = true }, shape = RoundedCornerShape(999.dp), color = c.ink) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Add, null, tint = c.onInk, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(t["log_activity"], color = c.onInk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (timeline.isEmpty()) {
                Text(t["no_activity"], color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
            } else {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    timeline.forEach { item ->
                        when (item) {
                            is VisitItem -> {
                                val visit = item.visit
                                Surface(shape = RoundedCornerShape(14.dp), color = c.surface, border = androidx.compose.foundation.BorderStroke(1.dp, c.edge)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutcomeDot(visit.outcomeEnum())
                                            Spacer(Modifier.width(8.dp))
                                            Text("${t["visit"]}: ${visit.typeEnum().label(t.en)}", color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Text(fullDay(visit.date), color = c.muted, fontSize = 12.sp)
                                        }
                                        val note = visit.notesFor(t.en)
                                        if (note.isNotBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(markdownToPlain(note), color = c.muted, fontSize = 13.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            is ActivityItem -> {
                                val a = item.activity
                                Surface(
                                    onClick = { editingActivity = a; activitySheet = true },
                                    shape = RoundedCornerShape(14.dp), color = c.surface, border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(activityIcon(activityTypeEnum(a.type)), null, tint = c.ink2, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(8.dp))
                                            val head = activityTypeEnum(a.type).label(t.en) +
                                                (activityResultEnum(a.result).takeIf { it != ActivityResult.NONE }?.let { " · ${it.label(t.en)}" } ?: "")
                                            Text(head, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Text(if (a.date.isNotBlank()) fullDay(a.date) else "", color = c.muted, fontSize = 12.sp)
                                        }
                                        if (a.summary.isNotBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(a.summary, color = c.muted, fontSize = 13.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
        // Full-screen quote editor overlays the profile when open.
        if (quoteEditorOpen) QuoteEditor(store, cust, editingQuote) { quoteEditorOpen = false }
    }

    if (orderSheet) OrderSheet(store, cust.name, editingOrder) { orderSheet = false }
    if (activitySheet) ActivitySheet(store, cust, editingActivity) { activitySheet = false }

    if (consentDialog) AlertDialog(
        onDismissRequest = { consentDialog = false },
        confirmButton = { TextButton(onClick = { store.grantAiConsent(); consentDialog = false; consentThen() }) { Text(t["agree"]) } },
        dismissButton = { TextButton(onClick = { consentDialog = false }) { Text(t["cancel"], color = c.muted) } },
        title = { Text(t["ai_consent_title"]) },
        text = { Text(t["ai_consent_desc"]) },
        containerColor = c.surface,
    )

    if (composeOpen) ComposeSheet(store, cust) { composeOpen = false }

    if (oppPick) AlertDialog(
        onDismissRequest = { oppPick = false },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { oppPick = false }) { Text(t["cancel"], color = c.muted) } },
        title = { Text(t["previsit_pick_opp"]) },
        text = {
            Column {
                Surface(onClick = { oppPick = false; runBrief(null) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                    Text(t["previsit_general"], Modifier.padding(vertical = 10.dp), color = c.ink, fontWeight = FontWeight.Bold)
                }
                activeOpps.forEach { o ->
                    Surface(onClick = { oppPick = false; runBrief(o) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                        Text(o.title, Modifier.padding(vertical = 10.dp), color = c.ink2)
                    }
                }
            }
        },
        containerColor = c.surface,
    )

    if (briefOpen) PrevisitSheet(
        loading = briefLoading, error = briefError, brief = brief,
        onRetry = { startBrief() },
        onCancel = { briefJob?.cancel(); briefLoading = false },
        onDismiss = { briefJob?.cancel(); briefOpen = false },
    )
}

/** Shows the AI pre-visit brief: loading (cancellable), error (retry), or the sections. */
@Composable
private fun PrevisitSheet(
    loading: Boolean, error: String?, brief: AiFormatter.PrevisitBrief?,
    onRetry: () -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit,
) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(t["previsit_prep"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(bottom = 12.dp))
            when {
                loading -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = c.ink, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(t["previsit_thinking"], color = c.muted, fontSize = 13.sp)
                    }
                    TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t["cancel"], color = c.muted) }
                }
                error != null -> {
                    Text(t["ai_failed"], color = c.lost, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(error, color = c.muted, fontSize = 12.5.sp)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(t["retry"], enabled = true) { onRetry() }
                }
                brief != null -> {
                    PrevisitSection(t["previsit_summary"], listOf(brief.summary))
                    PrevisitSection(t["previsit_goal"], listOf(brief.goal))
                    PrevisitSection(t["previsit_questions"], brief.questions)
                    PrevisitSection(t["previsit_commitments"], brief.commitments)
                    PrevisitSection(t["previsit_watch"], brief.watchOut)
                    Text(t["previsit_disclaimer"], color = c.faint, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun PrevisitSection(title: String, items: List<String>) {
    val c = LocalSales.current
    val shown = items.filter { it.isNotBlank() }
    if (shown.isEmpty()) return
    Text(title, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    shown.forEach { line ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            if (shown.size > 1) { Text("•  ", color = c.muted, fontSize = 14.sp) }
            Text(line, color = c.ink2, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

/** AI message composer (plan 4.3): pick type/tone/recipient, generate, edit, then copy/email/share.
 *  No direct send — it hands off to the mail/share app (device does the sending). */
@Composable
private fun ComposeSheet(store: Store, customer: Customer, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by remember { mutableStateOf(DraftType.POST_VISIT) }
    var tone by remember { mutableStateOf(DraftTone.FRIENDLY) }
    var english by remember { mutableStateOf(store.reportLang == "en") }
    val contacts = customer.contacts.filter { it.name.isNotBlank() }
    var recipient by remember { mutableStateOf(contacts.firstOrNull()) }
    val custOpps = store.opportunities.filter {
        (it.customerId.isNotBlank() && it.customerId == customer.id) || (it.customerId.isBlank() && it.customerName.trim().equals(customer.name.trim(), true))
    }
    var oppId by remember { mutableStateOf(custOpps.firstOrNull { it.stageEnum().isActive }?.id ?: "") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun generate() {
        val opp = custOpps.firstOrNull { it.id == oppId }
        val recent = store.activitiesForCustomer(customer).firstOrNull()?.summary
            ?: store.visitsFor(customer).firstOrNull()?.notesFor(english).orEmpty()
        val quote = if (type == DraftType.QUOTE_FOLLOWUP) store.quotesForCustomer(customer).firstOrNull() else null
        val context = DraftContext.build(type, customer, opp, recipient, recent, quote)
        loading = true; error = null
        job = scope.launch {
            try {
                val r = AiFormatter.draftMessage(store.apiKey, store.aiModel, type, tone, context, english)
                subject = r.subject; body = r.body
            } catch (e: Exception) { error = e.message ?: "error" } finally { loading = false }
        }
    }

    ModalBottomSheet(onDismissRequest = { job?.cancel(); onDismiss() }, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(t["compose_ai"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(bottom = 12.dp))

            LabeledBlock(t["compose_type"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DraftType.values().forEach { d -> ChoiceChip(d.label(t.en), type == d) { type = d } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    LabeledBlock(t["compose_tone"]) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DraftTone.values().forEach { tn -> ChoiceChip(tn.label(t.en), tone == tn) { tone = tn } }
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    LabeledBlock(t["language"]) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip("EN", english) { english = true }
                            ChoiceChip("AR", !english) { english = false }
                        }
                    }
                }
            }
            if (!type.isInternal && contacts.isNotEmpty()) LabeledBlock(t["compose_recipient"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    contacts.forEach { cp -> ChoiceChip(cp.name.ifBlank { cp.phone }, recipient?.id == cp.id) { recipient = cp } }
                }
            }
            if (custopps_active(custOpps).isNotEmpty()) LabeledBlock(t["opp_link"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(t["none"], oppId.isBlank()) { oppId = "" }
                    custopps_active(custOpps).forEach { o -> ChoiceChip(o.title, oppId == o.id) { oppId = o.id } }
                }
            }

            Spacer(Modifier.height(10.dp))
            PrimaryButton(if (body.isBlank()) t["compose_generate"] else t["compose_regenerate"], enabled = !loading) { generate() }

            if (loading) {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = c.ink, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp)); Text(t["previsit_thinking"], color = c.muted, fontSize = 13.sp)
                }
                TextButton(onClick = { job?.cancel(); loading = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t["cancel"], color = c.muted) }
            }
            if (error != null) { Spacer(Modifier.height(8.dp)); Text("${t["ai_failed"]}: $error", color = c.lost, fontSize = 12.5.sp) }

            if (body.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                if (!type.isInternal || subject.isNotBlank()) {
                    LabeledBlock(t["compose_subject"]) { Input(subject, { subject = it }, t["compose_subject"]) }
                }
                LabeledBlock(t["compose_body"]) { MultiField("", body, { body = it }, t["compose_body"]) }
                Text(t["previsit_disclaimer"], color = c.faint, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(t["copy"], false) { clip.setText(AnnotatedString(listOf(subject, body).filter { it.isNotBlank() }.joinToString("\n\n"))); Toast.makeText(ctx, t["copied"], Toast.LENGTH_SHORT).show() }
                    val email = recipient?.email?.trim().orEmpty()
                    if (!type.isInternal && email.isNotBlank()) ChoiceChip(t["email"], false) {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                                putExtra(Intent.EXTRA_SUBJECT, subject); putExtra(Intent.EXTRA_TEXT, body)
                            })
                        }
                    }
                    ChoiceChip(t["share"], false) {
                        runCatching {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").apply {
                                putExtra(Intent.EXTRA_SUBJECT, subject); putExtra(Intent.EXTRA_TEXT, listOf(subject, body).filter { it.isNotBlank() }.joinToString("\n\n"))
                            }
                            ctx.startActivity(Intent.createChooser(send, null))
                        }
                    }
                }
            }
        }
    }
}

private fun custopps_active(l: List<Opportunity>) = l.filter { it.stageEnum().isActive }

/** In-app AI command bar (plan 4.4): the AI parses the command; the APP validates, shows a
 *  confirmation card, and executes via Store operations. No free DB access, no auto delete/send. */
@Composable
private fun CommandSheet(store: Store, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var intent by remember { mutableStateOf<CommandIntent?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun run() {
        if (input.isBlank()) return
        loading = true; error = null; intent = null; result = null
        job = scope.launch {
            try { intent = AiFormatter.parseCommand(store.apiKey, store.aiModel, input.trim(), todayIso()) }
            catch (e: Exception) { error = e.message ?: "error" } finally { loading = false }
        }
    }

    // Resolve the target customer for the parsed intent (exact, else unambiguous contains).
    fun resolveCustomer(name: String): Customer? {
        if (name.isBlank()) return null
        store.customerFor(name)?.let { return it }
        val hits = store.customers.filter { it.name.contains(name, true) }
        return hits.singleOrNull()
    }

    ModalBottomSheet(onDismissRequest = { job?.cancel(); onDismiss() }, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(t["assistant"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(bottom = 4.dp))
            Text(t["assistant_hint"], color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Input(input, { input = it }, t["assistant_placeholder"]) }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(50.dp).clip(CircleShape).background(c.ink).clickable { run() }, contentAlignment = Alignment.Center) {
                    Icon(if (t.en) AppIcons.ArrowForward else AppIcons.ArrowBack, null, tint = c.onInk, modifier = Modifier.size(22.dp))
                }
            }

            if (loading) Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = c.ink, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
            }
            error?.let { Spacer(Modifier.height(8.dp)); Text("${t["ai_failed"]}: $it", color = c.lost, fontSize = 12.5.sp) }
            result?.let { Spacer(Modifier.height(12.dp)); Text(it, color = c.ok, fontSize = 14.sp, fontWeight = FontWeight.Bold) }

            val i = intent
            if (i != null && result == null) {
                Spacer(Modifier.height(14.dp))
                when (i.action) {
                    CommandAction.ADD_TASK -> {
                        val cust = resolveCustomer(i.customer)
                        Surface(shape = RoundedCornerShape(14.dp), color = c.sunk, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(t["cmd_add_task"], color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(i.text.ifBlank { "—" }, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                val meta = listOfNotNull(
                                    i.customer.takeIf { it.isNotBlank() }?.let { "@$it" },
                                    i.date.takeIf { it.isNotBlank() }?.let { fullDay(it) },
                                    i.time.takeIf { it.isNotBlank() }?.let { fmtTime(it) },
                                ).joinToString(" · ")
                                if (meta.isNotBlank()) { Spacer(Modifier.height(3.dp)); Text(meta, color = c.muted, fontSize = 12.5.sp) }
                                if (i.customer.isNotBlank() && cust == null) { Spacer(Modifier.height(4.dp)); Text(t["cmd_customer_unmatched"], color = c.lost, fontSize = 11.5.sp) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(t["cmd_confirm"], enabled = i.text.isNotBlank()) {
                            val date = i.date.ifBlank { todayIso() }
                            if (taskExists(store.tasks, i.customer, i.text, date)) { result = t["cmd_dup"] }
                            else { store.addTask(i.text, i.customer, date, i.time, source = TaskSource.AI); result = t["cmd_added"] }
                        }
                    }
                    CommandAction.RESCHEDULE_TASK -> {
                        val q = i.query.ifBlank { i.text }
                        val match = store.tasks.firstOrNull {
                            it.statusEnum() != TaskStatus.DONE && it.statusEnum() != TaskStatus.CANCELLED &&
                                (it.action.contains(q, true) || it.client.contains(q, true))
                        }
                        if (match == null) Text(t["cmd_task_not_found"], color = c.muted, fontSize = 13.sp)
                        else {
                            Surface(shape = RoundedCornerShape(14.dp), color = c.sunk, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(t["cmd_reschedule"], color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text(match.action.ifBlank { match.client }, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("${fullDay(match.date)} → ${if (i.date.isNotBlank()) fullDay(i.date) else "—"}", color = c.muted, fontSize = 12.5.sp)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            PrimaryButton(t["cmd_confirm"], enabled = i.date.isNotBlank()) { store.rescheduleTask(match.id, i.date); result = t["cmd_moved"] }
                        }
                    }
                    CommandAction.SEARCH -> {
                        val q = i.query.ifBlank { i.customer }.ifBlank { i.text }
                        val custs = store.customers.filter { it.name.contains(q, true) }.take(6)
                        val opps = store.opportunities.filter { it.title.contains(q, true) || it.customerName.contains(q, true) }.take(6)
                        val tks = store.tasks.filter { it.action.contains(q, true) || it.client.contains(q, true) }.take(6)
                        if (custs.isEmpty() && opps.isEmpty() && tks.isEmpty()) Text(t["no_results"], color = c.muted, fontSize = 13.sp)
                        else Column {
                            custs.forEach { Text("👤 ${it.name}", color = c.ink2, fontSize = 13.5.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                            opps.forEach { Text("📈 ${it.title} · ${it.customerName}", color = c.ink2, fontSize = 13.5.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                            tks.forEach { Text("✓ ${it.action.ifBlank { it.client }}", color = c.ink2, fontSize = 13.5.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                        }
                    }
                    CommandAction.UNKNOWN -> Text(t["cmd_unknown"], color = c.muted, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Reviews AI-extracted deal facts (plan 4.2): current vs proposed, per-field confirm, one apply.
 *  Applying to a non-existent deal creates one — an explicit user action, never by name-guessing. */
@Composable
private fun ExtractReviewSheet(
    store: Store, clientName: String, loading: Boolean, error: String?, extract: AiFormatter.VisitExtract?,
    onRetry: () -> Unit, onDismiss: () -> Unit,
) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val existing = remember(clientName, store.opportunities) {
        store.opportunities.filter { it.customerName.trim().equals(clientName.trim(), true) }
            .let { l -> l.firstOrNull { it.stageEnum().isActive } ?: l.firstOrNull() }
    }
    val checks = remember { mutableStateMapOf<String, Boolean>() }

    fun budgetLabel(v: String) = if (v.isBlank()) "" else budgetEnum(v).label(t.en)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(t["extract_title"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(bottom = 4.dp))
            Text(if (existing != null) "${t["extract_apply_to"]}: ${existing.title}" else t["extract_new_deal"], color = c.muted, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 12.dp))

            when {
                loading -> Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = c.ink, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp)); Text(t["previsit_thinking"], color = c.muted, fontSize = 13.sp)
                }
                error != null -> { Text("${t["ai_failed"]}: $error", color = c.lost, fontSize = 12.5.sp); Spacer(Modifier.height(12.dp)); PrimaryButton(t["retry"], enabled = true) { onRetry() } }
                extract == null || extract.isEmpty() -> Text(t["extract_nothing"], color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                else -> {
                    // key, label, current, proposed
                    val rows = buildList {
                        if (extract.need.isNotBlank()) add(arrayOf("need", t["opp_need"], existing?.need.orEmpty(), extract.need))
                        if (extract.problem.isNotBlank()) add(arrayOf("problem", t["opp_problem"], existing?.problem.orEmpty(), extract.problem))
                        if (extract.product.isNotBlank()) add(arrayOf("product", t["opp_product"], existing?.product.orEmpty(), extract.product))
                        if (extract.budgetStatus.isNotBlank()) add(arrayOf("budget", t["opp_budget"], existing?.budgetStatus?.let { budgetLabel(it) }.orEmpty(), budgetLabel(extract.budgetStatus)))
                        if (extract.closeDate.isNotBlank()) add(arrayOf("close", t["opp_close_date"], existing?.closeDate.orEmpty(), extract.closeDate))
                        if (extract.competitor.isNotBlank()) add(arrayOf("competitor", t["opp_competitor"], existing?.competitor.orEmpty(), extract.competitor))
                        if (extract.nextStep.isNotBlank()) add(arrayOf("next", t["next_step"], existing?.nextStep.orEmpty(), extract.nextStep))
                        if (extract.objection.isNotBlank()) add(arrayOf("objection", t["extract_objection"], "", extract.objection))
                    }
                    Text(t["extract_suggestion_note"], color = c.faint, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
                    rows.forEach { row ->
                        val key = row[0]
                        val on = checks[key] ?: true
                        Surface(shape = RoundedCornerShape(12.dp), color = c.sunk, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Box(
                                    Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                                        .background(if (on) c.ink else Color.Transparent)
                                        .border(2.dp, if (on) c.ink else c.edge, RoundedCornerShape(6.dp))
                                        .clickable { checks[key] = !on },
                                    contentAlignment = Alignment.Center,
                                ) { if (on) Icon(CheckIcon, null, tint = c.onInk, modifier = Modifier.size(13.dp)) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row[1], color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(3.dp))
                                    if (row[2].isNotBlank()) Text("${t["extract_current"]}: ${row[2]}", color = c.faint, fontSize = 12.sp, textDecoration = TextDecoration.LineThrough)
                                    Text("${t["extract_proposed"]}: ${row[3]}", color = c.ink, fontSize = 13.5.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    PrimaryButton(t["extract_apply"], enabled = true) {
                        fun sel(k: String) = (checks[k] ?: true)
                        var o = existing ?: Opportunity(id = store.newOpportunityId(), title = clientName.trim().ifBlank { t["opp_title"] }, customerName = clientName.trim())
                        if (sel("need")) o = o.copy(need = extract.need)
                        if (sel("problem")) o = o.copy(problem = extract.problem)
                        if (sel("product")) o = o.copy(product = extract.product)
                        if (sel("budget")) o = o.copy(budgetStatus = extract.budgetStatus)
                        if (sel("close")) o = o.copy(closeDate = extract.closeDate)
                        if (sel("competitor")) o = o.copy(competitor = extract.competitor)
                        if (sel("next")) o = o.copy(nextStep = extract.nextStep)
                        if (sel("objection")) o = o.copy(notes = listOf(o.notes, extract.objection).filter { it.isNotBlank() }.joinToString("\n"))
                        store.upsertOpportunity(o)   // single operation — no half-applied state
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun activityIcon(type: ActivityType): ImageVector = when (type) {
    ActivityType.CALL -> AppIcons.Phone
    ActivityType.EMAIL -> AppIcons.Email
    ActivityType.MEETING -> AppIcons.People
    ActivityType.MESSAGE -> AppIcons.Email
    ActivityType.NOTE -> AppIcons.Plan
}

/** Log/edit an interaction (call, meeting, message) tied to a customer and optionally a deal. */
@Composable
private fun ActivitySheet(store: Store, customer: Customer, editing: com.sales.visits.Activity?, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState()
    var type by remember { mutableStateOf(activityTypeEnum(editing?.type)) }
    var result by remember { mutableStateOf(activityResultEnum(editing?.result)) }
    var summary by remember { mutableStateOf(editing?.summary ?: "") }
    var date by remember { mutableStateOf(editing?.date?.ifBlank { todayIso() } ?: todayIso()) }
    var oppId by remember { mutableStateOf(editing?.opportunityId ?: "") }
    var showDate by remember { mutableStateOf(false) }
    // The customer's deals, so an activity can be tied to one.
    val custOpps = store.opportunities.filter {
        (it.customerId.isNotBlank() && it.customerId == customer.id) ||
            (it.customerId.isBlank() && it.customerName.trim().lowercase() == customer.name.trim().lowercase())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) t["log_activity"] else t["edit_activity"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.weight(1f))
                CircleBtn(CloseXIcon, t["done"]) { onDismiss() }
            }
            LabeledBlock(t["activity_type"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityType.values().forEach { at -> ChoiceChip(at.label(t.en), type == at) { type = at } }
                }
            }
            LabeledBlock(t["activity_result"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityResult.values().forEach { r -> ChoiceChip(r.label(t.en), result == r) { result = r } }
                }
            }
            PickerField(t["activity_date"], fullDay(date), Modifier.fillMaxWidth().padding(bottom = 15.dp)) { showDate = true }
            if (custOpps.isNotEmpty()) LabeledBlock(t["opp_link"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(t["none"], oppId.isBlank()) { oppId = "" }
                    custOpps.forEach { o -> ChoiceChip(o.title, oppId == o.id) { oppId = o.id } }
                }
            }
            LabeledBlock(t["activity_summary"]) { Input(summary, { summary = it }, t["activity_summary_hint"]) }

            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = {
                    store.upsertActivity(
                        com.sales.visits.Activity(
                            id = editing?.id ?: store.newActivityId(),
                            customerId = customer.id, customerName = customer.name,
                            opportunityId = oppId, contactId = editing?.contactId.orEmpty(),
                            type = type.name, date = date, time = editing?.time.orEmpty(),
                            summary = summary.trim(), result = result.name, by = editing?.by.orEmpty(),
                            createdAt = editing?.createdAt?.ifBlank { todayIso() } ?: todayIso(),
                        )
                    )
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp), color = c.ink, modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(t["save"], color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            if (editing != null) {
                TextButton(onClick = { store.deleteActivity(editing.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Delete, null, tint = c.lost, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t["delete"], color = c.lost, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (showDate) DatePick(date) { date = it; showDate = false }
}

private fun quoteNumText(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

/** Full-screen quote editor: line items, tax, statuses, revisions and convert-to-order (plan 3.2). */
@Composable
private fun QuoteEditor(store: Store, customer: Customer, editing: Quote?, onClose: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val isLockedOriginal = editing != null && quoteStatusEnum(editing.status) != QuoteStatus.DRAFT
    var status by remember { mutableStateOf(quoteStatusEnum(editing?.status)) }
    var currency by remember { mutableStateOf((editing?.currency ?: "").ifBlank { store.defaultCurrency }) }
    var taxPct by remember { mutableStateOf(editing?.taxPct?.takeIf { it > 0.0 }?.let { quoteNumText(it) } ?: "") }
    var expiry by remember { mutableStateOf(editing?.expiryDate ?: "") }
    var notes by remember { mutableStateOf(editing?.notes ?: "") }
    val lines = remember { mutableStateListOf<QuoteLine>().apply { addAll(editing?.lines.orEmpty()) } }
    var showExpiry by remember { mutableStateOf(false) }
    var pickProduct by remember { mutableStateOf(false) }
    val number = remember { editing?.number?.ifBlank { store.nextQuoteNumber() } ?: store.nextQuoteNumber() }
    BackHandler { onClose() }

    fun buildQuote(id: String): Quote = Quote(
        id = id, number = number, customerId = customer.id, customerName = customer.name,
        opportunityId = editing?.opportunityId.orEmpty(), contactId = editing?.contactId.orEmpty(),
        currency = currency.trim(), status = status.name, version = editing?.version ?: 1,
        taxPct = taxPct.toDoubleOrNull() ?: 0.0, lines = lines.toList(), notes = notes.trim(),
        attachmentPath = editing?.attachmentPath.orEmpty(), orderId = editing?.orderId.orEmpty(),
        createdAt = editing?.createdAt?.ifBlank { todayIso() } ?: todayIso(),
        sentDate = editing?.sentDate.orEmpty(), expiryDate = expiry,
    )
    val preview = buildQuote(editing?.id ?: "preview")
    val contentChanged = editing != null && (
        lines.toList() != editing.lines ||
            (taxPct.toDoubleOrNull() ?: 0.0) != editing.taxPct || currency.trim() != editing.currency
        )

    fun save() {
        var saved = if (isLockedOriginal && contentChanged) {
            // Editing a sent quote's contents → save a NEW revision; the original stays as received.
            buildQuote(store.newQuoteId()).copy(version = (editing!!.version + 1), status = QuoteStatus.DRAFT.name, orderId = "", sentDate = "")
        } else {
            buildQuote(editing?.id ?: store.newQuoteId())
        }
        // Stamp the sent date the first time it's marked sent.
        if (saved.statusEnum() == QuoteStatus.SENT && saved.sentDate.isBlank()) saved = saved.copy(sentDate = todayIso())
        store.upsertQuote(saved)
        if (saved.statusEnum() == QuoteStatus.SENT) store.onQuoteSent(saved)   // 3.4: follow-up + stage advance
        onClose()
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onClose() }
            Spacer(Modifier.width(12.dp))
            Text(number, color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, modifier = Modifier.weight(1f))
            CircleBtn(CheckIcon, t["save"]) { save() }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (isLockedOriginal) {
                Surface(shape = RoundedCornerShape(12.dp), color = c.sunk, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(t["quote_revision_note"], Modifier.padding(12.dp), color = c.ink2, fontSize = 12.5.sp, lineHeight = 17.sp)
                }
            }
            LabeledBlock(t["quote_status"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuoteStatus.values().forEach { s -> ChoiceChip(s.label(t.en), status == s) { status = s } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field(t["currency"], currency, { currency = it }, t["currency_hint"]) }
                Box(Modifier.weight(1f)) { LabeledBlock(t["quote_tax"]) { Input(taxPct, { taxPct = it.filter { ch -> ch.isDigit() || ch == '.' } }, "0", kb = KeyboardType.Number) } }
            }
            PickerField(t["quote_expiry"], if (expiry.isBlank()) t["none"] else fullDay(expiry), Modifier.fillMaxWidth().padding(bottom = 15.dp)) { showExpiry = true }

            // Line items
            Text(t["quote_lines"], color = c.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            lines.forEachIndexed { i, ln ->
                Surface(shape = RoundedCornerShape(14.dp), color = c.sunk, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Box(Modifier.clip(CircleShape).clickable { lines.removeAt(i) }.padding(4.dp)) { Icon(CloseXIcon, t["delete"], tint = c.faint, modifier = Modifier.size(15.dp)) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Input(ln.description, { lines[i] = lines[i].copy(description = it) }, t["quote_line_desc"])
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { LabeledBlock(t["quote_qty"]) { Input(quoteNumText(ln.quantity), { lines[i] = lines[i].copy(quantity = it.filter { ch -> ch.isDigit() || ch == '.' }.toDoubleOrNull() ?: 0.0) }, "1", kb = KeyboardType.Number) } }
                            Box(Modifier.weight(1f)) { LabeledBlock(t["quote_unit_price"]) { Input(quoteNumText(ln.unitPrice), { lines[i] = lines[i].copy(unitPrice = it.filter { ch -> ch.isDigit() || ch == '.' }.toDoubleOrNull() ?: 0.0) }, "0", kb = KeyboardType.Number) } }
                            Box(Modifier.weight(1f)) { LabeledBlock(t["quote_discount"]) { Input(quoteNumText(ln.discountPct), { lines[i] = lines[i].copy(discountPct = it.filter { ch -> ch.isDigit() || ch == '.' }.toDoubleOrNull() ?: 0.0) }, "0", kb = KeyboardType.Number) } }
                            Box(Modifier.weight(1f)) { LabeledBlock(t["quote_unit_cost"]) { Input(quoteNumText(ln.unitCost), { lines[i] = lines[i].copy(unitCost = it.filter { ch -> ch.isDigit() || ch == '.' }.toDoubleOrNull() ?: 0.0) }, "0", kb = KeyboardType.Number) } }
                        }
                        Text("${t["quote_line_net"]}: ${fmtMoney(QuoteMath.lineNet(ln))} ${currency.trim()}", color = c.muted, fontSize = 12.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                ChoiceChip(t["quote_add_line"], false) { lines.add(QuoteLine(id = store.newQuoteId())) }
                ChoiceChip(t["quote_add_stock"], false) { pickProduct = true }
            }

            // Totals
            Surface(shape = RoundedCornerShape(14.dp), color = c.surface, border = androidx.compose.foundation.BorderStroke(1.dp, c.edge), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    QuoteTotalRow(t["quote_subtotal"], "${fmtMoney(QuoteMath.subtotal(preview))} ${currency.trim()}", c.ink2)
                    QuoteTotalRow("${t["quote_tax"]} (${quoteNumText(preview.taxPct)}%)", "${fmtMoney(QuoteMath.tax(preview))} ${currency.trim()}", c.ink2)
                    Spacer(Modifier.height(4.dp))
                    QuoteTotalRow(t["quote_total"], "${fmtMoney(QuoteMath.total(preview))} ${currency.trim()}", c.ink, bold = true)
                    // Profit margin — only when a cost was entered on some line (internal view; never printed for the customer).
                    if (QuoteMath.hasCost(preview)) {
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = c.edge)
                        Spacer(Modifier.height(6.dp))
                        QuoteTotalRow(t["quote_cost"], "${fmtMoney(QuoteMath.cost(preview))} ${currency.trim()}", c.muted)
                        QuoteTotalRow("${t["quote_profit"]} (${quoteNumText(QuoteMath.marginPct(preview))}%)", "${fmtMoney(QuoteMath.profit(preview))} ${currency.trim()}", if (QuoteMath.profit(preview) >= 0) c.ok else c.lost, bold = true)
                    }
                }
            }

            MultiField(t["customer_notes"], notes, { notes = it }, t["customer_notes_hint"])

            // Convert to order — explicit, one-time, only for an accepted quote.
            if (editing != null && editing.orderId.isNotBlank()) {
                Text(t["quote_order_created"], color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            } else if (editing != null && quoteStatusEnum(editing.status) == QuoteStatus.ACCEPTED) {
                Surface(onClick = { store.createOrderFromQuote(editing); onClose() }, shape = RoundedCornerShape(14.dp), color = c.ink, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(t["quote_make_order"], color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // Export the quote as a PDF to share with the customer (cost/profit stay internal, never printed).
            if (lines.isNotEmpty()) {
                Surface(
                    onClick = {
                        val f = QuotePdf.build(ctx, preview, customer, company = "", en = t.en)
                        if (f != null) QuotePdf.share(ctx, f, t["quote_export_pdf"])
                        else Toast.makeText(ctx, t["quote_pdf_failed"], Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(14.dp), color = c.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(50.dp),
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Report, null, tint = c.ink2, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t["quote_export_pdf"], color = c.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            if (editing != null) {
                TextButton(onClick = { store.deleteQuote(editing.id); onClose() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Delete, null, tint = c.lost, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t["delete"], color = c.lost, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showExpiry) DatePick(expiry.ifBlank { todayIso() }) { expiry = it; showExpiry = false }
    if (pickProduct) QuotePickProductSheet(store, onPick = { item ->
        // Snapshot the current price — a later inventory change won't alter this quote.
        lines.add(QuoteLine(id = store.newQuoteId(), description = item.name, productId = item.id, quantity = 1.0, unitPrice = item.price))
        pickProduct = false
    }, onDismiss = { pickProduct = false })
}

@Composable
private fun QuoteTotalRow(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, fontSize = if (bold) 15.sp else 13.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = if (bold) 15.sp else 13.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold)
    }
}

/** Simple stock picker for adding a quote line at the item's current price. */
@Composable
private fun QuotePickProductSheet(store: Store, onPick: (InventoryItem) -> Unit, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var q by remember { mutableStateOf("") }
    val items = store.inventory.filter { q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(t["quote_pick_product"], fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(vertical = 6.dp))
            Input(q, { q = it }, t["search_items"])
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                Text(t["no_results"], color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { item ->
                        Surface(onClick = { onPick(item) }, shape = RoundedCornerShape(12.dp), color = c.sunk, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (item.hasPrice) Text("${fmtMoney(item.price)}${if (item.currency.isNotBlank()) " " + item.currency else ""}", color = c.muted, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderSheet(store: Store, customerName: String, editing: Order?, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState()
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var amount by remember { mutableStateOf(editing?.amount?.takeIf { it > 0.0 }?.let { fmtMoney(it) } ?: "") }
    var currency by remember { mutableStateOf((editing?.currency ?: "").ifBlank { store.defaultCurrency }) }
    var status by remember { mutableStateOf(editing?.status ?: "DELIVERED") }
    var date by remember { mutableStateOf(editing?.date ?: todayIso()) }
    var notes by remember { mutableStateOf(editing?.notes ?: "") }
    var showDate by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.edge))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) t["new_order"] else t["edit_order"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.weight(1f))
                CircleBtn(CloseXIcon, t["done"]) { onDismiss() }
            }
            LabeledBlock(t["order_items"]) { Input(title, { title = it }, t["order_items_hint"]) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { LabeledBlock(t["order_amount"]) { Input(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' } }, "0", kb = KeyboardType.Number) } }
                Box(Modifier.weight(1f)) { LabeledBlock(t["currency"]) { Input(currency, { currency = it }, t["currency_hint"]) } }
            }
            LabeledBlock(t["order_status"]) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(t["order_delivered"], status == "DELIVERED") { status = "DELIVERED" }
                    ChoiceChip(t["order_ordered"], status == "ORDERED") { status = "ORDERED" }
                }
            }
            PickerField(t["order_date"], fullDay(date), Modifier.fillMaxWidth().padding(bottom = 15.dp)) { showDate = true }
            LabeledBlock(t["customer_notes"]) { Input(notes, { notes = it }, t["customer_notes_hint"]) }
            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = {
                    store.upsertOrder(Order(
                        id = editing?.id ?: store.newOrderId(), customerName = customerName,
                        title = title.trim(), amount = amount.toDoubleOrNull() ?: 0.0, currency = currency.trim(),
                        date = date, status = status, notes = notes.trim(),
                    ))
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp), color = c.ink, modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(t["save"], color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            if (editing != null) {
                TextButton(onClick = { store.deleteOrder(editing.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Delete, null, tint = c.lost, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t["delete"], color = c.lost, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (showDate) DatePick(date) { date = it; showDate = false }
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
            CircleBtn(CheckIcon, t["done"]) { done() }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            Field(t["customer_name"], form.name, { form.name = it }, t["client_name_hint"])
            Field(t["industry"], form.industry, { form.industry = it }, t["industry_hint"])
            run {
                val used = remember(store.customers) {
                    store.customers.map { it.industry.trim() }.filter { it.isNotBlank() }.distinct()
                }
                val typed = form.industry.trim()
                val suggestions = used.filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }.take(8)
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { s -> ChoiceChip(s, false) { form.industry = s } }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
            // Relationship status — the prospect-bank switch.
            LabeledBlock(t["relationship"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RelationshipStatus.values().forEach { rs ->
                        ChoiceChip(rs.label(t.en), form.relationship == rs.name) { form.relationship = rs.name }
                    }
                }
            }
            ContactsEditor(form.contacts)
            Field(t["address"], form.address, { form.address = it }, t["address_hint"])
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field(t["city"], form.city, { form.city = it }, t["city_hint"]) }
                Box(Modifier.weight(1f)) { Field(t["region"], form.region, { form.region = it }, t["region_hint"]) }
            }
            LabeledBlock(t["location_url"]) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Input(form.locationUrl, { form.locationUrl = it }, t["location_url_hint"], kb = KeyboardType.Uri) }
                    if (form.locationUrl.isNotBlank()) {
                        Box(
                            Modifier.height(52.dp).clip(RoundedCornerShape(12.dp)).background(c.ink)
                                .clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(form.locationUrl.trim()))) } }
                                .padding(horizontal = 16.dp), contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Place, t["open_location"], tint = c.onInk, modifier = Modifier.size(19.dp)) }
                    }
                }
            }
            Field(t["website"], form.website, { form.website = it }, t["website_hint"])
            Field(t["source"], form.source, { form.source = it }, t["source_hint"])
            Field(t["equipment"], form.equipment, { form.equipment = it }, t["equipment_hint"])
            Field(t["apps_field"], form.apps, { form.apps = it }, t["apps_hint"])
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
            if (editing != null) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Delete, null, tint = c.lost, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t["delete_customer"], color = c.lost, fontWeight = FontWeight.Bold)
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
    var type by mutableStateOf<VisitType?>(v?.typeEnum())   // null = purpose not chosen yet
    var outcome by mutableStateOf(v?.outcomeEnum() ?: Outcome.NONE)
    // Two separate language notes. Older visits stored one combined note → split it by script so the
    // Arabic container shows only Arabic and the English container only English.
    var notesAr by mutableStateOf((v?.notesAr ?: "").ifBlank { v?.notes?.let { noteInLanguage(it, false, fallbackWhole = false) }.orEmpty() })
    var notesEn by mutableStateOf((v?.notesEn ?: "").ifBlank { v?.notes?.let { noteInLanguage(it, true, fallbackWhole = false) }.orEmpty() })
    var next by mutableStateOf(v?.next ?: "")
    var nextDate by mutableStateOf(v?.nextDate ?: "")
    val checklist = mutableStateListOf<ChecklistItem>().apply { addAll(v?.checklist.orEmpty()) }
    val images = mutableStateListOf<String>().apply { addAll(v?.images.orEmpty()) }
    fun toVisit(id: String) = Visit(
        id = id, client = client.trim(), contact = contact.trim(), phone = phone.trim(),
        address = address.trim(), date = date, time = time, type = (type ?: VisitType.FIRST_VISIT).name, outcome = outcome.name,
        // Legacy `notes` kept as the two languages combined (used by search and as a fallback).
        notes = listOf(notesAr.trim(), notesEn.trim()).filter { it.isNotBlank() }.joinToString("\n\n"),
        notesAr = notesAr.trim(), notesEn = notesEn.trim(),
        next = next.trim(), nextDate = nextDate,
        checklist = checklist.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotBlank() },
        images = images.toList(),
    )
}

@Composable
fun CircleBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val c = LocalSales.current
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (c.dark) c.surface else Color.White)
            .border(1.dp, c.edge, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = c.ink, modifier = Modifier.size(19.dp)) }
}

/** Appearance row: a light↔dark switch that plays the "poured" circular reveal from the switch. */
@Composable
private fun ThemeSwitchRow() {
    val c = LocalSales.current
    val t = LocalL.current
    val reveal = LocalThemeReveal.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Palette, null, tint = c.ink2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(t["appearance"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(SunIcon, null, tint = if (!reveal.dark) c.ink else c.faint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = reveal.dark,
            onCheckedChange = { reveal.toggle(pos) },
            modifier = Modifier.onGloballyPositioned { pos = it.boundsInWindow().center },
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onInk, checkedTrackColor = c.ink,
                uncheckedThumbColor = c.surface, uncheckedTrackColor = c.edge,
                uncheckedBorderColor = c.edge,
            ),
        )
        Spacer(Modifier.width(6.dp))
        Icon(MoonIcon, null, tint = if (reveal.dark) c.ink else c.faint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun EditorField(value: String, onChange: (String) -> Unit, hint: String, textSize: TextUnit, weight: FontWeight, minLines: Int, error: Boolean = false) {
    val c = LocalSales.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(color = c.ink, fontSize = textSize, fontWeight = weight, fontFamily = LocalAppFont.current, lineHeight = textSize * 1.45f),
        cursorBrush = SolidColor(c.ink),
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        decorationBox = { inner ->
            if (value.isEmpty()) Text(hint, color = if (error) c.lost else c.faint, fontSize = textSize, fontWeight = weight, lineHeight = textSize * 1.45f)
            inner()
        },
    )
}

@Composable
private fun ColumnScope.ChecklistSection(items: MutableList<ChecklistItem>) {
    val c = LocalSales.current
    val t = LocalL.current
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = { items.add(ChecklistItem()) }, shape = RoundedCornerShape(10.dp), color = c.sunk) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Plan, null, tint = c.ink2, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(t["checklist"], color = c.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    items.forEachIndexed { i, item ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (item.done) c.ink else Color.Transparent)
                    .border(1.5.dp, if (item.done) c.ink else c.edge, RoundedCornerShape(6.dp))
                    .clickable { items[i] = items[i].copy(done = !items[i].done) },
                contentAlignment = Alignment.Center,
            ) { if (item.done) Icon(CheckIcon, null, tint = c.onInk, modifier = Modifier.size(14.dp)) }
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = item.text, onValueChange = { items[i] = items[i].copy(text = it) },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = if (item.done) c.muted else c.ink, fontSize = 15.sp, fontFamily = LocalAppFont.current,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                ),
                cursorBrush = SolidColor(c.ink),
                decorationBox = { inner ->
                    if (item.text.isEmpty()) Text(t["checklist_hint"], color = c.faint, fontSize = 15.sp)
                    inner()
                },
            )
            Box(Modifier.clip(CircleShape).clickable { items.removeAt(i) }.padding(6.dp), contentAlignment = Alignment.Center) {
                Icon(CloseXIcon, null, tint = c.faint, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun ColumnScope.ImagesSection(images: MutableList<String>) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) copyImageToFiles(ctx, uri)?.let { images.add(it) }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, shape = RoundedCornerShape(10.dp), color = c.sunk) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Image, null, tint = c.ink2, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(t["add_image"], color = c.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (images.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            images.toList().forEach { path ->
                val bmp = remember(path) { loadImageBitmap(path) }
                Box(Modifier.size(96.dp)) {
                    if (bmp != null) {
                        Image(bmp, null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(c.sunk))
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(c.ink)
                            .clickable { images.remove(path); deleteImageFile(path) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(CloseXIcon, t["delete"], tint = c.onInk, modifier = Modifier.size(13.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MdToolBtn(label: String, bold: Boolean = false, onClick: () -> Unit) {
    val c = LocalSales.current
    Surface(onClick = onClick, shape = RoundedCornerShape(9.dp), color = c.sunk) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Text(label, color = c.ink2, fontSize = 15.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold)
        }
    }
}

/** A note field with a small markdown toolbar (headings / bold / bullet), bound to a value/setter. */
@Composable
private fun ColumnScope.NotesEditor(value: String, onValue: (String) -> Unit, hint: String) {
    val c = LocalSales.current
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    // Keep in sync when the AI (or a language split) writes into the bound value.
    LaunchedEffect(value) { if (value != tfv.text) tfv = TextFieldValue(value) }
    fun applyMd(prefix: String, suffix: String = "", lineLevel: Boolean = false) {
        val text = tfv.text
        val sel = tfv.selection
        tfv = if (lineLevel) {
            val ls = if (sel.start <= 0) 0 else text.lastIndexOf('\n', sel.start - 1).let { if (it < 0) 0 else it + 1 }
            tfv.copy(text = text.substring(0, ls) + prefix + text.substring(ls), selection = TextRange(sel.start + prefix.length))
        } else if (sel.collapsed) {
            tfv.copy(text = text.substring(0, sel.start) + prefix + suffix + text.substring(sel.start), selection = TextRange(sel.start + prefix.length))
        } else {
            val selected = text.substring(sel.start, sel.end)
            tfv.copy(text = text.substring(0, sel.start) + prefix + selected + suffix + text.substring(sel.end), selection = TextRange(sel.end + prefix.length + suffix.length))
        }
        onValue(tfv.text)
    }
    BasicTextField(
        value = tfv, onValueChange = { tfv = it; onValue(it.text) },
        textStyle = TextStyle(color = c.ink, fontSize = 16.sp, fontFamily = LocalAppFont.current, lineHeight = 23.sp),
        cursorBrush = SolidColor(c.ink), modifier = Modifier.fillMaxWidth(), minLines = 4,
        decorationBox = { inner ->
            if (tfv.text.isEmpty()) Text(hint, color = c.faint, fontSize = 16.sp, lineHeight = 23.sp)
            inner()
        },
    )
    // Markdown toolbar hidden for now (code kept). Flip to re-enable after redesign.
    @Suppress("SimplifyBooleanWithConstants")
    if (false) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MdToolBtn("H₁") { applyMd("# ", lineLevel = true) }
            MdToolBtn("H₂") { applyMd("## ", lineLevel = true) }
            MdToolBtn("B", bold = true) { applyMd("**", "**") }
            MdToolBtn("•") { applyMd("- ", lineLevel = true) }
        }
    }
}

/** One language container (Arabic-only / English-only) for the visit note. */
@Composable
private fun ColumnScope.LangNoteCard(label: String, value: String, onValue: (String) -> Unit, hint: String) {
    val c = LocalSales.current
    Surface(
        shape = RoundedCornerShape(18.dp), color = c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            NotesEditor(value, onValue, hint)
        }
    }
}

@Composable
private fun VisitEditor(store: Store, editing: Visit?, onClose: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val form = remember(editing?.id ?: "new") { VisitForm(editing) }
    var infoOpen by remember { mutableStateOf(false) }
    var quickOpen by remember { mutableStateOf(false) }
    var quickInvalid by remember { mutableStateOf(false) }
    var quickShakeTrigger by remember { mutableIntStateOf(0) }
    var clientMissing by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var templatesOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerInitial by remember { mutableStateOf("") }
    // The customer record this visit is linked to. Editing it in the info sheet writes back to
    // the customer database, so the visit and the customers page share one source of truth.
    var custForm by remember(editing?.id ?: "new") {
        mutableStateOf(CustomerForm(store.customerFor(editing?.client.orEmpty())).also {
            if (it.name.isBlank()) it.name = editing?.client.orEmpty()
        })
    }
    val quickShake = remember { Animatable(0f) }
    val density = LocalDensity.current
    // 4.2 structured extraction state
    var extractSheet by remember { mutableStateOf(false) }
    var extractLoading by remember { mutableStateOf(false) }
    var extractError by remember { mutableStateOf<String?>(null) }
    var extract by remember { mutableStateOf<AiFormatter.VisitExtract?>(null) }
    var extractConsent by remember { mutableStateOf(false) }

    LaunchedEffect(quickShakeTrigger) {
        if (quickShakeTrigger == 0) return@LaunchedEffect
        quickShake.snapTo(0f)
        quickShake.animateTo(0f, keyframes {
            durationMillis = 360
            -10f at 45
            10f at 90
            -8f at 140
            8f at 190
            -4f at 240
            4f at 290
        })
    }
    LaunchedEffect(form.type) {
        if (form.type != null) quickInvalid = false
    }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(ctx) }
    // 0 = idle, 1 = recording, 2 = sending the recording to the AI
    var recState by remember { mutableStateOf(0) }
    val levels = remember { mutableStateListOf<Float>() }   // live waveform samples
    var recSeconds by remember { mutableStateOf(0) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    LaunchedEffect(recState) {
        if (recState == 1) {
            levels.clear(); recSeconds = 0
            val startMs = System.currentTimeMillis()
            while (recState == 1) {
                levels.add((recorder.amplitude() / 9000f).coerceIn(0.05f, 1f))
                if (levels.size > 40) levels.removeAt(0)
                recSeconds = ((System.currentTimeMillis() - startMs) / 1000).toInt()
                delay(85)
            }
        }
    }

    fun processRecording(file: java.io.File) {
        if (store.apiKey.isBlank()) {
            recState = 0; file.delete()
            Toast.makeText(ctx, t["need_key"], Toast.LENGTH_LONG).show(); return
        }
        recState = 2
        scope.launch {
            try {
                val r = AiFormatter.formatAudio(
                    store.apiKey, store.aiModel, file.readBytes(), "audio/aac",
                    inventory = store.inventoryContext(), taskLangEnglish = (store.lang == "en"),
                )
                // Fill the two separate language containers.
                form.notesAr = r.ar
                form.notesEn = r.en
                // Auto-create any follow-up tasks the agent inferred (on the visit's day).
                if (r.tasks.isNotEmpty()) {
                    r.tasks.forEach { task ->
                        store.addTask(task.title, task.client.ifBlank { form.client }, form.date, source = TaskSource.AI)
                    }
                    Toast.makeText(ctx, "${t["tasks_added_prefix"]} ${r.tasks.size}", Toast.LENGTH_LONG).show()
                }
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
    fun startRec() {
        if (recState != 0) return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (recorder.start()) recState = 1 else Toast.makeText(ctx, t["ai_failed"], Toast.LENGTH_SHORT).show()
        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    // Finish + send the recording to the AI (the right button).
    fun sendRec() {
        if (recState != 1) return
        val f = recorder.stop()
        recState = 0
        if (f != null) processRecording(f)
        else Toast.makeText(ctx, t["rec_failed"], Toast.LENGTH_SHORT).show()
    }
    // Discard the recording without sending (the left button).
    fun cancelRec() {
        if (recState != 1) return
        recorder.cancel()
        recState = 0
    }

    // Any user-authored content on the visit (so an all-empty new visit can just close).
    fun hasAnyInput(): Boolean =
        form.client.isNotBlank() || form.notesAr.isNotBlank() || form.notesEn.isNotBlank() ||
            form.images.isNotEmpty() || form.checklist.any { it.text.isNotBlank() } ||
            form.type != null

    // Mandatory to save: a client and a chosen visit purpose.
    fun canSave(): Boolean =
        form.client.isNotBlank() && form.type != null

    fun persist() {
        // Keep the linked customer in sync, then snapshot its details onto the visit.
        if (custForm.name.isNotBlank()) {
            store.upsertCustomer(custForm.toCustomer())
            val first = custForm.contacts.firstOrNull()
            form.contact = first?.name.orEmpty()
            form.phone = first?.phone.orEmpty()
            form.address = custForm.address
        }
        store.upsert(form.toVisit(editing?.id ?: uid()))
    }

    // Single handler for both the back button and system back — never silently loses data.
    fun done() {
        when {
            !hasAnyInput() -> onClose()                 // empty new visit: just leave, no shake
            canSave() -> { persist(); onClose() }       // complete: save and leave
            else -> {
                // Incomplete but has data: highlight what's missing and offer a safe way out.
                if (form.client.isBlank()) clientMissing = true
                else if (form.type == null) { quickInvalid = true; quickShakeTrigger++ }
                confirmDiscard = true
            }
        }
    }
    BackHandler { done() }

    fun runExtract() {
        val note = form.notesEn.ifBlank { form.notesAr }
        when {
            store.apiKey.isBlank() -> { Toast.makeText(ctx, t["need_key"], Toast.LENGTH_LONG).show(); return }
            note.isBlank() -> { Toast.makeText(ctx, t["extract_need_note"], Toast.LENGTH_LONG).show(); return }
            !store.aiConsent -> { extractConsent = true; return }
        }
        extractSheet = true; extractLoading = true; extract = null; extractError = null
        scope.launch {
            try { extract = AiFormatter.extractVisit(store.apiKey, store.aiModel, note) }
            catch (e: Exception) { extractError = e.message ?: "error" }
            finally { extractLoading = false }
        }
    }

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
            if (editing != null) {
                Spacer(Modifier.width(8.dp))
                Box {
                    CircleBtn(AppIcons.More, t["more"]) { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(t["delete_visit"], color = c.lost) },
                            onClick = { menuOpen = false; store.delete(editing.id); onClose() },
                        )
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(6.dp))
            EditorField(form.client, { v ->
                if ("@" in v) {
                    val seed = v.replace("@", "").trim()
                    pickerInitial = seed; form.client = seed; pickerOpen = true
                    if (seed.isNotBlank()) clientMissing = false
                } else {
                    form.client = v
                    custForm.name = v
                    if (v.isNotBlank()) clientMissing = false
                    if (v.isBlank()) { form.contact = ""; form.phone = ""; form.address = "" }
                }
            }, t["client_name_hint"], 24.sp, FontWeight.ExtraBold, 1, error = clientMissing)
            if (clientMissing) {
                Text(t["client_required"], color = c.lost, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(10.dp))
            // Writing tools (templates, checklist, images, markdown toolbar) are hidden for now —
            // the code stays; flip this to re-enable once we redesign them.
            val showWritingTools = false

            // Two separate containers: Arabic-only and English-only, so each language is easy to read/edit.
            LangNoteCard(t["note_ar"], form.notesAr, { form.notesAr = it }, t["notes_hint"])
            LangNoteCard(t["note_en"], form.notesEn, { form.notesEn = it }, t["notes_hint"])

            // Purpose of visit — a mandatory container right under the English note. Tap to pick.
            Surface(
                onClick = { quickOpen = true },
                shape = RoundedCornerShape(18.dp),
                color = c.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (quickInvalid) c.lost else c.edge),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    .graphicsLayer { translationX = quickShake.value * density.density },
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(t["purpose"], color = if (quickInvalid) c.lost else c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            form.type?.label(t.en) ?: t["purpose_desc"],
                            color = if (form.type != null) c.ink else c.faint,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                        )
                        Icon(if (t.en) AppIcons.ArrowForward else AppIcons.ArrowBack, null, tint = c.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Extract structured deal facts from the note (AI) — review before applying (4.2).
            if (form.notesAr.isNotBlank() || form.notesEn.isNotBlank()) {
                Surface(
                    onClick = { runExtract() },
                    shape = RoundedCornerShape(14.dp), color = c.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.ink),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Chart, null, tint = c.ink, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t["extract_details"], color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Quick note templates — tap to drop a snippet into the matching-language container.
            if (showWritingTools) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = { templatesOpen = true }, shape = RoundedCornerShape(999.dp), color = c.sunk,
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Add, null, tint = c.ink2, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(t["templates"], color = c.ink2, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                store.noteTemplates.forEach { tpl ->
                    Surface(
                        onClick = {
                            if (isArabicText(tpl)) form.notesAr = if (form.notesAr.isBlank()) tpl else form.notesAr + "\n" + tpl
                            else form.notesEn = if (form.notesEn.isBlank()) tpl else form.notesEn + "\n" + tpl
                        },
                        shape = RoundedCornerShape(999.dp), color = c.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
                    ) {
                        Text(
                            tpl, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = c.ink2, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (showWritingTools) {
                ChecklistSection(form.checklist)
                ImagesSection(form.images)
            }

            Spacer(Modifier.height(120.dp))
        }
    }
        // While recording, the circular button expands into a WhatsApp-style bar with a
        // live waveform + timer. Otherwise it's a circular mic FAB at the bottom-end
        // (right in English / left in Arabic). Tap to record, tap again to stop.
        // The recording pill grows out of the mic button (cancel on the left, send on the right).
        androidx.compose.animation.AnimatedVisibility(
            visible = recState == 1,
            // Grows out from and shrinks back to the centre — the "tab pill" behaviour.
            enter = androidx.compose.animation.expandHorizontally(tween(320, easing = FastOutSlowInEasing), expandFrom = Alignment.CenterHorizontally) +
                androidx.compose.animation.fadeIn(tween(220)),
            exit = androidx.compose.animation.shrinkHorizontally(tween(300, easing = FastOutSlowInEasing), shrinkTowards = Alignment.CenterHorizontally) +
                androidx.compose.animation.fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            RecordingBar(levels, recSeconds, onCancel = { cancelRec() }, onSend = { sendRec() })
        }
        // After sending: the pill shrinks into a small "solving orb" while the AI formats the note.
        androidx.compose.animation.AnimatedVisibility(
            visible = recState == 2,
            enter = androidx.compose.animation.expandHorizontally(tween(300, easing = FastOutSlowInEasing), expandFrom = Alignment.CenterHorizontally) +
                androidx.compose.animation.fadeIn(tween(220)),
            exit = androidx.compose.animation.shrinkHorizontally(tween(260, easing = FastOutSlowInEasing), shrinkTowards = Alignment.CenterHorizontally) +
                androidx.compose.animation.fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            SolvingOrbPill(t["polishing"])
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = recState == 0,
            enter = androidx.compose.animation.scaleIn(tween(220)) + androidx.compose.animation.fadeIn(tween(200)),
            exit = androidx.compose.animation.scaleOut(tween(160)) + androidx.compose.animation.fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Box(
                Modifier.navigationBarsPadding().padding(end = 22.dp, bottom = 26.dp)
                    .size(56.dp).clip(CircleShape).background(c.ink)
                    .clickable { startRec() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Mic, t["voice_input"], tint = c.onInk, modifier = Modifier.size(26.dp))
            }
        }
    }

    if (quickOpen) PurposeSheet(form) { quickOpen = false }
    if (extractConsent) AlertDialog(
        onDismissRequest = { extractConsent = false },
        confirmButton = { TextButton(onClick = { store.grantAiConsent(); extractConsent = false; runExtract() }) { Text(t["agree"]) } },
        dismissButton = { TextButton(onClick = { extractConsent = false }) { Text(t["cancel"], color = c.muted) } },
        title = { Text(t["ai_consent_title"]) }, text = { Text(t["ai_consent_desc"]) }, containerColor = c.surface,
    )
    if (extractSheet) ExtractReviewSheet(
        store = store, clientName = form.client, loading = extractLoading, error = extractError, extract = extract,
        onRetry = { runExtract() }, onDismiss = { extractSheet = false },
    )
    if (infoOpen) InfoSheet(store, form, custForm, editing != null,
        onDelete = { editing?.let { store.delete(it.id) }; onClose() },
        onDismiss = { infoOpen = false })
    if (showDate) DatePick(form.date) { form.date = it; showDate = false }
    if (pickerOpen) CustomerPickerSheet(
        store, pickerInitial,
        onPick = { name ->
            form.client = name
            if (name.isNotBlank()) clientMissing = false
            // Link the whole customer record so the info sheet edits (and mirrors) it live.
            val existing = store.customerFor(name)
            custForm = CustomerForm(existing).also { if (it.name.isBlank()) it.name = name }
            existing?.let { cust ->
                val first = cust.contacts.firstOrNull()
                form.contact = first?.name ?: cust.contact
                form.phone = first?.phone?.takeIf { it.isNotBlank() } ?: cust.phone
                form.address = cust.address
            }
            pickerOpen = false
        },
        onDismiss = { pickerOpen = false },
    )

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(t["discard"], color = c.lost) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(t["keep_editing"], color = c.muted) } },
            title = { Text(t["discard_q"]) },
            text = { Text(if (form.client.isBlank()) t["discard_no_client"] else t["discard_desc"]) },
            containerColor = c.surface,
        )
    }

    if (templatesOpen) TemplatesSheet(store) { templatesOpen = false }
}

/** Manage the reusable quick note templates (add / delete). */
@Composable
private fun TemplatesSheet(store: Store, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState()
    var draft by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.edge))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t["templates"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.weight(1f))
                CircleBtn(CloseXIcon, t["done"]) { onDismiss() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Input(draft, { draft = it }, t["add_template_hint"]) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(c.ink)
                        .clickable { store.addTemplate(draft); draft = "" },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Add, t["add"], tint = c.onInk, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            store.noteTemplates.forEach { tpl ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tpl, color = c.ink2, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(CircleShape).clickable { store.removeTemplate(tpl) }.padding(6.dp)) {
                        Icon(AppIcons.Delete, t["delete"], tint = c.lost, modifier = Modifier.size(17.dp))
                    }
                }
                HorizontalDivider(color = c.edge)
            }
        }
    }
}

/** Bottom sheet to pick an existing customer (or add a new one) for a visit's client field. */
@Composable
private fun CustomerPickerSheet(store: Store, initialQuery: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    // Apple-Maps style: opens partially, drag up to expand; the keyboard lifts it (imePadding).
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var query by remember { mutableStateOf(initialQuery) }
    val focus = remember { FocusRequester() }
    // Auto-focus so the keyboard opens and you can type straight away.
    LaunchedEffect(Unit) { delay(180); runCatching { focus.requestFocus() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.edge))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).imePadding()) {
            // Prominent search bar
            TextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text(t["search_customers"], color = c.faint) },
                leadingIcon = { Icon(AppIcons.Search, null, tint = c.muted, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (query.isNotBlank()) Box(Modifier.clip(CircleShape).clickable { query = "" }.padding(6.dp)) {
                        Icon(CloseXIcon, t["done"], tint = c.muted, modifier = Modifier.size(16.dp))
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = c.sunk, unfocusedContainerColor = c.sunk,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedLeadingIconColor = c.muted, unfocusedLeadingIconColor = c.muted,
                    focusedTextColor = c.ink, unfocusedTextColor = c.ink, cursorColor = c.ink,
                ),
            )
            Spacer(Modifier.height(12.dp))
            val q = query.trim().lowercase()
            val matches = store.customers.filter { q.isBlank() || it.name.lowercase().contains(q) || it.contact.lowercase().contains(q) }.take(60)
            Text(
                if (q.isBlank()) t["customers"] else "${t["customers"]} · ${matches.size}",
                color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                matches.forEach { cust ->
                    Surface(onClick = { onPick(cust.name) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
                                Text(cust.name.trim().take(1).uppercase(), color = c.ink, fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cust.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = listOf(cust.industry, cust.contact).filter { it.isNotBlank() }.joinToString(" · ")
                                if (sub.isNotBlank()) Text(sub, color = c.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(if (t.en) AppIcons.ArrowForward else AppIcons.ArrowBack, null, tint = c.faint, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = c.edge.copy(alpha = 0.5f))
                }
                if (query.trim().isNotBlank() && matches.none { it.name.equals(query.trim(), ignoreCase = true) }) {
                    Spacer(Modifier.height(8.dp))
                    Surface(onClick = { onPick(query.trim()) }, shape = RoundedCornerShape(12.dp), color = c.ink, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.PersonAdd, null, tint = c.onInk, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("${t["add_customer"]}: \"${query.trim()}\"", color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PurposeSheet(form: VisitForm, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(t["purpose"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
            Text(t["purpose_desc"], color = c.muted, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 10.dp))
            VisitType.values().forEach { vt ->
                val selected = form.type == vt
                Surface(
                    onClick = { form.type = vt; onDismiss() },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) c.ink else c.sunk,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(vt.label(t.en), color = if (selected) c.onInk else c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (selected) Icon(CheckIcon, null, tint = c.onInk, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** The small "solving orb" pill shown while the AI formats the note (like the reference block). */
@Composable
private fun SolvingOrbPill(text: String) {
    val c = LocalSales.current
    Surface(shape = RoundedCornerShape(999.dp), color = c.ink, shadowElevation = 8.dp) {
        Row(
            Modifier.padding(start = 10.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SolvingOrb(Modifier.size(28.dp), c.onInk)
            Spacer(Modifier.width(10.dp))
            Text(text, color = c.onInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A rotating sphere of dots. */
@Composable
private fun SolvingOrb(modifier: Modifier, color: Color) {
    val transition = rememberInfiniteTransition(label = "orb")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "spin",
    )
    Canvas(modifier) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.minDimension / 2f * 0.86f
        val cosA = kotlin.math.cos(angle); val sinA = kotlin.math.sin(angle)
        val lat = 8; val lon = 12
        for (i in 1 until lat) {                     // skip poles to avoid clumping
            val theta = Math.PI * i / lat            // 0..PI
            val st = kotlin.math.sin(theta).toFloat(); val ct = kotlin.math.cos(theta).toFloat()
            for (j in 0 until lon) {
                val phi = (2 * Math.PI * j / lon).toFloat()
                val x = st * kotlin.math.cos(phi)
                val z = st * kotlin.math.sin(phi)
                val xr = x * cosA - z * sinA
                val zr = x * sinA + z * cosA
                val depth = (zr + 1f) / 2f            // 0 back .. 1 front
                val px = cx + xr * r
                val py = cy + ct * r
                drawCircle(color = color.copy(alpha = 0.2f + depth * 0.8f), radius = 0.6f + depth * 1.5f, center = Offset(px, py))
            }
        }
    }
}

/** WhatsApp-style recording bar: a stop button, a live scrolling waveform, and a timer. */
@Composable
private fun RecordingBar(levels: List<Float>, seconds: Int, onCancel: () -> Unit, onSend: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    // Compact pill (roughly the size of the solving-orb pill), not full width.
    Surface(shape = RoundedCornerShape(999.dp), color = c.ink, shadowElevation = 8.dp) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: cancel (discard the recording)
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c.onInk.copy(alpha = 0.14f)).clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) { Icon(CloseXIcon, t["cancel"], tint = c.onInk, modifier = Modifier.size(15.dp)) }
            Spacer(Modifier.width(8.dp))
            // Middle: live waveform (fixed compact width) — shows the most recent samples.
            Row(
                Modifier.width(84.dp).height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            ) {
                levels.takeLast(20).forEach { lvl ->
                    Box(Modifier.width(3.dp).height((4 + lvl * 22).dp).clip(RoundedCornerShape(2.dp)).background(c.onInk.copy(alpha = 0.85f)))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(formatDuration(seconds), color = c.onInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            // Right: send (finish + send to the AI)
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c.onInk).clickable(onClick = onSend),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Plane, t["ai_send"], tint = c.ink, modifier = Modifier.size(17.dp)) }
        }
    }
}

@Composable
private fun InfoSheet(store: Store, form: VisitForm, custForm: CustomerForm, editing: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    // Opens partially (about half) so it doesn't jump to the top; the user can drag it up.
    val sheetState = rememberModalBottomSheetState()
    var showTime by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.edge))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t["customer_info"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.weight(1f))
                CircleBtn(CloseXIcon, t["done"]) { onDismiss() }
            }

            // The customer info here mirrors the customers page and writes back to the same record.
            Field(t["industry"], custForm.industry, { custForm.industry = it }, t["industry_hint"])
            run {
                val used = remember(store.customers) {
                    store.customers.map { it.industry.trim() }.filter { it.isNotBlank() }.distinct()
                }
                val typed = custForm.industry.trim()
                val suggestions = used.filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }.take(8)
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { s -> ChoiceChip(s, false) { custForm.industry = s } }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
            ContactsEditor(custForm.contacts)
            Field(t["address"], custForm.address, { custForm.address = it }, t["address_hint"])
            LabeledBlock(t["location_url"]) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Input(custForm.locationUrl, { custForm.locationUrl = it }, t["location_url_hint"], kb = KeyboardType.Uri) }
                    if (custForm.locationUrl.isNotBlank()) {
                        Box(
                            Modifier.height(52.dp).clip(RoundedCornerShape(12.dp)).background(c.ink)
                                .clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(custForm.locationUrl.trim()))) } }
                                .padding(horizontal = 16.dp), contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Place, t["open_location"], tint = c.onInk, modifier = Modifier.size(19.dp)) }
                    }
                }
            }
            PickerField(t["time"], fmtTime(form.time), Modifier.fillMaxWidth()) { showTime = true }

            if (editing) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Delete, null, tint = c.lost, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t["delete_visit"], color = c.lost, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = onDismiss, shape = RoundedCornerShape(16.dp), color = c.ink,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(t["done"], color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    if (showTime) TimePick(form.time) { form.time = it; showTime = false }
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
    var offset by remember { mutableStateOf(0) }
    val vs = weekVisits(store.visits, offset)
    val clients = vs.map { it.client }.toSet().size
    val succ = vs.count { it.outcomeEnum() == Outcome.SUCCESS }
    val rEn = store.reportLang == "en"   // the report/export language (independent of the app language)

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
                    if (n > 0) BarRow(vt.label(rEn), n, maxT, c.ink)
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
                    // Compact one-line-per-visit summary; the full details live in the Excel export.
                    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(v.outcomeEnum().color(c)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.client, fontSize = 14.5.sp, color = c.ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = listOf(v.typeEnum().label(rEn), v.contact).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, fontSize = 12.5.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(v.outcomeEnum().label(rEn), fontSize = 11.5.sp, color = c.muted, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (vs.isNotEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp)).background(c.ink)
                    .clickable { ExcelExport.shareWeek(ctx, store, offset, rEn, t["export_excel"]) }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Download, null, tint = c.onInk, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(t["export_excel"], color = c.onInk, fontWeight = FontWeight.Bold)
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
private fun InsightsScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val visits = store.visits
    BackHandler(onBack = onBack)
    FrostedScaffold(header = {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t["insights_tab"], fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
        }
    }) {
        if (visits.isEmpty()) {
            EmptyState(AppIcons.Chart, t["empty_insights"], null)
        } else {
            val byClient = visits.groupingBy { it.client }.eachCount()
            val thisMonth = java.time.YearMonth.now()
            val monthBars = (5 downTo 0).map { back ->
                val m = thisMonth.minusMonths(back.toLong())
                val prefix = "%04d-%02d".format(m.year, m.monthValue)
                MonthBar(monthName(m.atDay(1)).take(3), visits.count { it.date.startsWith(prefix) })
            }
            MonthlyBarsCard(t["visits_by_month"], monthBars)
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

/** The 5 working days of the week in one rounded container, with a soft highlight on the selected day. */
@Composable
private fun WeekDayStrip(dates: List<java.time.LocalDate>, selectedDate: String, onSelect: (String) -> Unit) {
    val c = LocalSales.current
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
        shadowElevation = if (c.dark) 0.dp else 2.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dates.forEach { date ->
                val iso = date.toString()
                val sel = iso == selectedDate
                val isToday = iso == todayIso()
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(if (sel) c.sunk else Color.Transparent)
                        .clickable { onSelect(iso) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(dayName(date), maxLines = 1, fontSize = 11.sp,
                        color = if (sel) c.ink2 else c.muted, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(date.dayOfMonth.toString(), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (sel) c.ink else c.ink2)
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (isToday) c.ink else Color.Transparent))
                }
            }
        }
    }
}

/** Month grid with a dot on days that have visits or follow-ups; tap a day to open its plan. */
@Composable
private fun MonthCalendar(store: Store, selectedIso: String, onSelect: (String) -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val start = WeekConfig.startDay
    var month by remember(selectedIso) {
        mutableStateOf(runCatching { java.time.YearMonth.parse(selectedIso.take(7)) }.getOrDefault(java.time.YearMonth.now()))
    }
    val today = todayIso()
    Surface(
        shape = RoundedCornerShape(22.dp), color = c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(AppIcons.ArrowBack, null, tint = c.ink2, modifier = Modifier.size(18.dp)) }
                Text(
                    "${monthName(month.atDay(1))} ${month.year}",
                    color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(AppIcons.ArrowForward, null, tint = c.ink2, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(6.dp))
            // Weekday headers, ordered from the configured week start.
            Row(Modifier.fillMaxWidth()) {
                (0..6).forEach { i ->
                    val dow = start.plus(i.toLong())
                    Text(
                        dayName(java.time.LocalDate.of(2024, 1, 1).with(java.time.temporal.TemporalAdjusters.nextOrSame(dow))),
                        color = c.faint, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val first = month.atDay(1)
            val lead = ((first.dayOfWeek.value - start.value) + 7) % 7
            val daysInMonth = month.lengthOfMonth()
            val cells = lead + daysInMonth
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val cellIndex = r * 7 + col
                        val dayNum = cellIndex - lead + 1
                        if (dayNum in 1..daysInMonth) {
                            val date = month.atDay(dayNum)
                            val iso = date.toString()
                            val sel = iso == selectedIso
                            val isToday = iso == today
                            val hasVisit = store.visits.any { it.date == iso }
                            val overdueFollow = store.visits.any { it.next.isNotBlank() && it.nextDate == iso && iso < today }
                            val hasFollow = store.visits.any { it.next.isNotBlank() && it.nextDate == iso } || store.planFor(iso).isNotEmpty()
                            Column(
                                Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) c.ink else Color.Transparent)
                                    .border(if (isToday && !sel) 1.5.dp else 0.dp, if (isToday && !sel) c.ink else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { onSelect(iso) }
                                    .padding(vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("$dayNum", color = if (sel) c.onInk else c.ink2, fontSize = 13.sp, fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium)
                                Spacer(Modifier.height(3.dp))
                                val dotColor = when {
                                    overdueFollow -> c.lost
                                    hasVisit -> c.ok
                                    hasFollow -> if (sel) c.onInk else c.ink
                                    else -> Color.Transparent
                                }
                                Box(Modifier.size(5.dp).clip(CircleShape).background(dotColor))
                            }
                        } else {
                            Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- tasks (daily checklist) ---------------- */

/** Which part of the day a task falls in: 0 = morning, 1 = afternoon, 2 = evening (blank time → morning). */
private fun timeBucket(time: String): Int {
    val h = time.take(2).toIntOrNull() ?: return 0
    return when { h < 12 -> 0; h < 17 -> 1; else -> 2 }
}

private fun fmtDuration(min: Int, en: Boolean): String {
    if (min <= 0) return ""
    val h = min / 60; val m = min % 60
    val mn = if (en) "min" else "د"
    val hr = if (en) "h" else "س"
    return when {
        h > 0 && m > 0 -> "$h$hr $m$mn"
        h > 0 -> "$h$hr"
        else -> "$m $mn"
    }
}

@Composable
private fun TasksScreen(store: Store) {
    val c = LocalSales.current
    val t = LocalL.current
    val dates = remember(store.weekStartDay) {
        val s = weekStart(0)
        (0 until WORK_WEEK_DAYS).map { s.plusDays(it.toLong()) }
    }
    var selectedDate by remember {
        mutableStateOf(todayIso().takeIf { iso -> dates.any { it.toString() == iso } } ?: dates.first().toString())
    }
    val items = store.tasksFor(selectedDate)
    val doneCount = items.count { it.done }
    val totalMin = items.sumOf { it.minutes }
    val doneMin = items.filter { it.done }.sumOf { it.minutes }

    var sheetItem by remember { mutableStateOf<PlanItem?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var cmdOpen by remember { mutableStateOf(false) }
    var cmdConsent by remember { mutableStateOf(false) }
    fun openAssistant() {
        when {
            store.apiKey.isBlank() -> Toast.makeText(ctx, t["need_key"], Toast.LENGTH_LONG).show()
            !store.aiConsent -> cmdConsent = true
            else -> cmdOpen = true
        }
    }

    fun hoursText(): String {
        val d = doneMin / 60.0; val total = totalMin / 60.0
        fun f(x: Double) = if (x == x.toLong().toDouble()) x.toLong().toString() else "%.1f".format(x)
        return "${f(d)} ${t["of"]} ${f(total)} ${t["hrs"]}"
    }

    FrostedScaffold(header = {
        Header(t["tasks_tab"], null, mark = false) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { openAssistant() }) { Icon(AppIcons.Plan, t["assistant"], tint = c.ink) }
                StatChip(AppIcons.Check, doneCount.toString())
                if (totalMin > 0) StatChip(ClockIcon, hoursText())
            }
        }
    }) {
        WeekDayStrip(dates, selectedDate) { selectedDate = it }
        Spacer(Modifier.height(4.dp))

        if (items.isEmpty()) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Plan, null, tint = c.muted, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t["empty_tasks_title"], color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(t["empty_tasks_desc"], color = c.muted, fontSize = 12.5.sp)
                    }
                }
            }
        } else {
            val labels = listOf(t["morning"], t["afternoon"], t["evening"])
            (0..2).forEach { bucket ->
                val group = items.filter { timeBucket(it.time) == bucket }
                    .sortedWith(compareBy({ it.time.ifBlank { "99:99" } }))
                if (group.isNotEmpty()) {
                    Text(
                        labels[bucket], color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 6.dp),
                    )
                    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        group.forEach { item ->
                            TaskCard(
                                item = item,
                                onToggle = { store.toggleTask(item.id) },
                                onOpen = { sheetItem = item; sheetOpen = true },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(150.dp))
    }

    // Add / edit task
    Box(Modifier.fillMaxSize()) {
        Fab(
            onClick = { sheetItem = null; sheetOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 88.dp),
        )
    }
    if (sheetOpen) TaskSheet(store, sheetItem, selectedDate) { sheetOpen = false }
    if (cmdConsent) AlertDialog(
        onDismissRequest = { cmdConsent = false },
        confirmButton = { TextButton(onClick = { store.grantAiConsent(); cmdConsent = false; cmdOpen = true }) { Text(t["agree"]) } },
        dismissButton = { TextButton(onClick = { cmdConsent = false }) { Text(t["cancel"], color = c.muted) } },
        title = { Text(t["ai_consent_title"]) }, text = { Text(t["ai_consent_desc"]) }, containerColor = c.surface,
    )
    if (cmdOpen) CommandSheet(store) { cmdOpen = false }
}

@Composable
private fun StatChip(icon: ImageVector, text: String) {
    val c = LocalSales.current
    Surface(shape = RoundedCornerShape(999.dp), color = c.sunk) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.ink2, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun TaskCard(item: PlanItem, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val st = item.statusEnum()
    val done = st == TaskStatus.DONE
    val closed = done || st == TaskStatus.CANCELLED
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = if (closed) c.sunk else c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
        shadowElevation = if (c.dark || closed) 0.dp else 1.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                        .background(if (done) c.ink else Color.Transparent)
                        .border(2.dp, if (done) c.ink else c.edge, RoundedCornerShape(7.dp))
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) { if (done) Icon(CheckIcon, null, tint = c.onInk, modifier = Modifier.size(13.dp)) }
                Spacer(Modifier.width(12.dp))
                val label = buildAnnotatedString {
                    if (item.client.isNotBlank()) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.ink)) { append("@${item.client}") }
                        if (item.action.isNotBlank()) append(": ${item.action}")
                    } else append(item.action)
                }
                Text(
                    label, modifier = Modifier.weight(1f),
                    color = if (closed) c.muted else c.ink2, fontSize = 14.sp, lineHeight = 19.sp,
                    textDecoration = if (closed) TextDecoration.LineThrough else null,
                )
                if (item.minutes > 0) {
                    Spacer(Modifier.width(10.dp))
                    Surface(shape = RoundedCornerShape(999.dp), color = c.sunk) {
                        Text(fmtDuration(item.minutes, t.en), Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            // Status / source tags — only when there's something worth showing.
            val tags = buildList {
                if (st == TaskStatus.CANCELLED || st == TaskStatus.POSTPONED) add(st.label(t.en))
                item.source.takeIf { it.isNotBlank() && it != TaskSource.MANUAL.name }
                    ?.let { runCatching { TaskSource.valueOf(it) }.getOrNull()?.let { s -> add(s.label(t.en)) } }
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(start = 34.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag ->
                        Surface(shape = RoundedCornerShape(999.dp), color = c.sunk) {
                            Text(tag, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = c.muted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskSheet(store: Store, editing: PlanItem?, date: String, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState()
    var action by remember { mutableStateOf(editing?.action ?: "") }
    var client by remember { mutableStateOf(editing?.client ?: "") }
    var time by remember { mutableStateOf(editing?.time ?: "") }
    var minutes by remember { mutableStateOf(editing?.minutes ?: 0) }
    var status by remember { mutableStateOf(editing?.statusEnum() ?: TaskStatus.OPEN) }
    var oppId by remember { mutableStateOf(editing?.opportunityId ?: "") }
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerInit by remember { mutableStateOf("") }
    var showTime by remember { mutableStateOf(false) }
    // Deals for the chosen customer, so a follow-up can be tied to one.
    val custOpps = if (client.isBlank()) emptyList() else store.opportunities.filter { it.customerName.trim().equals(client.trim(), ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.edge))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) t["new_task"] else t["edit_task"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.weight(1f))
                CircleBtn(CloseXIcon, t["done"]) { onDismiss() }
            }

            LabeledBlock(t["task_action"]) {
                Input(action, { v ->
                    val at = v.indexOf('@')
                    if (at >= 0) { action = v.substring(0, at).trimEnd(); pickerInit = v.substring(at + 1).trim(); pickerOpen = true }
                    else action = v
                }, t["plan_hint"])
            }

            LabeledBlock(t["choose_customer"]) {
                Surface(
                    onClick = { pickerInit = client; pickerOpen = true },
                    shape = RoundedCornerShape(12.dp), color = c.sunk, modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Person, null, tint = c.ink2, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(client.ifBlank { t["choose_customer"] }, color = if (client.isBlank()) c.faint else c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (client.isNotBlank()) Box(Modifier.clip(CircleShape).clickable { client = "" }.padding(3.dp)) {
                            Icon(CloseXIcon, null, tint = c.faint, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            PickerField(t["time"], if (time.isBlank()) t["none"] else fmtTime(time), Modifier.fillMaxWidth().padding(bottom = 15.dp)) { showTime = true }

            LabeledBlock(t["duration"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 15, 30, 45, 60, 90, 120).forEach { m ->
                        ChoiceChip(if (m == 0) t["none"] else fmtDuration(m, t.en), minutes == m) { minutes = m }
                    }
                }
            }

            if (custOpps.isNotEmpty()) LabeledBlock(t["opp_link"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(t["none"], oppId.isBlank()) { oppId = "" }
                    custOpps.forEach { o -> ChoiceChip(o.title, oppId == o.id) { oppId = o.id } }
                }
            }

            // Status — cancelled/postponed are real states, not just a date change.
            LabeledBlock(t["task_status"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskStatus.values().forEach { s -> ChoiceChip(s.label(t.en), status == s) { status = s } }
                }
            }

            // Add to calendar (6.4). If Google Calendar is connected, create a real 2-way-synced event
            // (edits/deletes follow); otherwise hand off to any calendar app via an intent.
            val calCtx = LocalContext.current
            Row(Modifier.padding(bottom = 8.dp)) {
                ChoiceChip(t["add_to_calendar"], false) {
                    val title = listOf(client, action).filter { it.isNotBlank() }.joinToString(": ").ifBlank { t["tasks_tab"] }
                    val synced = editing != null && CalendarSync.isConnected(calCtx) && store.addTaskToCalendar(editing.id)
                    if (synced) Toast.makeText(calCtx, t["cal_synced"], Toast.LENGTH_SHORT).show()
                    else launchCalendarInsert(calCtx, title, action, date, time, minutes)
                }
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = {
                    if (action.isNotBlank() || client.isNotBlank()) {
                        val id = editing?.id ?: store.addTask(action, client, date, time, minutes, oppId)
                        if (editing != null) store.updateTask(editing.id, action, client, time, minutes, oppId)
                        if (id.isNotBlank()) store.setTaskStatus(id, status)
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp), color = c.ink, modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(t["save"], color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            if (editing != null) {
                TextButton(onClick = { store.deleteTask(editing.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Delete, null, tint = c.lost, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t["delete"], color = c.lost, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (pickerOpen) CustomerPickerSheet(store, pickerInit, onPick = { client = it; pickerOpen = false }, onDismiss = { pickerOpen = false })
    if (showTime) TimePick(time.ifBlank { nowHm() }) { time = it; showTime = false }
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
    // Only the 5 working days of the current week (e.g. Sunday–Thursday), per the week-start setting.
    val dates = remember(store.weekStartDay) {
        val s = weekStart(0)
        (0 until WORK_WEEK_DAYS).map { s.plusDays(it.toLong()) }
    }
    var selectedDate by remember {
        mutableStateOf(todayIso().takeIf { iso -> dates.any { it.toString() == iso } } ?: dates.first().toString())
    }
    var filter by remember { mutableStateOf(0) }   // 0 = all, 1 = to-do, 2 = done
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
    var newAction by remember { mutableStateOf("") }
    var newClient by remember { mutableStateOf("") }
    var planPickerOpen by remember { mutableStateOf(false) }
    var planPickerInit by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    fun exitSelect() { selecting = false; selected.clear() }
    BackHandler(selecting) { exitSelect() }
    var goalDialog by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<PlanItem?>(null) }
    var taskDetail by remember { mutableStateOf<PlanItem?>(null) }
    val visitsToday = store.visitsOn(selectedDate)

    FrostedScaffold(header = {
        Header(if (selectedDate == todayIso()) t["today_title"] else t["today_plan"], fullDay(selectedDate), mark = false) {
            if (selecting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected.isNotEmpty()) IconButton(onClick = { store.deletePlans(selected.toList()); exitSelect() }) {
                        Icon(AppIcons.Delete, t["delete"], tint = c.lost)
                    }
                    IconButton(onClick = { exitSelect() }) { Icon(CloseXIcon, t["done"], tint = c.ink) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showCalendar = !showCalendar }) {
                        Icon(AppIcons.Calendar, t["calendar"], tint = if (showCalendar) c.ink else c.muted)
                    }
                    IconButton(onClick = onEnableNotifications) {
                        Icon(
                            if (notificationsEnabled) AppIcons.Notifications else AppIcons.NotificationsOff,
                            t["reminders"], tint = if (notificationsEnabled) c.ink else c.muted,
                        )
                    }
                }
            }
        }
    }) {
        WeekDayStrip(dates, selectedDate) { selectedDate = it }
        if (showCalendar) MonthCalendar(store, selectedDate) { selectedDate = it }

        // Daily goal: real visits recorded vs. the target the rep sets (independent of plan size).
        Surface(
            onClick = { goalDialog = true },
            shape = RoundedCornerShape(24.dp), color = c.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t["daily_goal"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (store.dailyGoal > 0) "$visitsToday / ${store.dailyGoal} ${t["visit_count"]}" else t["set_goal_hint"],
                        color = c.muted, fontSize = 12.5.sp,
                    )
                }
                if (store.dailyGoal > 0) {
                    val reached = visitsToday >= store.dailyGoal
                    val gp = (visitsToday.toFloat() / store.dailyGoal).coerceIn(0f, 1f)
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { gp }, modifier = Modifier.size(46.dp),
                            color = if (reached) c.ok else c.ink, trackColor = c.sunk, strokeWidth = 5.dp,
                        )
                        if (reached) Icon(CheckIcon, null, tint = c.ok, modifier = Modifier.size(20.dp))
                        else Text("$visitsToday", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    Icon(AppIcons.Add, null, tint = c.faint, modifier = Modifier.size(22.dp))
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

        // Unified agenda (today only): overdue / due-today / suggestions — from tasks, visit
        // follow-ups, opportunity next-steps and quote expiries, by local rules (no AI).
        val agenda = if (selectedDate == todayIso())
            buildAgenda(todayIso(), store.tasks, store.visits, store.opportunities, store.quotes) else null
        if (agenda != null) {
            AgendaBlock(agenda, store, onEdit, onOpenTask = { taskDetail = it })
        } else if (due.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text(t["due_followups"], color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                due.forEach { visit -> DueFollowUpRow(visit) { onEdit(visit) } }
            }
        }

        Text(
            t["today_plan"], color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 2.dp),
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Input(newAction, { v ->
                    val at = v.indexOf('@')
                    if (at >= 0) { newAction = v.substring(0, at).trimEnd(); planPickerInit = v.substring(at + 1).trim(); planPickerOpen = true }
                    else newAction = v
                }, t["plan_hint"], Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(c.ink)
                        .clickable { store.addPlan(newAction, newClient, selectedDate); newAction = ""; newClient = "" },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Add, t["add"], tint = c.onInk, modifier = Modifier.size(24.dp)) }
            }
            if (newClient.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(999.dp), color = c.sunk) {
                    Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Person, null, tint = c.ink2, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(newClient, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.clip(CircleShape).clickable { newClient = "" }.padding(3.dp)) {
                            Icon(CloseXIcon, null, tint = c.faint, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
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
            // To do / Completed segmented filter, mirroring the reference design.
            Surface(
                shape = RoundedCornerShape(999.dp), color = c.sunk,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(t["seg_all"], t["seg_todo"], t["seg_done"]).forEachIndexed { i, label ->
                        val sel = filter == i
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                                .background(if (sel) c.ink else Color.Transparent)
                                .clickable { filter = i }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (sel) c.onInk else c.muted,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
            val shownItems = when (filter) {
                1 -> items.filter { !it.done }
                2 -> items.filter { it.done }
                else -> items
            }
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
            if (shownItems.isEmpty()) {
                Text(
                    if (filter == 2) t["seg_none_done"] else t["seg_none_todo"],
                    fontSize = 13.sp, color = c.muted,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                )
            }
            Column(Modifier.padding(horizontal = 18.dp)) {
                shownItems.forEachIndexed { i, item ->
                    RoadStop(
                        index = i + 1, item = item, customer = store.customerFor(item.client),
                        first = i == 0, last = i == shownItems.lastIndex,
                        selecting = selecting, selectedNow = item.id in selected,
                        onToggle = {
                            if (selecting) { if (item.id in selected) selected.remove(item.id) else selected.add(item.id) }
                            else store.togglePlan(item.id)
                        },
                        onOpen = { detailItem = item },
                        onLongPress = { if (!selecting) { selecting = true; selected.add(item.id) } },
                        onMoveUp = { store.movePlan(item.id, true) },
                        onMoveDown = { store.movePlan(item.id, false) },
                        onReschedule = { store.reschedulePlan(item.id, it) },
                    )
                }
            }
        }
        Spacer(Modifier.height(150.dp))
    }

    if (planPickerOpen) CustomerPickerSheet(
        store = store,
        initialQuery = planPickerInit,
        onPick = { name -> newClient = name; planPickerOpen = false },
        onDismiss = { planPickerOpen = false },
    )

    if (goalDialog) {
        var goal by remember { mutableStateOf(store.dailyGoal.takeIf { it > 0 }?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { goalDialog = false },
            confirmButton = { TextButton(onClick = { store.chooseDailyGoal(goal.toIntOrNull() ?: 0); goalDialog = false }) { Text(t["save"], color = c.ink) } },
            dismissButton = {
                if (store.dailyGoal > 0) TextButton(onClick = { store.chooseDailyGoal(0); goalDialog = false }) { Text(t["clear_goal"], color = c.muted) }
                else TextButton(onClick = { goalDialog = false }) { Text(t["cancel"], color = c.muted) }
            },
            title = { Text(t["daily_goal"]) },
            text = {
                Column {
                    Text(t["daily_goal_desc"], color = c.muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Input(goal, { goal = it.filter { ch -> ch.isDigit() }.take(2) }, t["daily_goal"], kb = KeyboardType.Number)
                }
            },
            containerColor = c.surface,
        )
    }

    // Tapping a route stop opens its full details.
    detailItem?.let { picked ->
        // Resolve the live item so it reflects toggles/edits made while the sheet is open.
        val live = store.plan.firstOrNull { it.id == picked.id }
        if (live == null) detailItem = null
        else PlanDetailSheet(
            store = store, item = live,
            onToggleDone = { store.togglePlan(live.id) },
            onReschedule = { store.reschedulePlan(live.id, it) },
            onSaveEdit = { a, cl, ti, m -> store.updatePlan(live.id, a, cl, ti, m) },
            onDelete = { store.deletePlan(live.id) },
            onDeleted = { detailItem = null },
            onDismiss = { detailItem = null },
        )
    }

    // Tapping a task in the daily agenda (overdue / due-today / decisions) opens the same detail card.
    taskDetail?.let { picked ->
        val live = store.tasks.firstOrNull { it.id == picked.id }
        if (live == null) taskDetail = null
        else PlanDetailSheet(
            store = store, item = live,
            onToggleDone = { store.toggleTask(live.id) },
            onReschedule = { store.rescheduleTask(live.id, it) },
            onSaveEdit = { a, cl, ti, m -> store.updateTask(live.id, a, cl, ti, m) },
            onDelete = { store.deleteTask(live.id) },
            onDeleted = { taskDetail = null },
            onDismiss = { taskDetail = null },
        )
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

/**
 * Opens the device calendar prefilled to create an event (plan 6.4, no-OAuth slice). Works with any
 * installed calendar (Google/Outlook/…). If time is blank the event is all-day on [dateIso].
 */
private fun launchCalendarInsert(ctx: android.content.Context, title: String, description: String, dateIso: String, timeHm: String, minutes: Int) {
    val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, title.ifBlank { description }.ifBlank { "Follow-up" })
        .putExtra(CalendarContract.Events.DESCRIPTION, description)
    runCatching {
        val date = java.time.LocalDate.parse(dateIso)
        if (timeHm.isBlank()) {
            val begin = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        } else {
            val parts = timeHm.split(":")
            val begin = date.atTime(parts.getOrNull(0)?.toIntOrNull() ?: 9, parts.getOrNull(1)?.toIntOrNull() ?: 0)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + (minutes.takeIf { it > 0 } ?: 30) * 60_000L)
        }
    }
    runCatching { ctx.startActivity(intent) }
}

@Composable
private fun agendaReasonLabel(r: AgendaReason): String {
    val t = LocalL.current
    return when (r) {
        AgendaReason.APPOINTMENT -> t["reason_appointment"]
        AgendaReason.OVERDUE -> t["overdue"]
        AgendaReason.DUE_TODAY -> t["reason_due_today"]
        AgendaReason.NEEDS_ACTION -> t["reason_needs_action"]
        AgendaReason.EXPIRED -> t["reason_expired"]
    }
}

@Composable
private fun AgendaBlock(agenda: Agenda, store: Store, onEditVisit: (Visit) -> Unit, onOpenTask: (PlanItem) -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    @Composable
    fun section(title: String, items: List<AgendaItem>) {
        if (items.isEmpty()) return
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
            Text(title, color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            items.forEach { item -> AgendaRow(item, store, onEditVisit, onOpenTask) }
        }
    }
    section(t["agenda_overdue"], agenda.overdue)
    section(t["agenda_today"], agenda.today)
    section(t["agenda_suggestions"], agenda.suggestions)
}

@Composable
private fun AgendaRow(item: AgendaItem, store: Store, onEditVisit: (Visit) -> Unit, onOpenTask: (PlanItem) -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val isTask = item.kind == "TASK"
    val icon = when (item.kind) {
        "VISIT" -> AppIcons.Notifications
        "OPP" -> AppIcons.Chart
        "QUOTE" -> AppIcons.Plan
        else -> AppIcons.Check
    }
    val onOpen: () -> Unit = {
        when (item.kind) {
            "VISIT" -> store.visits.firstOrNull { it.id == item.refId }?.let(onEditVisit)
            "TASK" -> store.tasks.firstOrNull { it.id == item.refId }?.let(onOpenTask)
            else -> {}
        }
    }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp), color = c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (item.reason == AgendaReason.OVERDUE) c.lost.copy(alpha = 0.5f) else c.edge),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isTask) {
                Box(
                    Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).border(2.dp, c.edge, RoundedCornerShape(7.dp))
                        .clickable { store.setTaskStatus(item.refId, TaskStatus.DONE) },
                    contentAlignment = Alignment.Center,
                ) {}
            } else {
                Box(Modifier.size(34.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = c.ink2, modifier = Modifier.size(17.dp))
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title.ifBlank { item.customer }, color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(
                    item.customer.takeIf { it.isNotBlank() && it != item.title },
                    item.time.takeIf { it.isNotBlank() }?.let { fmtTime(it) },
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(sub, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Reason chip explains why it's here.
            Surface(shape = RoundedCornerShape(999.dp), color = if (item.reason == AgendaReason.OVERDUE) c.lost.copy(alpha = 0.12f) else c.sunk) {
                Text(
                    agendaReasonLabel(item.reason),
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = if (item.reason == AgendaReason.OVERDUE) c.lost else c.muted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                )
            }
            // Postpone a task by one day (a reschedule, not a status change).
            if (isTask) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(CircleShape).clickable {
                        val next = runCatching { java.time.LocalDate.parse(item.date.ifBlank { todayIso() }).plusDays(1).toString() }.getOrDefault(todayIso())
                        store.rescheduleTask(item.refId, next)
                    }.padding(4.dp),
                ) { Icon(ClockIcon, t["postpone"], tint = c.muted, modifier = Modifier.size(17.dp)) }
            }
        }
    }
}

@Composable
private fun RoadStop(
    index: Int, item: PlanItem, customer: Customer?, first: Boolean, last: Boolean,
    selecting: Boolean, selectedNow: Boolean,
    onToggle: () -> Unit, onOpen: () -> Unit, onLongPress: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit,
    onReschedule: (String) -> Unit,
) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    // Numbers you can call: contacts that have a phone, else the customer's own number.
    val callable = customer?.contacts?.filter { it.phone.isNotBlank() }
        ?.ifEmpty { if (customer.phone.isNotBlank()) listOf(ContactPerson(customer.contact, "", customer.phone)) else emptyList() }
        ?: emptyList()
    val locUrl = customer?.locationUrl?.trim().orEmpty()
    val address = customer?.address?.trim().orEmpty()
    var showCallChooser by remember { mutableStateOf(false) }
    val title = item.action.ifBlank { item.client }
    val subtitle = if (item.action.isNotBlank()) item.client else ""

    Row(Modifier.height(IntrinsicSize.Min)) {
        // rail: connecting line + node
        Box(Modifier.width(40.dp).fillMaxHeight()) {
            if (!first) Box(Modifier.align(Alignment.TopCenter).width(2.5.dp).fillMaxHeight(0.5f).background(c.faint))
            if (!last) Box(Modifier.align(Alignment.BottomCenter).width(2.5.dp).fillMaxHeight(0.5f).background(c.faint))
            Box(
                Modifier.align(Alignment.Center).size(30.dp).clip(CircleShape)
                    .background(if (item.done || (selecting && selectedNow)) c.ink else c.surface)
                    .border(2.dp, if (item.done || (selecting && selectedNow)) c.ink else c.muted, CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    selecting && selectedNow -> Icon(AppIcons.Check, null, tint = c.onInk, modifier = Modifier.size(17.dp))
                    selecting -> {}
                    item.done -> Icon(AppIcons.Check, null, tint = c.onInk, modifier = Modifier.size(17.dp))
                    else -> Text("$index", color = c.ink2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            modifier = Modifier.weight(1f).padding(vertical = 6.dp)
                .combinedClickable(onClick = { if (selecting) onToggle() else onOpen() }, onLongClick = onLongPress),
            shape = RoundedCornerShape(16.dp),
            color = if (selectedNow) c.sunk else c.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedNow) c.ink else c.edge),
            shadowElevation = if (c.dark) 0.dp else 2.dp,
        ) {
            Row(Modifier.padding(start = 16.dp, end = 6.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        title,
                        color = if (item.done) c.muted else c.ink,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(subtitle, color = c.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (selecting) {
                    if (!first) IconButton(onClick = onMoveUp) { Text("↑", color = c.ink2, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    if (!last) IconButton(onClick = onMoveDown) { Text("↓", color = c.ink2, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                } else {
                    if (locUrl.isNotBlank() || address.isNotBlank()) {
                        IconButton(onClick = {
                            val uri = if (locUrl.isNotBlank()) Uri.parse(locUrl) else mapsRouteUri(listOf(address))
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        }) { Icon(AppIcons.Directions, t["navigate"], tint = c.ink2, modifier = Modifier.size(19.dp)) }
                    }
                    if (callable.isNotEmpty()) {
                        IconButton(onClick = {
                            if (callable.size == 1) runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + callable[0].phone.trim()))) }
                            else showCallChooser = true
                        }) { Icon(AppIcons.Phone, t["call"], tint = c.ink2, modifier = Modifier.size(19.dp)) }
                    }
                    if (!item.done) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(AppIcons.More, t["reschedule"], tint = c.ink2, modifier = Modifier.size(19.dp)) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                val base = runCatching { java.time.LocalDate.parse(item.date) }.getOrDefault(java.time.LocalDate.now())
                                DropdownMenuItem(text = { Text(t["move_tomorrow"]) }, onClick = { menuOpen = false; onReschedule(base.plusDays(1).toString()) })
                                DropdownMenuItem(text = { Text(t["move_day_after"]) }, onClick = { menuOpen = false; onReschedule(base.plusDays(2).toString()) })
                                DropdownMenuItem(text = { Text(t["move_pick_day"]) }, onClick = { menuOpen = false; showDate = true })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDate) DatePick(item.date) { showDate = false; onReschedule(it) }

    if (showCallChooser) {
        AlertDialog(
            onDismissRequest = { showCallChooser = false },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCallChooser = false }) { Text(t["cancel"], color = c.muted) } },
            title = { Text(t["choose_contact"]) },
            text = {
                Column {
                    callable.forEach { cp ->
                        Surface(
                            onClick = {
                                showCallChooser = false
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cp.phone.trim()))) }
                            },
                            color = Color.Transparent, modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.Phone, null, tint = c.ink2, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(cp.name.ifBlank { cp.phone }, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    if (cp.name.isNotBlank()) Text(cp.phone, color = c.muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            },
            containerColor = c.surface,
        )
    }
}

/** Full detail card for a route stop (tap-to-open). Shows every field of the plan item plus quick
 *  actions (navigate / call / calendar), status toggle, reschedule, inline edit and delete. */
@Composable
private fun PlanDetailSheet(
    store: Store, item: PlanItem,
    onToggleDone: () -> Unit,
    onReschedule: (String) -> Unit,
    onSaveEdit: (String, String, String, Int) -> Unit,
    onDelete: () -> Unit,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val st = item.statusEnum()
    val done = item.done
    val customer = store.customerFor(item.client)
    val opp = item.opportunityId.takeIf { it.isNotBlank() }?.let { id -> store.opportunities.firstOrNull { it.id == id } }
    val callable = customer?.contacts?.filter { it.phone.isNotBlank() }
        ?.ifEmpty { if (customer.phone.isNotBlank()) listOf(ContactPerson(customer.contact, "", customer.phone)) else emptyList() }
        ?: emptyList()
    val locUrl = customer?.locationUrl?.trim().orEmpty()
    val address = customer?.address?.trim().orEmpty()

    var editing by remember { mutableStateOf(false) }
    var eAction by remember { mutableStateOf(item.action) }
    var eClient by remember { mutableStateOf(item.client) }
    var eTime by remember { mutableStateOf(item.time) }
    var eMinutes by remember { mutableStateOf(item.minutes) }
    var showTime by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showCallChooser by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.edge))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            // Header: title + status pill + close
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.action.ifBlank { item.client }.ifBlank { t["task_details"] },
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.weight(1f),
                )
                CircleBtn(CloseXIcon, t["done"]) { onDismiss() }
            }
            Surface(shape = RoundedCornerShape(999.dp), color = if (done) c.ok.copy(alpha = 0.15f) else c.sunk) {
                Text(
                    st.label(t.en), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (done) c.ok else c.ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))

            if (editing) {
                LabeledBlock(t["task_action"]) { Input(eAction, { eAction = it }, t["plan_hint"]) }
                LabeledBlock(t["choose_customer"]) { Input(eClient, { eClient = it }, t["choose_customer"]) }
                PickerField(t["time"], if (eTime.isBlank()) t["none"] else fmtTime(eTime), Modifier.fillMaxWidth().padding(bottom = 15.dp)) { showTime = true }
                LabeledBlock(t["duration"]) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 15, 30, 45, 60, 90, 120).forEach { m ->
                            ChoiceChip(if (m == 0) t["none"] else fmtDuration(m, t.en), eMinutes == m) { eMinutes = m }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = { onSaveEdit(eAction, eClient, eTime, eMinutes); editing = false },
                    shape = RoundedCornerShape(16.dp), color = c.ink, modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(t["save"], color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 15.sp) } }
                TextButton(onClick = { editing = false }, modifier = Modifier.fillMaxWidth()) { Text(t["cancel"], color = c.muted) }
            } else {
                // Detail rows
                DetailRow(AppIcons.Person, t["choose_customer"], item.client.ifBlank { t["none"] })
                DetailRow(AppIcons.Calendar, t["date"], fullDay(item.date))
                if (item.time.isNotBlank()) DetailRow(AppIcons.Notifications, t["time"], fmtTime(item.time))
                if (item.minutes > 0) DetailRow(AppIcons.Plan, t["duration"], fmtDuration(item.minutes, t.en))
                item.source.takeIf { it.isNotBlank() && it != TaskSource.MANUAL.name }
                    ?.let { runCatching { TaskSource.valueOf(it) }.getOrNull() }
                    ?.let { DetailRow(AppIcons.Plan, t["task_source"], it.label(t.en)) }
                opp?.let { DetailRow(AppIcons.Directions, t["opp_link"], it.title) }
                if (item.completedAt.isNotBlank()) DetailRow(AppIcons.Check, t["completed"], fullDay(item.completedAt))
                if (item.result.isNotBlank()) DetailRow(AppIcons.Plan, t["result"], item.result)

                Spacer(Modifier.height(14.dp))
                // Quick actions
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (locUrl.isNotBlank() || address.isNotBlank()) {
                        ChoiceChip(t["navigate"], false) {
                            val uri = if (locUrl.isNotBlank()) Uri.parse(locUrl) else mapsRouteUri(listOf(address))
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        }
                    }
                    if (callable.isNotEmpty()) {
                        ChoiceChip(t["call"], false) {
                            if (callable.size == 1) runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + callable[0].phone.trim()))) }
                            else showCallChooser = true
                        }
                    }
                    ChoiceChip(t["add_to_calendar"], false) {
                        val title = listOf(item.client, item.action).filter { it.isNotBlank() }.joinToString(": ").ifBlank { t["task_details"] }
                        launchCalendarInsert(ctx, title, item.action, item.date, item.time, item.minutes)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Surface(
                    onClick = onToggleDone,
                    shape = RoundedCornerShape(16.dp), color = if (done) c.sunk else c.ink,
                    border = if (done) androidx.compose.foundation.BorderStroke(1.dp, c.edge) else null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (done) t["mark_undone"] else t["mark_done"], color = if (done) c.ink else c.onInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(t["reschedule"], color = c.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val base = runCatching { java.time.LocalDate.parse(item.date) }.getOrDefault(java.time.LocalDate.now())
                    ChoiceChip(t["move_tomorrow"], false) { onReschedule(base.plusDays(1).toString()); onDismiss() }
                    ChoiceChip(t["move_day_after"], false) { onReschedule(base.plusDays(2).toString()); onDismiss() }
                    ChoiceChip(t["move_pick_day"], false) { showDate = true }
                }

                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { eAction = item.action; eClient = item.client; eTime = item.time; eMinutes = item.minutes; editing = true }, modifier = Modifier.weight(1f)) {
                        Text(t["edit"], color = c.ink2, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { onDelete(); onDeleted() }, modifier = Modifier.weight(1f)) {
                        Icon(AppIcons.Delete, null, tint = c.lost, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t["delete"], color = c.lost, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showTime) TimePick(eTime.ifBlank { nowHm() }) { eTime = it; showTime = false }
    if (showDate) DatePick(item.date) { showDate = false; onReschedule(it); onDismiss() }
    if (showCallChooser) {
        AlertDialog(
            onDismissRequest = { showCallChooser = false },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCallChooser = false }) { Text(t["cancel"], color = c.muted) } },
            title = { Text(t["choose_contact"]) },
            text = {
                Column {
                    callable.forEach { cp ->
                        Surface(
                            onClick = {
                                showCallChooser = false
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cp.phone.trim()))) }
                            },
                            color = Color.Transparent, modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.Phone, null, tint = c.ink2, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(cp.name.ifBlank { cp.phone }, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    if (cp.name.isNotBlank()) Text(cp.phone, color = c.muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    val c = LocalSales.current
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = c.muted, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(value, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/* ---------------- tools: hub + inventory (stock + prices) ---------------- */

/** The Tools hub: a menu of tool pages (stock, prices, insights, opportunities, team). */
@Composable
private fun ToolsScreen(
    store: Store,
    onInsights: () -> Unit,
    onOpps: () -> Unit,
    onTeam: (() -> Unit)?,
    onBack: () -> Unit,
) {
    var sub by remember { mutableStateOf<String?>(null) }
    when (sub) {
        "stock" -> InventoryScreen(store) { sub = null }
        "prices" -> PricesScreen(store) { sub = null }
        "mail" -> MailScreen(store) { sub = null }
        "ask" -> AskDataScreen(store) { sub = null }
        "analytics" -> AnalyticsScreen(store) { sub = null }
        "weekly" -> WeeklyReviewScreen(store) { sub = null }
        "lost" -> LostDealsScreen(store) { sub = null }
        "knowledge" -> ProductKnowledgeScreen(store) { sub = null }
        "objections" -> ObjectionsScreen(store) { sub = null }
        "projects" -> ProjectsScreen(store) { sub = null }
        "linking" -> NeedsLinkingScreen(store) { sub = null }
        else -> ToolsHub(store, onBack, onStock = { sub = "stock" }, onPrices = { sub = "prices" }, onMail = { sub = "mail" }, onInsights, onOpps, onTeam, onAsk = { sub = "ask" }, onAnalytics = { sub = "analytics" }, onWeekly = { sub = "weekly" }, onLost = { sub = "lost" }, onKnowledge = { sub = "knowledge" }, onObjections = { sub = "objections" }, onProjects = { sub = "projects" }, onLinking = { sub = "linking" })
    }
}

@Composable
private fun ToolsHub(
    store: Store,
    onBack: () -> Unit, onStock: () -> Unit, onPrices: () -> Unit, onMail: () -> Unit,
    onInsights: () -> Unit, onOpps: () -> Unit, onTeam: (() -> Unit)?, onAsk: () -> Unit, onAnalytics: () -> Unit, onWeekly: () -> Unit, onLost: () -> Unit,
    onKnowledge: () -> Unit, onObjections: () -> Unit, onProjects: () -> Unit, onLinking: () -> Unit,
) {
    val c = LocalSales.current
    val t = LocalL.current
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t["tools"], fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
        }
        Spacer(Modifier.height(6.dp))
        GroupCard {
            SettingsRow(AppIcons.Chart, t["opportunities"]) { onOpps() }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Insights, t["insights_tab"]) { onInsights() }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Chart, t["analytics"]) { onAnalytics() }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Report, t["weekly_review"]) { onWeekly() }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Chart, t["lost_deals"]) { onLost() }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Map, t["projects"]) { onProjects() }
            HorizontalDivider(color = c.edge)
            val unlinked = store.unlinkedNames()
            if (unlinked.isNotEmpty()) {
                SettingsRow(AppIcons.PersonAdd, t["needs_linking"], value = unlinked.size.toString()) { onLinking() }
                HorizontalDivider(color = c.edge)
            }
            SettingsRow(AppIcons.Search, t["ask_data"]) { onAsk() }
            if (onTeam != null) {
                HorizontalDivider(color = c.edge)
                SettingsRow(AppIcons.People, t["team"]) { onTeam() }
            }
        }
        GroupCard {
            SettingsRow(AppIcons.Inbox, t["inventory"]) { onStock() }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Report, t["prices"]) { onPrices() }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Inbox, t["product_knowledge"]) { onKnowledge() }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Plan, t["objections"]) { onObjections() }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Email, t["mail"]) { onMail() }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** "Ask your data" + Sales Coach (plan 4.5): the app computes the numbers/records locally; the
 *  optional AI button only summarizes what was computed. */
@Composable
private fun AskDataScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    val overdue = DataQueries.overdueTasks(store.tasks, todayIso())
    val quotesNoFu = DataQueries.sentQuotesNoFollowup(store.quotes, store.activities)
    val oppsNoDM = DataQueries.oppsNoDecisionMaker(store.opportunities, store.customers)
    val oppsNoNext = DataQueries.oppsNoNextStep(store.opportunities)
    val prospects = DataQueries.prospects(store.customers)
    val queries = listOf(
        t["q_overdue"] to overdue,
        t["q_quotes_nofu"] to quotesNoFu,
        t["q_opps_nodm"] to oppsNoDM,
        t["q_opps_nonext"] to oppsNoNext,
        t["q_prospects"] to prospects,
    )
    val flagged = queries.filter { it.second.isNotEmpty() }

    var summary by remember { mutableStateOf<String?>(null) }
    var summarizing by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    fun summarize() {
        when {
            store.apiKey.isBlank() -> { Toast.makeText(ctx, t["need_key"], Toast.LENGTH_LONG).show(); return }
            !store.aiConsent -> { consent = true; return }
        }
        val findings = flagged.joinToString("\n") { (title, list) -> "$title: ${list.size} — " + list.take(5).joinToString("; ") { it.title } }
            .ifBlank { "No open issues." }
        summarizing = true; summary = null
        scope.launch {
            try { summary = AiFormatter.summarizeFindings(store.apiKey, store.aiModel, "Coach me on my pipeline", findings, t.en) }
            catch (e: Exception) { summary = "${t["ai_failed"]}: ${e.message}" } finally { summarizing = false }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t["ask_data"], fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
        }

        // Coach: bullets of what needs attention (computed locally; count = the basis).
        Card {
            Text(t["ask_coach"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (flagged.isEmpty()) Text(t["ask_on_track"], color = c.muted, fontSize = 13.5.sp)
            else flagged.forEach { (title, list) ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("•  ", color = c.muted, fontSize = 14.sp)
                    Text("$title — ${list.size}", color = c.ink2, fontSize = 13.5.sp, lineHeight = 19.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                summarizing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = c.ink, strokeWidth = 3.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp)); Text(t["previsit_thinking"], color = c.muted, fontSize = 12.5.sp)
                }
                summary != null -> Text(summary!!, color = c.ink2, fontSize = 13.5.sp, lineHeight = 19.sp)
                else -> ChoiceChip(t["ask_summarize"], false) { summarize() }
            }
        }

        queries.forEach { (title, list) -> QueryCard(title, list) }
        Text(t["ask_scope_note"], color = c.faint, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        Spacer(Modifier.height(40.dp))
    }

    if (consent) AlertDialog(
        onDismissRequest = { consent = false },
        confirmButton = { TextButton(onClick = { store.grantAiConsent(); consent = false; summarize() }) { Text(t["agree"]) } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text(t["cancel"], color = c.muted) } },
        title = { Text(t["ai_consent_title"]) }, text = { Text(t["ai_consent_desc"]) }, containerColor = c.surface,
    )
}

@Composable
private fun QueryCard(title: String, findings: List<DataQueries.Finding>) {
    val c = LocalSales.current
    var expanded by remember { mutableStateOf(false) }
    Card {
        Row(Modifier.fillMaxWidth().clickable(enabled = findings.isNotEmpty()) { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(findings.size.toString(), color = if (findings.isEmpty()) c.muted else c.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            if (findings.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Icon(if (expanded) AppIcons.ArrowBack else AppIcons.ArrowForward, null, tint = c.muted, modifier = Modifier.size(16.dp))
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            findings.forEach { f ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(f.title, color = c.ink2, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    if (f.subtitle.isNotBlank()) Text(f.subtitle, color = c.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

/** Analytics dashboard (plan 5.2): period filter, defined metrics, per-currency money, and a
 *  same-length prior-period comparison shown as ACTUAL counts (not just percentages). */
@Composable
private fun AnalyticsScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    BackHandler(onBack = onBack)
    var period by remember { mutableStateOf("WEEK") }
    val today = remember { java.time.LocalDate.now() }

    fun rangeOf(kind: String): Analytics.DateRange = when (kind) {
        "WEEK" -> weekStart(0).let { Analytics.DateRange(it.toString(), it.plusDays(6).toString()) }
        "MONTH" -> today.withDayOfMonth(1).let { Analytics.DateRange(it.toString(), it.plusMonths(1).minusDays(1).toString()) }
        else -> Analytics.DateRange(today.minusDays(29).toString(), today.toString())
    }
    val r = rangeOf(period)
    val start = java.time.LocalDate.parse(r.start); val end = java.time.LocalDate.parse(r.end)
    val len = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
    val prev = Analytics.DateRange(start.minusDays(len).toString(), start.minusDays(1).toString())
    fun metricsFor(rg: Analytics.DateRange) = Analytics.compute(
        rg, store.customers, store.visits, store.activities, store.quotes, store.opportunities, store.orders, store.tasks, store.defaultCurrency,
    )
    val m = metricsFor(r)
    val pm = metricsFor(prev)

    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t["analytics"], fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("WEEK" to t["period_week"], "MONTH" to t["period_month"], "D30" to t["period_30"]).forEach { (k, label) ->
                ChoiceChip(label, period == k) { period = k }
            }
        }
        Text("${fullDay(r.start)} — ${fullDay(r.end)}", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp))

        Card {
            MetricRow(t["m_new_prospects"], m.newProspects, pm.newProspects)
            MetricRow(t["m_interactions"], m.completedInteractions, pm.completedInteractions)
            MetricRow(t["m_no_answer"], m.callsNoAnswer, pm.callsNoAnswer)
            MetricRow(t["m_quotes_sent"], m.quotesSent, pm.quotesSent)
            MetricRow(t["m_won"], m.won, pm.won)
            MetricRow(t["m_lost"], m.lost, pm.lost)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t["m_adherence"], color = c.ink2, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                Text(m.adherence?.let { "${(it * 100).toInt()}% (${m.followUpOnTime}/${m.followUpTotal})" } ?: "—", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        MoneyCard(t["m_pipeline"], m.pipelineByCurrency, note = t["m_pipeline_note"])
        MoneyCard(t["m_won_value"], m.wonValueByCurrency)
        MoneyCard(t["m_sales"], m.salesByCurrency, note = t["m_sales_note"])

        Text(t["analytics_note"], color = c.faint, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun MetricRow(label: String, value: Int, prev: Int) {
    val c = LocalSales.current
    val diff = value - prev
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.ink2, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Text(value.toString(), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(8.dp))
        val (txt, col) = when {
            diff > 0 -> "▲$diff" to c.ok
            diff < 0 -> "▼${-diff}" to c.lost
            else -> "=" to c.muted
        }
        Text(txt, color = col, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 34.dp))
    }
}

@Composable
private fun MoneyCard(title: String, byCurrency: Map<String, Double>, note: String? = null) {
    val c = LocalSales.current
    Card {
        Text(title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (byCurrency.isEmpty()) Text("—", color = c.muted, fontSize = 13.sp)
        else byCurrency.entries.sortedByDescending { it.value }.forEach { (cur, amt) ->
            Text("${fmtMoney(amt)} $cur", color = c.ink2, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 1.dp))
        }
        if (note != null) { Spacer(Modifier.height(4.dp)); Text(note, color = c.faint, fontSize = 11.sp) }
    }
}

/** Weekly review (plan 5.3): computed locally from Analytics + DataQueries, with deltas vs last week,
 *  a single suggested focus you can add as a task, and an OPTIONAL AI summary of the computed facts. */
@Composable
private fun WeeklyReviewScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    val wkStart = weekStart(0)
    val range = Analytics.DateRange(wkStart.toString(), wkStart.plusDays(6).toString())
    val prev = Analytics.DateRange(wkStart.minusDays(7).toString(), wkStart.minusDays(1).toString())
    fun metrics(rg: Analytics.DateRange) = Analytics.compute(rg, store.customers, store.visits, store.activities, store.quotes, store.opportunities, store.orders, store.tasks, store.defaultCurrency)
    val m = metrics(range); val pm = metrics(prev)

    val overdue = DataQueries.overdueTasks(store.tasks, todayIso()).size
    val quotesNoFu = DataQueries.sentQuotesNoFollowup(store.quotes, store.activities).size
    val oppsNoDM = DataQueries.oppsNoDecisionMaker(store.opportunities, store.customers).size
    val oppsNoNext = DataQueries.oppsNoNextStep(store.opportunities).size
    val focus = WeeklyReview.focus(overdue, quotesNoFu, oppsNoDM, oppsNoNext)
    val focusText = t[focus.lowercase()]   // FOCUS_OVERDUE -> "focus_overdue"
    val generatedAt = remember { java.time.LocalDateTime.now().let { "%04d-%02d-%02d %02d:%02d".format(it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute) } }

    var summary by remember { mutableStateOf<String?>(null) }
    var summarizing by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    fun summarize() {
        when {
            store.apiKey.isBlank() -> { Toast.makeText(ctx, t["need_key"], Toast.LENGTH_LONG).show(); return }
            !store.aiConsent -> { consent = true; return }
        }
        val findings = buildString {
            appendLine("Won: ${m.won} (prev ${pm.won}); Lost: ${m.lost} (prev ${pm.lost})")
            appendLine("Interactions: ${m.completedInteractions} (prev ${pm.completedInteractions}); Quotes sent: ${m.quotesSent}")
            appendLine("New prospects: ${m.newProspects}")
            appendLine("Attention — overdue: $overdue, quotes without follow-up: $quotesNoFu, deals w/o decision-maker: $oppsNoDM, deals w/o next step: $oppsNoNext")
        }
        summarizing = true; summary = null
        scope.launch {
            try { summary = AiFormatter.summarizeFindings(store.apiKey, store.aiModel, "Summarize my sales week", findings, t.en) }
            catch (e: Exception) { summary = "${t["ai_failed"]}: ${e.message}" } finally { summarizing = false }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t["weekly_review"], fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
        }
        Text("${fullDay(range.start)} — ${fullDay(range.end)} · ${t["generated"]} $generatedAt", color = c.muted, fontSize = 11.5.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp))

        Card {
            Text(t["wr_happened"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            MetricRow(t["m_won"], m.won, pm.won)
            MetricRow(t["m_lost"], m.lost, pm.lost)
            MetricRow(t["m_interactions"], m.completedInteractions, pm.completedInteractions)
            MetricRow(t["m_quotes_sent"], m.quotesSent, pm.quotesSent)
            MetricRow(t["m_new_prospects"], m.newProspects, pm.newProspects)
        }

        Card {
            Text(t["wr_attention"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            listOf(t["q_overdue"] to overdue, t["q_quotes_nofu"] to quotesNoFu, t["q_opps_nodm"] to oppsNoDM, t["q_opps_nonext"] to oppsNoNext)
                .filter { it.second > 0 }.let { flagged ->
                    if (flagged.isEmpty()) Text(t["ask_on_track"], color = c.muted, fontSize = 13.5.sp)
                    else flagged.forEach { (label, n) -> Row(Modifier.padding(vertical = 3.dp)) { Text("•  ", color = c.muted, fontSize = 14.sp); Text("$label — $n", color = c.ink2, fontSize = 13.5.sp) } }
                }
        }

        Card {
            Text(t["wr_focus"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(focusText, color = c.ink2, fontSize = 14.sp, lineHeight = 20.sp)
            if (focus != "FOCUS_OK") {
                Spacer(Modifier.height(10.dp))
                ChoiceChip(t["wr_add_focus"], false) {
                    store.addTask(focusText, "", todayIso(), source = TaskSource.MANUAL)
                    Toast.makeText(ctx, t["cmd_added"], Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 5.5 Weekly development goal — a behavior to work on; reviewed when the week rolls over.
        Card {
            Text(t["dev_goal"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val thisWeekIso = range.start
            val goalIsThisWeek = store.weeklyGoalWeek == thisWeekIso
            if (store.weeklyGoal.isNotBlank() && !goalIsThisWeek) {
                // Last week's goal — a short review before setting a new one.
                Text("${t["dev_last_goal"]}: ${store.weeklyGoal}", color = c.muted, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            var goalText by remember(store.weeklyGoal, goalIsThisWeek) { mutableStateOf(if (goalIsThisWeek) store.weeklyGoal else "") }
            Input(goalText, { goalText = it }, t["dev_goal_hint"])
            Spacer(Modifier.height(8.dp))
            ChoiceChip(t["dev_save_goal"], false) {
                store.setWeeklyGoal(goalText, thisWeekIso)
                Toast.makeText(ctx, t["cmd_added"], Toast.LENGTH_SHORT).show()
            }
            Spacer(Modifier.height(4.dp))
            Text(t["dev_goal_note"], color = c.faint, fontSize = 11.sp, lineHeight = 15.sp)
        }

        Card {
            Text(t["wr_ai_summary"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            when {
                summarizing -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(color = c.ink, strokeWidth = 3.dp, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(t["previsit_thinking"], color = c.muted, fontSize = 12.5.sp) }
                summary != null -> Text(summary!!, color = c.ink2, fontSize = 13.5.sp, lineHeight = 19.sp)
                else -> ChoiceChip(t["ask_summarize"], false) { summarize() }
            }
            Spacer(Modifier.height(6.dp))
            Text(t["wr_fact_note"], color = c.faint, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.height(40.dp))
    }

    if (consent) AlertDialog(
        onDismissRequest = { consent = false },
        confirmButton = { TextButton(onClick = { store.grantAiConsent(); consent = false; summarize() }) { Text(t["agree"]) } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text(t["cancel"], color = c.muted) } },
        title = { Text(t["ai_consent_title"]) }, text = { Text(t["ai_consent_desc"]) }, containerColor = c.surface,
    )
}

/** Lost-deal review (plan 5.4): each loss with its last stage, duration, reason (+source) and competitor;
 *  recurring patterns only when they repeat; reflective questions. Recorded reasons are never changed. */
@Composable
private fun LostDealsScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    BackHandler(onBack = onBack)
    val lost = store.opportunities.filter { it.stageEnum() == OppStage.LOST }
        .sortedByDescending { it.stageChangedAt }
    val patterns = LostDeals.patterns(lost)

    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t["lost_deals"], fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
        }

        if (patterns.isNotEmpty()) Card {
            Text(t["lost_patterns"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            patterns.forEach { (label, n) ->
                Row(Modifier.padding(vertical = 3.dp)) { Text("•  ", color = c.muted, fontSize = 14.sp); Text("$label — $n", color = c.ink2, fontSize = 13.5.sp) }
            }
            Spacer(Modifier.height(4.dp))
            Text(t["lost_pattern_note"], color = c.faint, fontSize = 11.sp, lineHeight = 15.sp)
        }

        if (lost.isEmpty()) {
            Card { Text(t["lost_none"], color = c.muted, fontSize = 13.5.sp) }
        } else lost.forEach { o ->
            Card {
                Text(o.title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (o.customerName.isNotBlank()) Text(o.customerName, color = c.muted, fontSize = 12.5.sp)
                Spacer(Modifier.height(8.dp))
                val lastStage = LostDeals.lastStageBeforeLost(o).takeIf { it.isNotBlank() }?.let { mapLegacyStage(it).label(t.en) } ?: "—"
                LostLine(t["lost_last_stage"], lastStage)
                val dur = if (o.createdAt.isNotBlank() && o.stageChangedAt.isNotBlank())
                    runCatching { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(o.createdAt), java.time.LocalDate.parse(o.stageChangedAt)).toString() + " " + t["days"] }.getOrDefault("—") else "—"
                LostLine(t["lost_duration"], dur)
                LostLine(t["lost_reason"], o.lossReason.ifBlank { "—" } + (if (o.lossReason.isNotBlank()) " (${if (o.lossReasonDeclared) t["loss_declared"] else t["loss_inferred"]})" else ""))
                LostLine(t["opp_competitor"], o.competitor.ifBlank { "—" })
            }
        }

        // Reflective questions (static — no AI verdicts).
        Card {
            Text(t["lost_reflect"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            listOf(t["lost_q1"], t["lost_q2"], t["lost_q3"]).forEach {
                Row(Modifier.padding(vertical = 3.dp)) { Text("•  ", color = c.muted, fontSize = 14.sp); Text(it, color = c.ink2, fontSize = 13.5.sp, lineHeight = 19.sp) }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun LostLine(label: String, value: String) {
    val c = LocalSales.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", color = c.muted, fontSize = 12.5.sp)
        Text(value, color = c.ink2, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Product knowledge (plan 6.1): specs/applications with a verified flag + source; match to a need. */
@Composable
private fun ProductKnowledgeScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    BackHandler(onBack = onBack)
    var q by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ProductKnowledge?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    val matches = if (q.isBlank()) null else ProductMatch.forNeed(q, store.products)

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
                Spacer(Modifier.width(12.dp))
                Text(t["product_knowledge"], fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
            }
            Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) { Input(q, { q = it }, t["pk_search"]) }

            if (matches != null) {
                if (matches.isEmpty()) Text(t["pk_no_match"], color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
                else matches.forEach { mm ->
                    ProductCard(mm.product, why = "${t["pk_match_why"]}: ${mm.why}") { editing = mm.product; sheetOpen = true }
                }
            } else if (store.products.isEmpty()) {
                Card { Text(t["pk_none"], color = c.muted, fontSize = 13.5.sp) }
            } else {
                store.products.forEach { p -> ProductCard(p, why = null) { editing = p; sheetOpen = true } }
            }
            Spacer(Modifier.height(120.dp))
        }
        Fab(onClick = { editing = null; sheetOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 24.dp))
    }
    if (sheetOpen) ProductSheet(store, editing) { sheetOpen = false }
}

@Composable
private fun ProductCard(p: ProductKnowledge, why: String?, onClick: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    Card {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                val sub = listOf(p.manufacturer, p.model).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = c.muted, fontSize = 12.5.sp)
            }
            Surface(shape = RoundedCornerShape(999.dp), color = if (p.verified) c.ok.copy(alpha = 0.14f) else c.sunk) {
                Text(if (p.verified) t["pk_verified"] else t["pk_needs_check"], Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = if (p.verified) c.ok else c.muted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (p.applications.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(p.applications, color = c.ink2, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        if (why != null) { Spacer(Modifier.height(4.dp)); Text(why, color = c.muted, fontSize = 11.5.sp) }
        if (p.source.isNotBlank() || p.updatedAt.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(listOf(p.source.takeIf { it.isNotBlank() }?.let { "${t["pk_source"]}: $it" }, p.updatedAt.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · "), color = c.faint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProductSheet(store: Store, editing: ProductKnowledge?, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var manufacturer by remember { mutableStateOf(editing?.manufacturer ?: "") }
    var model by remember { mutableStateOf(editing?.model ?: "") }
    var applications by remember { mutableStateOf(editing?.applications ?: "") }
    var specs by remember { mutableStateOf(editing?.specs ?: "") }
    var materials by remember { mutableStateOf(editing?.materials ?: "") }
    var limits by remember { mutableStateOf(editing?.limits ?: "") }
    var suitableFor by remember { mutableStateOf(editing?.suitableFor ?: "") }
    var source by remember { mutableStateOf(editing?.source ?: "") }
    var verified by remember { mutableStateOf(editing?.verified ?: false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(if (editing == null) t["pk_add"] else t["pk_edit"], fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
            LabeledBlock(t["pk_name"]) { Input(name, { name = it }, t["pk_name"]) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { LabeledBlock(t["pk_manufacturer"]) { Input(manufacturer, { manufacturer = it }, t["pk_manufacturer"]) } }
                Box(Modifier.weight(1f)) { LabeledBlock(t["pk_model"]) { Input(model, { model = it }, t["pk_model"]) } }
            }
            LabeledBlock(t["pk_applications"]) { Input(applications, { applications = it }, t["pk_applications"]) }
            LabeledBlock(t["pk_specs"]) { Input(specs, { specs = it }, t["pk_specs"]) }
            LabeledBlock(t["pk_materials"]) { Input(materials, { materials = it }, t["pk_materials"]) }
            LabeledBlock(t["pk_limits"]) { Input(limits, { limits = it }, t["pk_limits"]) }
            LabeledBlock(t["pk_suitable"]) { Input(suitableFor, { suitableFor = it }, t["pk_suitable"]) }
            LabeledBlock(t["pk_source"]) { Input(source, { source = it }, t["pk_source"]) }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t["pk_verified_toggle"], color = c.ink2, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(checked = verified, onCheckedChange = { verified = it })
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(t["save"], enabled = name.isNotBlank()) {
                store.upsertProduct(
                    ProductKnowledge(
                        id = editing?.id ?: store.newProductId(), productId = editing?.productId.orEmpty(),
                        name = name.trim(), manufacturer = manufacturer.trim(), model = model.trim(),
                        specs = specs.trim(), applications = applications.trim(), materials = materials.trim(),
                        limits = limits.trim(), suitableFor = suitableFor.trim(),
                        documents = editing?.documents.orEmpty(), source = source.trim(), updatedAt = todayIso(), verified = verified,
                    )
                )
                onDismiss()
            }
            if (editing != null) TextButton(onClick = { store.deleteProduct(editing.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Icon(AppIcons.Delete, null, tint = c.lost, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(t["delete"], color = c.lost, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Objection-handling library (plan 6.1). */
@Composable
private fun ObjectionsScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    BackHandler(onBack = onBack)
    var editing by remember { mutableStateOf<Objection?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
                Spacer(Modifier.width(12.dp))
                Text(t["objections"], fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
            }
            if (store.objections.isEmpty()) Card { Text(t["obj_none"], color = c.muted, fontSize = 13.5.sp) }
            else store.objections.forEach { o ->
                Card {
                    Row(Modifier.fillMaxWidth().clickable { editing = o; sheetOpen = true }) {
                        Column(Modifier.weight(1f)) {
                            Text(o.text.ifBlank { "—" }, color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                            if (o.response.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(o.response, color = c.ink2, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(120.dp))
        }
        Fab(onClick = { editing = null; sheetOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 24.dp))
    }
    if (sheetOpen) ObjectionSheet(store, editing) { sheetOpen = false }
}

@Composable
private fun ObjectionSheet(store: Store, editing: Objection?, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(editing?.text ?: "") }
    var probe by remember { mutableStateOf(editing?.probe ?: "") }
    var evidence by remember { mutableStateOf(editing?.evidence ?: "") }
    var response by remember { mutableStateOf(editing?.response ?: "") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(if (editing == null) t["obj_add"] else t["obj_edit"], fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
            LabeledBlock(t["obj_text"]) { Input(text, { text = it }, t["obj_text"]) }
            LabeledBlock(t["obj_probe"]) { Input(probe, { probe = it }, t["obj_probe"]) }
            LabeledBlock(t["obj_evidence"]) { Input(evidence, { evidence = it }, t["obj_evidence"]) }
            LabeledBlock(t["obj_response"]) { MultiField("", response, { response = it }, t["obj_response"]) }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(t["save"], enabled = text.isNotBlank()) {
                store.upsertObjection(Objection(editing?.id ?: store.newObjectionId(), text.trim(), probe.trim(), evidence.trim(), response.trim()))
                onDismiss()
            }
            if (editing != null) TextButton(onClick = { store.deleteObjection(editing.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Icon(AppIcons.Delete, null, tint = c.lost, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(t["delete"], color = c.lost, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Big projects + account map (plan 6.6): group several deals under one account initiative and map
 *  the stakeholders driving the decision. Combined value is shown per currency (never summed across). */
@Composable
private fun ProjectsScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    BackHandler(onBack = onBack)
    var editing by remember { mutableStateOf<Project?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
                Spacer(Modifier.width(12.dp))
                Text(t["projects"], fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
            }
            if (store.projects.isEmpty()) Card { Text(t["proj_none"], color = c.muted, fontSize = 13.5.sp) }
            else store.projects.forEach { p ->
                val st = p.statusEnum()
                val byCur = p.valueByCurrency(store.opportunities)
                Card {
                    Column(Modifier.fillMaxWidth().clickable { editing = p; sheetOpen = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name.ifBlank { "—" }, color = c.ink, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(999.dp), color = c.sunk) {
                                Text(st.label(t.en), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = c.ink2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (p.customerName.isNotBlank()) { Spacer(Modifier.height(3.dp)); Text(p.customerName, color = c.muted, fontSize = 13.sp) }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatChip(AppIcons.Chart, "${p.opportunityIds.size} ${t["proj_deals"]}")
                            if (p.stakeholderIds.isNotEmpty()) StatChip(AppIcons.People, "${p.stakeholderIds.size} ${t["proj_stakeholders"]}")
                        }
                        if (byCur.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            byCur.forEach { (cur, v) -> Text("${fmtMoney(v)} $cur", color = c.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(120.dp))
        }
        Fab(onClick = { editing = null; sheetOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 24.dp))
    }
    if (sheetOpen) ProjectSheet(store, editing) { sheetOpen = false }
}

@Composable
private fun ProjectSheet(store: Store, editing: Project?, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var client by remember { mutableStateOf(editing?.customerName ?: "") }
    var description by remember { mutableStateOf(editing?.description ?: "") }
    var status by remember { mutableStateOf(editing?.statusEnum() ?: ProjectStatus.ACTIVE) }
    var targetDate by remember { mutableStateOf(editing?.targetDate ?: "") }
    val oppIds = remember { mutableStateListOf<String>().apply { editing?.let { addAll(it.opportunityIds) } } }
    val stakeholderIds = remember { mutableStateListOf<String>().apply { editing?.let { addAll(it.stakeholderIds) } } }
    var pickerOpen by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }

    val customer = store.customerFor(client)
    // Deals for the chosen customer, so several can be rolled into this one project.
    val custOpps = if (client.isBlank()) emptyList()
        else store.opportunities.filter { (customer != null && it.customerId == customer.id) || it.customerName.trim().equals(client.trim(), ignoreCase = true) }
    val contacts = customer?.contacts.orEmpty()
    val byCur = custOpps.filter { it.id in oppIds }.groupBy { it.currency.ifBlank { "—" } }.mapValues { (_, l) -> l.sumOf { it.value } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(if (editing == null) t["proj_add"] else t["proj_edit"], fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
            LabeledBlock(t["proj_name"]) { Input(name, { name = it }, t["proj_name"]) }
            LabeledBlock(t["choose_customer"]) {
                Surface(onClick = { pickerOpen = true }, shape = RoundedCornerShape(12.dp), color = c.sunk, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Person, null, tint = c.ink2, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(client.ifBlank { t["choose_customer"] }, color = if (client.isBlank()) c.faint else c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            LabeledBlock(t["proj_desc"]) { MultiField("", description, { description = it }, t["proj_desc"]) }

            LabeledBlock(t["proj_status"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectStatus.values().forEach { s -> ChoiceChip(s.label(t.en), status == s) { status = s } }
                }
            }
            PickerField(t["proj_target"], if (targetDate.isBlank()) t["none"] else fullDay(targetDate), Modifier.fillMaxWidth().padding(bottom = 15.dp)) { showDate = true }

            // Roll deals into the project.
            if (custOpps.isNotEmpty()) LabeledBlock(t["proj_link_deals"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    custOpps.forEach { o ->
                        ChoiceChip(o.title, o.id in oppIds) { if (o.id in oppIds) oppIds.remove(o.id) else oppIds.add(o.id) }
                    }
                }
            } else if (client.isNotBlank()) {
                Text(t["proj_no_deals"], color = c.muted, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (byCur.isNotEmpty()) {
                Text(t["proj_total"], color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                byCur.forEach { (cur, v) -> Text("${fmtMoney(v)} $cur", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(10.dp))
            }

            // Account map: the decision structure — who drives this deal.
            if (contacts.isNotEmpty()) {
                Text(t["account_map"], color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                contacts.forEach { cp ->
                    val cid = cp.id.ifBlank { cp.name }
                    val on = cid in stakeholderIds
                    val roles = cp.roles.mapNotNull { runCatching { DecisionRole.valueOf(it) }.getOrNull()?.label(t.en) }.joinToString(" · ")
                    Surface(
                        onClick = { if (on) stakeholderIds.remove(cid) else stakeholderIds.add(cid) },
                        shape = RoundedCornerShape(12.dp), color = if (on) c.sunk else c.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (on) c.ink else c.edge),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(cp.name.ifBlank { "—" }, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                val sub = listOf(cp.jobTitle, roles).filter { it.isNotBlank() }.joinToString(" — ")
                                if (sub.isNotBlank()) { Spacer(Modifier.height(2.dp)); Text(sub, color = c.muted, fontSize = 12.sp) }
                            }
                            if (on) Icon(CheckIcon, null, tint = c.ink, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            PrimaryButton(t["save"], enabled = name.isNotBlank()) {
                store.upsertProject(
                    Project(
                        id = editing?.id ?: store.newProjectId(),
                        customerId = customer?.id ?: editing?.customerId.orEmpty(),
                        customerName = client.trim(),
                        name = name.trim(), description = description.trim(),
                        opportunityIds = oppIds.toList(), stakeholderIds = stakeholderIds.toList(),
                        status = status.name, targetDate = targetDate,
                        createdAt = editing?.createdAt?.ifBlank { todayIso() } ?: todayIso(),
                    )
                )
                onDismiss()
            }
            if (editing != null) TextButton(onClick = { store.deleteProject(editing.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Icon(AppIcons.Delete, null, tint = c.lost, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(t["delete"], color = c.lost, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (pickerOpen) CustomerPickerSheet(store, client, onPick = { client = it; pickerOpen = false }, onDismiss = { pickerOpen = false })
    if (showDate) DatePick(targetDate.ifBlank { todayIso() }) { targetDate = it; showDate = false }
}

/** "Needs linking" (plan 1.6): records the migration left unlinked because the name was ambiguous or
 *  free-text. Pick the right customer to link every matching record at once. */
@Composable
private fun NeedsLinkingScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    BackHandler(onBack = onBack)
    var picking by remember { mutableStateOf<String?>(null) }
    val unlinked = store.unlinkedNames()
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
                Spacer(Modifier.width(12.dp))
                Text(t["needs_linking"], fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
            }
            Text(t["needs_linking_desc"], color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
            if (unlinked.isEmpty()) Card { Text(t["needs_linking_none"], color = c.muted, fontSize = 13.5.sp) }
            else unlinked.forEach { (name, count) ->
                Card {
                    Row(Modifier.fillMaxWidth().clickable { picking = name }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text("$count ${t["needs_linking_records"]}", color = c.muted, fontSize = 12.5.sp)
                        }
                        Surface(shape = RoundedCornerShape(999.dp), color = c.ink) {
                            Text(t["needs_linking_link"], Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = c.onInk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
    picking?.let { name ->
        CustomerPickerSheet(
            store = store, initialQuery = name,
            onPick = { picked ->
                store.customerFor(picked)?.let { store.linkNameToCustomer(name, it.id) }
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

/** Compose an email (recipient from customer contacts) and hand it to the device's email app. */
@Composable
private fun MailScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }

    // All contact emails across customers, for the recipient picker.
    val contactEmails = remember(store.customers) {
        store.customers.flatMap { cust -> cust.contacts.filter { it.email.isNotBlank() }.map { Triple(it.name.ifBlank { cust.name }, it.email, cust.name) } }
            .distinctBy { it.second }
    }

    fun send() {
        val recipients = to.split(",", ";", " ").map { it.trim() }.filter { it.isNotBlank() }
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            if (recipients.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, recipients.toTypedArray())
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject.trim())
            if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body.trim())
        }
        runCatching { ctx.startActivity(Intent.createChooser(intent, t["mail_send"])) }
            .onFailure { Toast.makeText(ctx, t["no_mail_app"], Toast.LENGTH_LONG).show() }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
                Spacer(Modifier.width(12.dp))
                Text(t["mail"], fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink, modifier = Modifier.weight(1f))
                CircleBtn(AppIcons.People, t["mail_pick"]) { pickerOpen = true }
            }
            Text(t["mail_desc"], color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(8.dp))
                LabeledBlock(t["mail_to"]) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Input(to, { to = it }, t["mail_to_hint"], kb = KeyboardType.Email) }
                        ActionSquare(AppIcons.People, t["mail_pick"]) { pickerOpen = true }
                    }
                }
                LabeledBlock(t["mail_subject"]) { Input(subject, { subject = it }, t["mail_subject_hint"]) }
                LabeledBlock(t["mail_body"]) {
                    TextField(
                        value = body, onValueChange = { body = it },
                        placeholder = { Text(t["mail_body_hint"], color = c.faint) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = c.sunk, unfocusedContainerColor = c.sunk,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = c.ink, unfocusedTextColor = c.ink, cursorColor = c.ink,
                        ),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = { send() }, shape = RoundedCornerShape(16.dp), color = c.ink,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Email, null, tint = c.onInk, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(t["mail_send"], color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(t["mail_hint_note"], color = c.faint, fontSize = 11.5.sp)
            }
            Spacer(Modifier.height(60.dp))
        }
    }

    if (pickerOpen) MailRecipientSheet(contactEmails) { email ->
        if (email != null) to = if (to.isBlank()) email else "$to, $email"
        pickerOpen = false
    }
}

@Composable
private fun MailRecipientSheet(contacts: List<Triple<String, String, String>>, onPick: (String?) -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = { onPick(null) }, sheetState = sheetState, containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.edge))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(t["choose_contact"], fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(vertical = 10.dp))
            if (contacts.isEmpty()) {
                Text(t["no_contact_emails"], color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp))
            } else {
                contacts.forEach { (name, email, company) ->
                    Surface(onClick = { onPick(email) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
                                Icon(AppIcons.Email, null, tint = c.ink2, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(email, company).filter { it.isNotBlank() }.joinToString(" · "), color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<InventoryItem?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    fun exitSelect() { selecting = false; selected.clear() }
    BackHandler(selecting) { exitSelect() }
    BackHandler(!selecting, onBack = onBack)
    val items = store.inventory

    // Import items from a picked .xlsx / .csv file.
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching 0
            val rows = ExcelImport.parse(bytes)
            rows.forEach { r ->
                store.upsertInventory(InventoryItem(
                    id = store.newInventoryId(), name = r.name, quantity = r.quantity,
                    price = r.price, currency = r.currency.ifBlank { store.defaultCurrency }, notes = r.notes,
                ))
            }
            rows.size
        }.onSuccess { n ->
            Toast.makeText(ctx, if (n > 0) "${t["imported_prefix"]} $n" else t["import_empty"], Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(ctx, t["import_failed"], Toast.LENGTH_LONG).show()
        }
    }
    fun launchImport() = importer.launch(arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel", "text/csv", "text/comma-separated-values", "application/octet-stream",
    ))

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    CircleBtn(CloseXIcon, t["done"]) { exitSelect() }
                    Spacer(Modifier.width(12.dp))
                    Text("${selected.size}", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink, modifier = Modifier.weight(1f))
                    val allSelected = items.isNotEmpty() && selected.size == items.size
                    CircleBtn(AppIcons.Check, t["select_all"]) {
                        if (allSelected) selected.clear()
                        else { selected.clear(); selected.addAll(items.map { it.id }) }
                    }
                    if (selected.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        CircleBtn(AppIcons.Delete, t["delete"]) {
                            store.deleteInventoryItems(selected.toList()); exitSelect()
                        }
                    }
                } else {
                    CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
                    Spacer(Modifier.width(12.dp))
                    Text(t["inventory"], fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink, modifier = Modifier.weight(1f))
                    if (items.isNotEmpty()) {
                        CircleBtn(AppIcons.Plan, t["select"]) { selecting = true }
                        Spacer(Modifier.width(8.dp))
                    }
                    CircleBtn(AppIcons.Download, t["import_excel"]) { launchImport() }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Inbox, null, tint = c.muted, modifier = Modifier.size(23.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(t["empty_inventory_title"], color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(t["empty_inventory_desc"], color = c.muted, fontSize = 12.5.sp)
                        }
                    }
                }
            } else {
                if (!selecting) SearchPill(search, { search = it }, t["search_items"])
                val shown = items.filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) }
                if (shown.isEmpty()) {
                    Text(t["no_results"], color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp))
                }
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    shown.forEach { item ->
                        InventoryRow(
                            item = item, selecting = selecting, selectedNow = item.id in selected,
                            onClick = {
                                if (selecting) { if (item.id in selected) selected.remove(item.id) else selected.add(item.id) }
                                else { editing = item; sheetOpen = true }
                            },
                            onLongClick = { if (!selecting) { selecting = true; selected.add(item.id) } },
                        )
                    }
                }
            }
            Spacer(Modifier.height(120.dp))
        }
        Fab(
            onClick = { editing = null; sheetOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 24.dp),
        )
    }

    if (sheetOpen) InventorySheet(store, editing) { sheetOpen = false }
}

/** A compact rounded search field (magnifier + clear) for list screens. */
@Composable
private fun SearchPill(query: String, onQuery: (String) -> Unit, hint: String) {
    val c = LocalSales.current
    TextField(
        value = query, onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
        placeholder = { Text(hint, color = c.faint) },
        leadingIcon = { Icon(AppIcons.Search, null, tint = c.muted, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (query.isNotBlank()) Box(Modifier.clip(CircleShape).clickable { onQuery("") }.padding(6.dp)) {
                Icon(CloseXIcon, null, tint = c.muted, modifier = Modifier.size(16.dp))
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.sunk, unfocusedContainerColor = c.sunk,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = c.muted, unfocusedLeadingIconColor = c.muted,
            focusedTextColor = c.ink, unfocusedTextColor = c.ink, cursorColor = c.ink,
        ),
    )
}

@Composable
private fun InventoryRow(item: InventoryItem, selecting: Boolean = false, selectedNow: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val c = LocalSales.current
    val t = LocalL.current
    Surface(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selectedNow) c.sunk else c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedNow) c.ink else c.edge),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .background(if (selectedNow) c.ink else Color.Transparent)
                        .border(2.dp, if (selectedNow) c.ink else c.muted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (selectedNow) Icon(CheckIcon, null, tint = c.onInk, modifier = Modifier.size(13.dp)) }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, color = c.ink, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val price = if (item.hasPrice) "${fmtMoney(item.price)}${if (item.currency.isNotBlank()) " " + item.currency else ""}" else t["no_price"]
                Text(price, color = c.muted, fontSize = 12.5.sp)
            }
            Surface(shape = RoundedCornerShape(999.dp), color = if (item.inStock) c.ok.copy(alpha = 0.15f) else c.lost.copy(alpha = 0.15f)) {
                Text(
                    if (item.inStock) "${t["in_stock"]} · ${item.quantity}" else t["out_of_stock"],
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (item.inStock) c.ok else c.lost, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun InventorySheet(store: Store, editing: InventoryItem?, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var qty by remember { mutableStateOf(editing?.quantity?.toString() ?: "") }
    var price by remember { mutableStateOf(editing?.price?.takeIf { it > 0.0 }?.toString() ?: "") }
    var currency by remember { mutableStateOf((editing?.currency ?: "").ifBlank { store.defaultCurrency }) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.edge))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) t["new_item"] else t["edit_item"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.weight(1f))
                CircleBtn(CloseXIcon, t["done"]) { onDismiss() }
            }
            LabeledBlock(t["item_name"]) { Input(name, { name = it }, t["item_name_hint"]) }
            LabeledBlock(t["quantity"]) { Input(qty, { qty = it.filter { ch -> ch.isDigit() } }, "0", kb = KeyboardType.Number) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { LabeledBlock(t["price"]) { Input(price, { price = it.filter { ch -> ch.isDigit() || ch == '.' } }, "0", kb = KeyboardType.Number) } }
                Box(Modifier.weight(1f)) { LabeledBlock(t["currency"]) { Input(currency, { currency = it }, t["currency_hint"]) } }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = {
                    if (name.isNotBlank()) {
                        store.upsertInventory(
                            InventoryItem(
                                id = editing?.id ?: store.newInventoryId(),
                                name = name.trim(),
                                quantity = qty.toIntOrNull() ?: 0,
                                price = price.toDoubleOrNull() ?: 0.0,
                                currency = currency.trim(),
                            )
                        )
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp), color = c.ink, modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(t["save"], color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            if (editing != null) {
                TextButton(onClick = { store.deleteInventory(editing.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Delete, null, tint = c.lost, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t["delete"], color = c.lost, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** A price list of every item, tap a row to edit its price. */
@Composable
private fun PricesScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    BackHandler(onBack = onBack)
    var editing by remember { mutableStateOf<InventoryItem?>(null) }
    var search by remember { mutableStateOf("") }
    val allItems = store.inventory
    val items = allItems.filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t["prices"], fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
        }
        if (allItems.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            SearchPill(search, { search = it }, t["search_items"])
            Spacer(Modifier.height(6.dp))
        }
        if (allItems.isEmpty()) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Report, null, tint = c.muted, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t["empty_inventory_title"], color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(t["empty_inventory_desc"], color = c.muted, fontSize = 12.5.sp)
                    }
                }
            }
        } else if (items.isEmpty()) {
            Text(t["no_results"], color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp))
        } else {
            GroupCard {
                items.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = c.edge)
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = item }.padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (item.hasPrice) "${fmtMoney(item.price)}${if (item.currency.isNotBlank()) " " + item.currency else ""}" else t["no_price"],
                            color = if (item.hasPrice) c.ink else c.faint, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
    if (editing != null) InventorySheet(store, editing) { editing = null }
}

/* ---------------- sales opportunities (pipeline) ---------------- */

@Composable
private fun oppStageColor(stage: OppStage): Color {
    val c = LocalSales.current
    return when (stage) {
        OppStage.WON -> c.ok
        OppStage.LOST -> c.lost
        OppStage.POSTPONED -> c.faint
        OppStage.QUOTE, OppStage.NEGOTIATION -> c.ink
        OppStage.NEW, OppStage.QUALIFYING, OppStage.NEED, OppStage.SOLUTION -> c.ink2
    }
}

@Composable
private fun OpportunitiesScreen(store: Store, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    BackHandler(onBack = onBack)
    var editing by remember { mutableStateOf<Opportunity?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var board by remember { mutableStateOf(false) }   // list vs Kanban board
    val opps = store.opportunities
    var filter by remember { mutableStateOf("ALL") }   // ALL | ACTIVE | WON | LOST | POSTPONED
    val openValue = opps.filter { it.stageEnum().isActive }.sumOf { it.value }
    val wonCount = opps.count { it.stageEnum() == OppStage.WON }
    val shown = opps.filter { o ->
        when (filter) {
            "ACTIVE" -> o.stageEnum().isActive
            "WON" -> o.stageEnum() == OppStage.WON
            "LOST" -> o.stageEnum() == OppStage.LOST
            "POSTPONED" -> o.stageEnum() == OppStage.POSTPONED
            else -> true
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
                Spacer(Modifier.width(12.dp))
                Text(t["opportunities"], fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink, modifier = Modifier.weight(1f))
                if (opps.isNotEmpty()) CircleBtn(if (board) AppIcons.Report else AppIcons.Chart, t["opp_view_toggle"]) { board = !board }
            }

            if (opps.isEmpty()) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Chart, null, tint = c.muted, modifier = Modifier.size(23.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(t["empty_opps_title"], color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(t["empty_opps_desc"], color = c.muted, fontSize = 12.5.sp)
                        }
                    }
                }
            } else {
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat(opps.size.toString(), t["opps_total"])
                        Stat(if (openValue > 0) fmtMoney(openValue) else "0", t["open_value"])
                        Stat(wonCount.toString(), t["stage_won"])
                    }
                }
                // Quick filter: All / Active / Won / Lost / Postponed.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("ALL" to t["all"], "ACTIVE" to t["opp_active"], "WON" to t["stage_won"], "LOST" to t["stage_lost"], "POSTPONED" to t["stage_postponed"])
                        .forEach { (key, label) -> ChoiceChip(label, filter == key) { filter = key } }
                }
                if (board) {
                    // Kanban board: one column per stage (that has cards), scrolls horizontally.
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OppStage.values().forEach { stage ->
                            val group = shown.filter { it.stageEnum() == stage }
                            if (group.isNotEmpty()) {
                                Column(Modifier.width(240.dp)) {
                                    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(oppStageColor(stage)))
                                        Spacer(Modifier.width(8.dp))
                                        Text("${stage.label(t.en)} · ${group.size}", color = c.ink2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    val stageValue = group.sumOf { it.value }
                                    if (stageValue > 0) Text(fmtMoney(stageValue), color = c.muted, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        group.forEach { o -> OpportunityRow(o) { editing = o; sheetOpen = true } }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Pipeline: grouped by stage.
                    OppStage.values().forEach { stage ->
                        val group = shown.filter { it.stageEnum() == stage }
                        if (group.isNotEmpty()) {
                            Row(Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(oppStageColor(stage)))
                                Spacer(Modifier.width(8.dp))
                                Text("${stage.label(t.en)} · ${group.size}", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                group.forEach { o -> OpportunityRow(o) { editing = o; sheetOpen = true } }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(120.dp))
        }
        Fab(
            onClick = { editing = null; sheetOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 24.dp),
        )
    }

    if (sheetOpen) OpportunitySheet(store, editing) { sheetOpen = false }
}

@Composable
private fun OpportunityRow(o: Opportunity, onClick: () -> Unit) {
    val c = LocalSales.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp), color = c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.title, color = c.ink, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOf(o.customerName, o.nextStep).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(sub, color = c.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (o.value > 0) {
                Spacer(Modifier.width(10.dp))
                Text("${fmtMoney(o.value)}${if (o.currency.isNotBlank()) " " + o.currency else ""}", color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun OpportunitySheet(store: Store, editing: Opportunity?, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState()
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var customer by remember { mutableStateOf(editing?.customerName ?: "") }
    var value by remember { mutableStateOf(editing?.value?.takeIf { it > 0.0 }?.let { fmtMoney(it) } ?: "") }
    var currency by remember { mutableStateOf((editing?.currency ?: "").ifBlank { store.defaultCurrency }) }
    var stage by remember { mutableStateOf(editing?.stageEnum() ?: OppStage.NEW) }
    var nextStep by remember { mutableStateOf(editing?.nextStep ?: "") }
    var nextDate by remember { mutableStateOf(editing?.nextDate ?: "") }
    var notes by remember { mutableStateOf(editing?.notes ?: "") }
    // Qualification fields (2.3)
    var product by remember { mutableStateOf(editing?.product ?: "") }
    var need by remember { mutableStateOf(editing?.need ?: "") }
    var problem by remember { mutableStateOf(editing?.problem ?: "") }
    var budgetStatus by remember { mutableStateOf(editing?.budgetStatus ?: "UNKNOWN") }
    var budgetAmount by remember { mutableStateOf(editing?.budgetAmount?.takeIf { it > 0.0 }?.let { fmtMoney(it) } ?: "") }
    var closeDate by remember { mutableStateOf(editing?.closeDate ?: "") }
    var competitor by remember { mutableStateOf(editing?.competitor ?: "") }
    var blocker by remember { mutableStateOf(editing?.blocker ?: "") }
    var lossReason by remember { mutableStateOf(editing?.lossReason ?: "") }
    var lossDeclared by remember { mutableStateOf(editing?.lossReasonDeclared ?: false) }
    val decisionIds = remember { mutableStateListOf<String>().apply { addAll(editing?.decisionContactIds.orEmpty()) } }
    var pickerOpen by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showClose by remember { mutableStateOf(false) }

    // The linked customer's contacts, so decision people can be tied to THIS deal.
    val linkedContacts = store.customerFor(customer)?.contacts.orEmpty().filter { it.id.isNotBlank() }
    val hasDecisionMaker = linkedContacts.any { it.id in decisionIds && DecisionRole.DECISION_MAKER.name in it.roles }
    val missing = Opportunity(id = "", title = "", need = need, product = product, closeDate = closeDate, budgetStatus = budgetStatus)
        .missingForQuote(hasDecisionMaker)
    val missingLabel = mapOf(
        "need" to t["opp_need"], "product" to t["opp_product"], "decision_maker" to t["decision_maker"],
        "timing" to t["opp_close_date"], "budget" to t["opp_budget"],
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.edge))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) t["new_opp"] else t["edit_opp"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.weight(1f))
                CircleBtn(CloseXIcon, t["done"]) { onDismiss() }
            }
            LabeledBlock(t["opp_title"]) { Input(title, { title = it }, t["opp_title_hint"]) }
            LabeledBlock(t["customer_name"]) {
                Surface(onClick = { pickerOpen = true }, shape = RoundedCornerShape(12.dp), color = c.sunk, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Person, null, tint = c.ink2, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(customer.ifBlank { t["choose_customer"] }, color = if (customer.isBlank()) c.faint else c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (customer.isNotBlank()) Box(Modifier.clip(CircleShape).clickable { customer = "" }.padding(3.dp)) {
                            Icon(CloseXIcon, null, tint = c.faint, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { LabeledBlock(t["opp_value"]) { Input(value, { value = it.filter { ch -> ch.isDigit() || ch == '.' } }, "0", kb = KeyboardType.Number) } }
                Box(Modifier.weight(1f)) { LabeledBlock(t["currency"]) { Input(currency, { currency = it }, t["currency_hint"]) } }
            }
            LabeledBlock(t["stage"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OppStage.values().forEach { s -> ChoiceChip(s.label(t.en), stage == s, oppStageColor(s)) { stage = s } }
                }
            }

            // Qualification.
            LabeledBlock(t["opp_product"]) { Input(product, { product = it }, t["opp_product_hint"]) }
            LabeledBlock(t["opp_need"]) { Input(need, { need = it }, t["opp_need_hint"]) }
            LabeledBlock(t["opp_problem"]) { Input(problem, { problem = it }, t["opp_problem_hint"]) }
            LabeledBlock(t["opp_budget"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetStatus.values().forEach { b -> ChoiceChip(b.label(t.en), budgetStatus == b.name) { budgetStatus = b.name } }
                }
            }
            if (budgetStatus == "HAS_BUDGET") {
                LabeledBlock(t["opp_budget_amount"]) { Input(budgetAmount, { budgetAmount = it.filter { ch -> ch.isDigit() || ch == '.' } }, "0", kb = KeyboardType.Number) }
            }
            PickerField(t["opp_close_date"], if (closeDate.isBlank()) t["none"] else fullDay(closeDate), Modifier.fillMaxWidth().padding(bottom = 15.dp)) { showClose = true }
            LabeledBlock(t["opp_competitor"]) { Input(competitor, { competitor = it }, t["opp_competitor_hint"]) }
            LabeledBlock(t["opp_blocker"]) { Input(blocker, { blocker = it }, t["opp_blocker_hint"]) }

            // Decision people on this deal (from the linked customer's contacts).
            if (linkedContacts.isNotEmpty()) LabeledBlock(t["opp_decision_people"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    linkedContacts.forEach { cp ->
                        val label = cp.name.ifBlank { cp.phone }.let { n -> if (cp.jobTitle.isNotBlank()) "$n · ${cp.jobTitle}" else n }
                        val on = cp.id in decisionIds
                        ChoiceChip(label, on) { if (on) decisionIds.remove(cp.id) else decisionIds.add(cp.id) }
                    }
                }
            }

            // Nudge: what's still missing before a proper quote (non-blocking).
            if (missing.isNotEmpty() && (stage == OppStage.QUOTE || stage == OppStage.NEGOTIATION || stage == OppStage.WON)) {
                Surface(shape = RoundedCornerShape(12.dp), color = c.sunk, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(AppIcons.Plan, null, tint = c.lost, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${t["opp_missing_prefix"]} " + missing.joinToString("، ") { missingLabel[it] ?: it },
                            color = c.ink2, fontSize = 12.5.sp, lineHeight = 17.sp,
                        )
                    }
                }
            }
            // Nudge: an active deal should have a dated next step.
            if (stage.isActive && nextDate.isBlank()) {
                Text(t["opp_needs_next"], color = c.lost, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            LabeledBlock(t["next_step"]) { Input(nextStep, { nextStep = it }, t["next_hint"]) }
            PickerField(t["follow_date"], if (nextDate.isBlank()) t["none"] else fullDay(nextDate), Modifier.fillMaxWidth().padding(bottom = 15.dp)) { showDate = true }

            // Loss reason — only when the deal is being marked lost.
            if (stage == OppStage.LOST) {
                LabeledBlock(t["opp_loss_reason"]) { Input(lossReason, { lossReason = it }, t["opp_loss_reason_hint"]) }
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(t["loss_declared"], lossDeclared) { lossDeclared = true }
                    ChoiceChip(t["loss_inferred"], !lossDeclared) { lossDeclared = false }
                }
            }

            LabeledBlock(t["customer_notes"]) { Input(notes, { notes = it }, t["customer_notes_hint"]) }

            // Stage history (read-only movement log).
            val hist = editing?.history.orEmpty()
            if (hist.isNotEmpty()) {
                Text(t["opp_history"], color = c.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    hist.takeLast(8).reversed().forEach { h ->
                        val fromL = mapLegacyStage(h.from).label(t.en); val toL = mapLegacyStage(h.to).label(t.en)
                        Text("• $fromL → $toL · ${if (h.at.isNotBlank()) fullDay(h.at) else ""}", color = c.muted, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = {
                    if (title.isNotBlank()) {
                        val base = Opportunity(
                            id = editing?.id ?: store.newOpportunityId(),
                            title = title.trim(), customerName = customer.trim(),
                            customerId = editing?.customerId.orEmpty(),   // upsertOpportunity backfills from the name
                            value = value.toDoubleOrNull() ?: 0.0, currency = currency.trim(),
                            stage = editing?.stage ?: OppStage.NEW.name,  // keep the stored stage; withStage records any change
                            owner = editing?.owner.orEmpty(),
                            nextStep = nextStep.trim(), nextDate = nextDate,
                            notes = notes.trim(), createdAt = editing?.createdAt?.ifBlank { todayIso() } ?: todayIso(),
                            product = product.trim(), need = need.trim(), problem = problem.trim(),
                            budgetStatus = budgetStatus, budgetAmount = budgetAmount.toDoubleOrNull() ?: 0.0,
                            closeDate = closeDate, competitor = competitor.trim(), blocker = blocker.trim(),
                            lossReason = if (stage == OppStage.LOST) lossReason.trim() else editing?.lossReason.orEmpty(),
                            lossReasonDeclared = lossDeclared,
                            stageChangedAt = editing?.stageChangedAt.orEmpty(),
                            lastInteraction = editing?.lastInteraction.orEmpty(),
                            decisionContactIds = decisionIds.toList(),
                            history = editing?.history.orEmpty(),
                        )
                        val finalOpp = base.withStage(stage, todayIso(), by = base.owner)
                        store.upsertOpportunity(finalOpp)
                        // 3.4: closing a deal auto-cancels its future open follow-ups.
                        if (finalOpp.stageEnum() == OppStage.WON || finalOpp.stageEnum() == OppStage.LOST) store.onOpportunityClosed(finalOpp)
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp), color = c.ink, modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(t["save"], color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            if (editing != null) {
                TextButton(onClick = { store.deleteOpportunity(editing.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Delete, null, tint = c.lost, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t["delete"], color = c.lost, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (pickerOpen) CustomerPickerSheet(store, customer, onPick = { customer = it; pickerOpen = false }, onDismiss = { pickerOpen = false })
    if (showDate) DatePick(nextDate.ifBlank { todayIso() }) { nextDate = it; showDate = false }
    if (showClose) DatePick(closeDate.ifBlank { todayIso() }) { closeDate = it; showClose = false }
}

/* ---------------- team / company (Phase 4 foundation) ---------------- */

@Composable
private fun TeamScreen(team: CompanyRepository, account: CloudAccount, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    val myName = account.profile.name.ifBlank { account.user?.email?.substringBefore('@').orEmpty() }
    val myEmail = account.user?.email.orEmpty()
    val myUid = account.user?.uid.orEmpty()

    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 22.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t["team"], fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
        }

        if (team.companyId == null) {
            // Not in a company yet: create one (manager) or join with a code (rep).
            var newName by remember { mutableStateOf("") }
            var code by remember { mutableStateOf("") }
            Text(t["team_intro"], color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
            Card {
                SectionTitle(t["create_company"])
                Input(newName, { newName = it }, t["company_name_hint"])
                Spacer(Modifier.height(10.dp))
                PrimaryButton(t["create_company"], enabled = newName.isNotBlank() && !team.busy) {
                    team.createCompany(newName, myName, myEmail)
                }
            }
            Card {
                SectionTitle(t["join_company"])
                Input(code, { code = it.uppercase().take(6) }, t["invite_code_hint"])
                Spacer(Modifier.height(10.dp))
                PrimaryButton(t["join_company"], enabled = code.isNotBlank() && !team.busy) {
                    team.joinByCode(code, myName, myEmail)
                }
            }
            if (team.error != null) Text(
                when (team.error) {
                    "invalid_code" -> t["invalid_code"]
                    "last_manager" -> t["last_manager"]
                    else -> t["team_error"]
                },
                color = c.lost, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            )
        } else {
            Card {
                Text(team.companyName, color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(if (team.isManager) t["role_manager"] else t["role_rep"], color = c.muted, fontSize = 13.sp)
                if (team.isManager && team.inviteCode.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        onClick = { clip.setText(AnnotatedString(team.inviteCode)); Toast.makeText(ctx, t["copied"], Toast.LENGTH_SHORT).show() },
                        shape = RoundedCornerShape(14.dp), color = c.sunk, modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t["invite_code"], color = c.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                Text(team.inviteCode, color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                            }
                            Icon(AppIcons.Copy, t["copy"], tint = c.ink2, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Text(
                "${t["team_members"]} · ${team.members.size}",
                color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 4.dp),
            )
            GroupCard {
                team.members.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = c.edge)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
                            Text(profileInitials(m.name.ifBlank { m.email }), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name.ifBlank { m.email.substringBefore('@') } + (if (m.uid == myUid) " · ${t["you"]}" else ""),
                                color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (m.role == TeamRole.MANAGER) t["role_manager"] else t["role_rep"], color = c.muted, fontSize = 12.sp)
                        }
                        // A manager can toggle another member's role (backend-enforced by rules).
                        if (team.isManager && m.uid != myUid) {
                            Surface(
                                onClick = { team.setMemberRole(m.uid, if (m.role == TeamRole.MANAGER) TeamRole.REP else TeamRole.MANAGER) },
                                shape = RoundedCornerShape(999.dp), color = c.sunk,
                            ) {
                                Text(
                                    if (m.role == TeamRole.MANAGER) t["make_rep"] else t["make_manager"],
                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = c.ink2, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // --- Manager dashboard (4.3): team goal, summary, per-rep breakdown ---
            var assignOpen by remember { mutableStateOf(false) }
            var reassignFor by remember { mutableStateOf<Assignment?>(null) }
            var teamGoalDialog by remember { mutableStateOf(false) }
            var repFilter by remember { mutableStateOf<String?>(null) }   // rep name filter (null = all)
            var statusFilter by remember { mutableStateOf(0) }            // 0 all · 1 pending · 2 done
            var shareOppOpen by remember { mutableStateOf(false) }        // share-to-team dialog (6.2)

            if (team.isManager) {
                val total = team.assignments.size
                val doneN = team.assignments.count { it.done }
                Text(t["dashboard"], color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 4.dp))
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t["team_goal"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Surface(onClick = { teamGoalDialog = true }, shape = RoundedCornerShape(999.dp), color = c.sunk) {
                            Text(
                                if (team.teamGoal > 0) "$doneN / ${team.teamGoal}" else t["set_goal"],
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = c.ink2, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (team.teamGoal > 0) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (doneN.toFloat() / team.teamGoal).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)),
                            color = c.ink, trackColor = c.sunk,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Stat(total.toString(), t["assigned_total"])
                        Stat(doneN.toString(), t["completed"])
                        Stat((total - doneN).toString(), t["remaining"])
                    }
                }
                // Per-rep breakdown
                val stats = team.repStats().filter { it.second > 0 }
                if (stats.isNotEmpty()) {
                    Card {
                        SectionTitle(t["by_rep"])
                        stats.forEach { (repName, assigned, done) ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(repName, color = c.ink2, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Box(Modifier.width(90.dp).height(7.dp).clip(RoundedCornerShape(99.dp)).background(c.sunk)) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth((done.toFloat() / assigned).coerceIn(0f, 1f)).clip(RoundedCornerShape(99.dp)).background(c.ink))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text("$done/$assigned", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // --- Work distribution board (4.2) ---
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t["work_distribution"], color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (team.isManager) Surface(onClick = { assignOpen = true }, shape = RoundedCornerShape(999.dp), color = c.ink) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Add, null, tint = c.onInk, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(t["assign_work"], color = c.onInk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // Filters (managers): by rep and by status.
            if (team.isManager && team.assignments.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceChip(t["seg_all"], repFilter == null) { repFilter = null }
                    team.members.forEach { m ->
                        val nm = m.name.ifBlank { m.email.substringBefore('@') }
                        ChoiceChip(nm, repFilter == nm) { repFilter = nm }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceChip(t["seg_all"], statusFilter == 0) { statusFilter = 0 }
                    ChoiceChip(t["seg_todo"], statusFilter == 1) { statusFilter = 1 }
                    ChoiceChip(t["seg_done"], statusFilter == 2) { statusFilter = 2 }
                }
            }
            // Managers see the whole board (filtered); reps see only their own work.
            val shownAssignments = (if (team.isManager) team.assignments else team.myAssignments())
                .filter { repFilter == null || it.assignedToName == repFilter }
                .filter { statusFilter == 0 || (statusFilter == 1 && !it.done) || (statusFilter == 2 && it.done) }
            if (shownAssignments.isEmpty()) {
                Text(t["no_assignments"], color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
            } else {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    shownAssignments.forEach { a ->
                        AssignmentRow(
                            a = a, isManager = team.isManager, ctx = ctx,
                            onToggle = { team.setAssignmentDone(a.id, !a.done) },
                            onReassign = { reassignFor = a },
                            onDelete = { team.deleteAssignment(a.id) },
                        )
                    }
                }
            }

            // ---- Team pipeline: shared opportunities visible to the whole team (6.2) ----
            val myUid = account.user?.uid.orEmpty()
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${t["team_pipeline"]} · ${team.teamOpps.size}", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(onClick = { shareOppOpen = true }, shape = RoundedCornerShape(999.dp), color = c.ink) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Add, null, tint = c.onInk, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(t["team_share_opp"], color = c.onInk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (team.teamOpps.isEmpty()) {
                Text(t["team_pipeline_empty"], color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
            } else {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    team.teamOpps.forEach { o ->
                        Surface(shape = RoundedCornerShape(14.dp), color = c.surface, border = androidx.compose.foundation.BorderStroke(1.dp, c.edge)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(o.title, color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val sub = listOf(o.customerName, o.ownerName.ifBlank { null }?.let { "${t["owner"]}: $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                                    if (sub.isNotBlank()) Text(sub, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (o.value > 0) Text("${fmtMoney(o.value)}${if (o.currency.isNotBlank()) " " + o.currency else ""}", color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                // Only the owner or a manager can delete (backend also enforces this).
                                if (team.isManager || o.ownerUid == myUid) {
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(onClick = { team.deleteTeamOpportunity(o.id) }, modifier = Modifier.size(28.dp)) {
                                        Icon(AppIcons.Delete, t["delete"], tint = c.faint, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- Shared customers: the company-wide customer book (6.2) ----
            Text("${t["team_customers"]} · ${team.teamCustomers.size}", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 22.dp, end = 18.dp, top = 16.dp, bottom = 4.dp))
            if (team.teamCustomers.isEmpty()) {
                Text(t["team_customers_empty"], color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
            } else {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    team.teamCustomers.forEach { tc ->
                        Surface(shape = RoundedCornerShape(14.dp), color = c.surface, border = androidx.compose.foundation.BorderStroke(1.dp, c.edge)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tc.name, color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val sub = listOf(tc.city, tc.industry, tc.ownerName.ifBlank { null }?.let { "${t["owner"]}: $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                                    if (sub.isNotBlank()) Text(sub, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (splitPhone(tc.phone).second.isNotBlank()) IconButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + tc.phone.trim()))) } }, modifier = Modifier.size(28.dp)) {
                                    Icon(AppIcons.Phone, t["call"], tint = c.ink2, modifier = Modifier.size(16.dp))
                                }
                                if (team.isManager || tc.ownerUid == myUid) {
                                    IconButton(onClick = { team.deleteTeamCustomer(tc.id) }, modifier = Modifier.size(28.dp)) {
                                        Icon(AppIcons.Delete, t["delete"], tint = c.faint, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text(t["team_customers_hint"], color = c.faint, fontSize = 11.5.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))

            if (assignOpen) AssignmentSheet(team, team.members) { assignOpen = false }
            if (shareOppOpen) ShareOppSheet { title, cust, value, cur ->
                team.shareOpportunity(title, cust, value, cur); shareOppOpen = false
            }
            reassignFor?.let { a ->
                RepPickerSheet(team.members, t["reassign"]) { m ->
                    if (m != null) team.reassign(a.id, m.uid, m.name.ifBlank { m.email })
                    reassignFor = null
                }
            }
            if (teamGoalDialog) {
                var g by remember { mutableStateOf(team.teamGoal.takeIf { it > 0 }?.toString() ?: "") }
                AlertDialog(
                    onDismissRequest = { teamGoalDialog = false },
                    confirmButton = { TextButton(onClick = { team.updateTeamGoal(g.toIntOrNull() ?: 0); teamGoalDialog = false }) { Text(t["save"], color = c.ink) } },
                    dismissButton = { TextButton(onClick = { teamGoalDialog = false }) { Text(t["cancel"], color = c.muted) } },
                    title = { Text(t["team_goal"]) },
                    text = {
                        Column {
                            Text(t["team_goal_desc"], color = c.muted, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            Input(g, { g = it.filter { ch -> ch.isDigit() }.take(4) }, t["team_goal"], kb = KeyboardType.Number)
                        }
                    },
                    containerColor = c.surface,
                )
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = { team.leave() },
                shape = RoundedCornerShape(24.dp), color = c.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(52.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(t["leave_company"], color = c.lost, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun AssignmentRow(
    a: Assignment, isManager: Boolean, ctx: android.content.Context,
    onToggle: () -> Unit, onReassign: () -> Unit, onDelete: () -> Unit,
) {
    val c = LocalSales.current
    val t = LocalL.current
    Surface(
        shape = RoundedCornerShape(16.dp), color = if (a.done) c.sunk else c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                    .background(if (a.done) c.ink else Color.Transparent)
                    .border(2.dp, if (a.done) c.ink else c.edge, RoundedCornerShape(7.dp))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) { if (a.done) Icon(CheckIcon, null, tint = c.onInk, modifier = Modifier.size(13.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    a.customerName, color = if (a.done) c.muted else c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    textDecoration = if (a.done) TextDecoration.LineThrough else null, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(a.assignedToName.takeIf { it.isNotBlank() }, a.note.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (a.phone.isNotBlank()) IconButton(onClick = {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + a.phone.trim()))) }
            }) { Icon(AppIcons.Phone, t["call"], tint = c.ink2, modifier = Modifier.size(18.dp)) }
            if (a.address.isNotBlank()) IconButton(onClick = {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, mapsRouteUri(listOf(a.address)))) }
            }) { Icon(AppIcons.Directions, t["navigate"], tint = c.ink2, modifier = Modifier.size(18.dp)) }
            if (isManager) {
                IconButton(onClick = onReassign) { Icon(AppIcons.People, t["reassign"], tint = c.ink2, modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onDelete) { Icon(AppIcons.Delete, t["delete"], tint = c.faint, modifier = Modifier.size(17.dp)) }
            }
        }
    }
}

/** Share a deal to the team pipeline (6.2). Plain inputs; the repo tags it with the sharer as owner. */
@Composable
private fun ShareOppSheet(onShare: (String, String, Double, String) -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState()
    var title by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { onShare("", "", 0.0, "") }, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(t["team_share_opp"], fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
            LabeledBlock(t["opp_title"]) { Input(title, { title = it }, t["opp_title_hint"]) }
            LabeledBlock(t["customer_name"]) { Input(customer, { customer = it }, t["customer_name"]) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { LabeledBlock(t["opp_value"]) { Input(value, { value = it.filter { ch -> ch.isDigit() || ch == '.' } }, "0", kb = KeyboardType.Number) } }
                Box(Modifier.weight(1f)) { LabeledBlock(t["currency"]) { Input(currency, { currency = it }, t["currency_hint"]) } }
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton(t["team_share_opp"], enabled = title.isNotBlank()) {
                onShare(title.trim(), customer.trim(), value.toDoubleOrNull() ?: 0.0, currency.trim())
            }
        }
    }
}

@Composable
private fun AssignmentSheet(team: CompanyRepository, members: List<TeamMember>, onDismiss: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var rep by remember { mutableStateOf(members.firstOrNull { it.role == TeamRole.REP } ?: members.firstOrNull()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.edge))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t["assign_work"], fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.weight(1f))
                CircleBtn(CloseXIcon, t["done"]) { onDismiss() }
            }
            LabeledBlock(t["customer_name"]) { Input(name, { name = it }, t["client_name_hint"]) }
            LabeledBlock(t["phone"]) { PhoneField(phone) { phone = it } }
            LabeledBlock(t["address"]) { Input(address, { address = it }, t["address_hint"]) }
            LabeledBlock(t["note"]) { Input(note, { note = it }, t["note"]) }
            LabeledBlock(t["assign_to"]) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    members.forEach { m ->
                        ChoiceChip(m.name.ifBlank { m.email.substringBefore('@') }, rep?.uid == m.uid) { rep = m }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = {
                    val r = rep
                    if (name.isNotBlank() && r != null) {
                        team.addAssignment(name, phone, address, note, r.uid, r.name.ifBlank { r.email })
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp), color = c.ink, modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(t["assign_work"], color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun RepPickerSheet(members: List<TeamMember>, title: String, onPick: (TeamMember?) -> Unit) {
    val c = LocalSales.current
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = { onPick(null) }, sheetState = sheetState, containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.edge))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.padding(vertical = 10.dp))
            members.forEach { m ->
                Surface(onClick = { onPick(m) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
                            Text(profileInitials(m.name.ifBlank { m.email }), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(m.name.ifBlank { m.email.substringBefore('@') }, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalSales.current
    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(14.dp), color = if (enabled) c.ink else c.sunk,
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = if (enabled) c.onInk else c.faint, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/* ---------------- account + profile ---------------- */

@Composable
private fun ProfileScreen(
    store: Store,
    account: CloudAccount?,
    onSettings: () -> Unit,
    onEdit: () -> Unit,
    onCustomers: () -> Unit,
    onTools: () -> Unit,
    onShareCard: () -> Unit,
) {
    val t = LocalL.current
    FrostedScaffold(header = { Header(t["profile"], null, mark = false) }) {
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
            SignedInProfile(store, account, onSettings, onEdit, onCustomers, onTools, onShareCard)
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
private fun SignedInProfile(store: Store, account: CloudAccount, onSettings: () -> Unit, onEdit: () -> Unit, onCustomers: () -> Unit, onTools: () -> Unit, onShareCard: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val profile = account.profile
    val email = account.user?.email.orEmpty()
    val name = profile.name.ifBlank { email.substringBefore('@') }
    val role = listOf(profile.jobTitle, profile.company).filter { it.isNotBlank() }.joinToString(" · ")

    // Header sits directly on the background (no card), centered on the screen.
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val photo = store.profilePhotoPath
        val photoBmp = remember(photo) { if (photo.isNotBlank()) loadImageBitmap(photo, 240) else null }
        Box(
            Modifier.size(92.dp).clip(CircleShape).background(c.ink).border(3.dp, c.ok, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photoBmp != null) Image(photoBmp, null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            else Text(profileInitials(name), color = c.onInk, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(14.dp))
        Text(name, color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current)
        if (role.isNotBlank()) Text(role, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        // Inline stats under the name, separated by a dot (like "followers · following").
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(store.visits.size.toString(), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(" ${t["stat_visits"]}", color = c.muted, fontSize = 14.sp)
            Box(Modifier.padding(horizontal = 9.dp).size(3.dp).clip(CircleShape).background(c.muted))
            Text(store.customers.size.toString(), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(" ${t["stat_clients"]}", color = c.muted, fontSize = 14.sp)
        }
    }
    Spacer(Modifier.height(8.dp))

    GroupCard {
        SettingsRow(AppIcons.Share, t["share_card"]) { onShareCard() }
        HorizontalDivider(color = c.edge)
        SettingsRow(AppIcons.Person, t["edit_profile"]) { onEdit() }
        HorizontalDivider(color = c.edge)
        SettingsRow(AppIcons.People, t["customers"]) { onCustomers() }
    }

    GroupCard {
        SettingsRow(AppIcons.Inbox, t["tools"]) { onTools() }
        HorizontalDivider(color = c.edge)
        SettingsRow(AppIcons.Settings, t["settings"]) { onSettings() }
    }

    Spacer(Modifier.height(10.dp))
    Surface(
        onClick = { account.signOut() },
        shape = RoundedCornerShape(24.dp), color = c.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.edge),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(54.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(t["sign_out"], color = c.lost, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ProfileEditorScreen(store: Store, account: CloudAccount, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val original = account.profile
    var name by remember(original) { mutableStateOf(original.name) }
    var job by remember(original) { mutableStateOf(original.jobTitle) }
    var company by remember(original) { mutableStateOf(original.company) }
    var phone by remember(original) { mutableStateOf(original.phone) }
    var companyEmail by remember(original) { mutableStateOf(original.companyEmail) }
    var shareEmail by remember(original) { mutableStateOf(original.shareLoginEmail) }
    val accountEmail = account.user?.email.orEmpty()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) copyImageToFiles(ctx, uri)?.let { store.setProfilePhoto(it) }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["cancel"]) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t["edit_profile"], color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    account.saveProfile(UserProfile(name, job, company, phone.takeIf { splitPhone(it).second.isNotBlank() } ?: "", companyEmail, shareEmail))
                    onBack()
                }
            }) { Text(t["done"], color = c.ink, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            // Tappable avatar: pick a real photo, or clear it back to initials.
            Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                val photo = store.profilePhotoPath
                val bmp = remember(photo) { if (photo.isNotBlank()) loadImageBitmap(photo, 240) else null }
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).background(c.ink).border(3.dp, c.ok, CircleShape)
                            .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bmp != null) Image(bmp, null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        else Text(profileInitials(name), color = c.onInk, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(c.surface).border(1.dp, c.edge, CircleShape)
                            .clickable { if (photo.isNotBlank()) store.setProfilePhoto("") else photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (photo.isNotBlank()) CloseXIcon else AppIcons.Image, t["change_photo"], tint = c.ink2, modifier = Modifier.size(15.dp)) }
                }
            }
            Field(t["full_name"], name, { name = it }, t["full_name"])
            Field(t["job_title"], job, { job = it }, t["job_title_hint"])
            Field(t["company"], company, { company = it }, t["company_hint"])
            LabeledBlock(t["company_email"]) { Input(companyEmail, { companyEmail = it }, t["company_email_hint"], kb = KeyboardType.Email) }
            LabeledBlock(t["phone"]) { PhoneField(phone) { phone = it } }

            if (accountEmail.isNotBlank()) {
                LabeledBlock(t["account_email"]) {
                    Surface(shape = RoundedCornerShape(12.dp), color = c.sunk, modifier = Modifier.fillMaxWidth()) {
                        Text(accountEmail, Modifier.padding(horizontal = 14.dp, vertical = 16.dp), color = c.ink, fontSize = 15.sp)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t["share_account_email"], color = c.ink2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(checked = shareEmail, onCheckedChange = { shareEmail = it })
                }
            }
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
private fun SettingsScreen(store: Store, account: CloudAccount?, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 22.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(t["settings"], fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = LocalDisplayFont.current, color = c.ink)
        }

        GroupCard {
            ThemeSwitchRow()
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Palette, t["color_style"], value = t["palette_${store.palette}"]) { sheet = "colorstyle" }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Language, t["language"], value = t["lang_${store.langMode}"]) { sheet = "language" }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Calendar, t["week_start"], value = t["day_${store.weekStartDay.take(3).lowercase()}"]) { sheet = "week" }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Report, t["report_lang"], value = t["lang_${store.reportLang}"]) { sheet = "reportlang" }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Notifications, t["reminder_time"], value = fmtTime(store.reminderTime)) { sheet = "remindertime" }
            HorizontalDivider(color = c.edge)
            SettingsRow(AppIcons.Chart, t["default_currency"], value = store.defaultCurrency) { sheet = "currency" }
            HorizontalDivider(color = c.edge)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Plan, null, tint = c.ink2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t["smart_automation"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(t["smart_automation_desc"], color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Switch(checked = store.smartAutomation, onCheckedChange = { store.chooseSmartAutomation(it) })
            }
        }

        if (account?.user != null) {
            GroupCard {
                SettingsRow(AppIcons.Update, t["cloud_sync"], value = t["sync_${account.syncState}"]) {}
            }
        }

        DriveSection(store)

        AiSection(store)

        BackupSection(store)

        UpdateSection()

        GroupCard {
            SettingsRow(AppIcons.Delete, t["delete_all"], danger = true) { confirmClear = true }
        }

        GroupCard {
            SettingsRow(AppIcons.Info, t["about"], value = "${t["version"]} ${AppUpdater.currentVersionName(ctx)}") {}
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

    if (sheet == "remindertime") TimePick(store.reminderTime) { store.chooseReminderTime(it); sheet = null }

    when (sheet) {
        "colorstyle" -> SettingsSheet(t["color_style"], { sheet = null }) {
            SheetLabel(t["color_style"])
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip(t["palette_classic"], store.palette == "classic", Modifier.weight(1f)) { store.choosePalette("classic") }
                ThemeChip(t["palette_warm"], store.palette == "warm", Modifier.weight(1f)) { store.choosePalette("warm") }
            }
        }
        "language" -> SettingsSheet(t["language"], { sheet = null }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip(t["lang_auto"], store.langMode == "auto", Modifier.weight(1f)) { store.chooseLang("auto") }
                ThemeChip(t["lang_ar"], store.langMode == "ar", Modifier.weight(1f)) { store.chooseLang("ar") }
                ThemeChip(t["lang_en"], store.langMode == "en", Modifier.weight(1f)) { store.chooseLang("en") }
            }
        }
        "currency" -> SettingsSheet(t["default_currency"], { sheet = null }) {
            SheetLabel(t["default_currency_desc"])
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SAR", "AED", "EGP", "USD", "EUR", "KWD", "QAR", "BHD", "OMR", "JOD").forEach { code ->
                    ChoiceChip(code, store.defaultCurrency == code) { store.chooseCurrency(code) }
                }
            }
            Spacer(Modifier.height(14.dp))
            SheetLabel(t["custom_currency"])
            Input(store.defaultCurrency, { store.chooseCurrency(it) }, "SAR")
        }
        "reportlang" -> SettingsSheet(t["report_lang"], { sheet = null }) {
            SheetLabel(t["report_lang_desc"])
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip(t["lang_en"], store.reportLang == "en", Modifier.weight(1f)) { store.chooseReportLang("en") }
                ThemeChip(t["lang_ar"], store.reportLang == "ar", Modifier.weight(1f)) { store.chooseReportLang("ar") }
            }
        }
        "week" -> SettingsSheet(t["week_start"], { sheet = null }) {
            val days = listOf(
                "SATURDAY" to t["day_sat"], "SUNDAY" to t["day_sun"], "MONDAY" to t["day_mon"],
                "TUESDAY" to t["day_tue"], "WEDNESDAY" to t["day_wed"], "THURSDAY" to t["day_thu"],
                "FRIDAY" to t["day_fri"],
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEach { (name, label) ->
                    ChoiceChip(label, store.weekStartDay == name) { store.chooseWeekStart(name) }
                }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, fontSize = 12.5.sp, color = LocalSales.current.muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
}

/** Reusable styled bottom sheet for a settings option (handle, header with close, content). */
@Composable
private fun SettingsSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.edge))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.weight(1f))
                CircleBtn(CloseXIcon, t["done"]) { onDismiss() }
            }
            content()
        }
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

/** Connect the user's Google Drive so attachments sync across devices for free (plan 6.3). */
@Composable
private fun DriveSection(store: Store) {
    val t = LocalL.current
    val c = LocalSales.current
    val ctx = LocalContext.current
    var connected by remember { mutableStateOf(DriveSync.isConnected(ctx)) }
    var email by remember { mutableStateOf(DriveSync.connectedEmail(ctx)) }
    val storageRepo = remember { StorageRepo(ctx.applicationContext, store) }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // GoogleSignIn stores the account; re-read the connection state regardless of resultCode.
        connected = DriveSync.isConnected(ctx)
        email = DriveSync.connectedEmail(ctx)
        if (connected) {
            Toast.makeText(ctx, t["drive_connected"], Toast.LENGTH_SHORT).show()
            storageRepo.uploadPending()
        }
    }

    GroupCard {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Download, null, tint = c.ink2, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t["drive_sync"], color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(if (connected) "${t["drive_connected_as"]} $email" else t["drive_sync_desc"], color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (connected) {
                TextButton(onClick = { DriveSync.disconnect(ctx); connected = false; email = "" }) { Text(t["drive_disconnect"], color = c.lost) }
            } else {
                TextButton(onClick = { signInLauncher.launch(DriveSync.signInClient(ctx).signInIntent) }) { Text(t["drive_connect"], color = c.ink) }
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
        else if (!danger) Icon(
            if (LocalL.current.en) AppIcons.ArrowForward else AppIcons.ArrowBack,
            null, tint = c.faint, modifier = Modifier.size(20.dp),
        )
    }
}

/* ---------------- small shared ---------------- */

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalSales.current
    Surface(
        shape = RoundedCornerShape(24.dp), color = c.surface,
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
        shape = RoundedCornerShape(24.dp), color = c.surface,
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

private data class MonthBar(val label: String, val count: Int)

/** Vertical monthly bar chart: a value above each rounded bar, the current month highlighted. */
@Composable
private fun MonthlyBarsCard(title: String, bars: List<MonthBar>) {
    val c = LocalSales.current
    val max = (bars.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    val barArea = 150.dp
    Card {
        SectionTitle(title)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            bars.forEachIndexed { i, bar ->
                val last = i == bars.lastIndex
                val h = (barArea * (bar.count.toFloat() / max)).coerceAtLeast(4.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        bar.count.toString(), fontSize = 11.sp,
                        fontWeight = if (last) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (last) c.ink else c.muted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.fillMaxWidth().height(h)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (last) c.ok else c.sunk),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        bar.label, fontSize = 11.sp,
                        fontWeight = if (last) FontWeight.Bold else FontWeight.Medium,
                        color = if (last) c.ink else c.muted,
                    )
                }
            }
        }
    }
}
