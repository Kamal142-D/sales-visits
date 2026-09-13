package com.sales.visits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize

enum class Tab(val icon: ImageVector, val labelRes: String, val subRes: String) {
    VISITS(AppIcons.Visits, "visits", "empty_visits_desc"),
    TODAY(AppIcons.Plan, "today_plan", "today_sub"),
    CUSTOMERS(AppIcons.People, "customers", "empty_customers_desc"),
    OPPORTUNITIES(AppIcons.Chart, "opportunities", "insights_sub"),
    TASKS(AppIcons.Check, "tasks_tab", "today_sub"),
    QUOTES(AppIcons.Copy, "quotes", "insights_sub"),
    WEEKLY(AppIcons.Report, "weekly_review", "insights_sub"),
    REPORT(AppIcons.Report, "report_tab", "insights_sub"),
    INSIGHTS(AppIcons.Insights, "insights_tab", "insights_sub"),
    SETTINGS(AppIcons.Settings, "settings", "profile_sub"),
}

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(1120.dp, 760.dp),
        position = WindowPosition(Alignment.Center),
    )
    val store = remember { Store() }
    val cloud = remember { CloudSync(store) }
    var tab by remember { mutableStateOf(Tab.VISITS) }
    val en = store.lang == "en"
    val dark = when (store.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "VisitFlow",
        state = windowState,
    ) {
        SalesTheme(dark, en, store.palette) {
            Surface(color = LocalSales.current.bg, modifier = Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize()) {
                    NavRail(tab, { tab = it }, en)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (tab) {
                            Tab.VISITS -> VisitsScreen(store)
                            Tab.TODAY -> TodayScreen(store)
                            Tab.CUSTOMERS -> CustomersScreen(store)
                            Tab.OPPORTUNITIES -> OpportunitiesScreen(store)
                            Tab.TASKS -> TasksScreen(store)
                            Tab.QUOTES -> QuotesScreen(store)
                            Tab.WEEKLY -> WeeklyReviewScreen(store)
                            Tab.REPORT -> ReportScreen(store)
                            Tab.INSIGHTS -> InsightsScreen(store)
                            Tab.SETTINGS -> SettingsScreen(store, cloud)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRail(selected: Tab, onSelect: (Tab) -> Unit, en: Boolean) {
    val c = LocalSales.current
    val t = LocalL.current
    Column(
        Modifier
            .width(82.dp)
            .fillMaxHeight()
            .background(c.navBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.ink),
            contentAlignment = Alignment.Center,
        ) {
            Text("V", color = c.onInk, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(26.dp))
        Tab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                Modifier
                    .width(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) c.navActiveBg else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    tab.icon, null,
                    tint = if (active) c.navFg else c.navFgDim,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    t[tab.labelRes],
                    color = if (active) c.navFg else c.navFgDim,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Consistent screen header (title + optional subtitle + trailing actions). */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = LocalSales.current.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 13.sp, color = LocalSales.current.muted)
            }
        }
        trailing()
    }
}

@Composable
fun EmptyState(text: String, icon: ImageVector) {
    val c = LocalSales.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c.faint, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, color = c.muted, fontSize = 14.sp)
    }
}

@Composable
fun Chip(text: String, color: Color = LocalSales.current.muted, bg: Color = LocalSales.current.sunk) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = LocalSales.current.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

fun editLabel(en: Boolean): String = if (en) "Edit" else "تعديل"