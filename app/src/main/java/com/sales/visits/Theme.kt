package com.sales.visits

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Extra brand tokens beyond the Material scheme (monochrome, Easlo-style). */
data class SalesColors(
    val bg: Color,
    val surface: Color,
    val sunk: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val faint: Color,
    val edge: Color,
    val onInk: Color,
    val ok: Color,
    val lead: Color,
    val hold: Color,
    val lost: Color,
    val neutral: Color,
    val navBg: Color,
    val navFg: Color,
    val navFgDim: Color,
    val navActiveBg: Color,
    val dark: Boolean,
)

val LightSales = SalesColors(
    bg = Color(0xFFF4F4F2),
    surface = Color(0xFFFFFFFF),
    sunk = Color(0xFFECECEB),
    ink = Color(0xFF101010),
    ink2 = Color(0xFF3A3A3A),
    muted = Color(0xFF8C8C8C),
    faint = Color(0xFFB4B4B4),
    edge = Color(0x14101010),
    onInk = Color(0xFFFFFFFF),
    ok = Color(0xFF101010),
    lead = Color(0xFF5F5F5F),
    hold = Color(0xFF9A9A9A),
    lost = Color(0xFF404040),
    neutral = Color(0xFFC4C4C4),
    navBg = Color(0xFFFFFFFF),
    navFg = Color(0xFF101010),
    navFgDim = Color(0x6B101010),
    navActiveBg = Color(0x12101010),
    dark = false,
)

val DarkSales = SalesColors(
    bg = Color(0xFF0B0B0B),
    surface = Color(0xFF161616),
    sunk = Color(0xFF1F1F1F),
    ink = Color(0xFFF3F3F3),
    ink2 = Color(0xFFC2C2C2),
    muted = Color(0xFF8A8A8A),
    faint = Color(0xFF5A5A5A),
    edge = Color(0x1AFFFFFF),
    onInk = Color(0xFF0B0B0B),
    ok = Color(0xFFF3F3F3),
    lead = Color(0xFFA0A0A0),
    hold = Color(0xFF6F6F6F),
    lost = Color(0xFFC8C8C8),
    neutral = Color(0xFF4A4A4A),
    navBg = Color(0xF21C1C1C),
    navFg = Color(0xFFF3F3F3),
    navFgDim = Color(0x8CFFFFFF),
    navActiveBg = Color(0x24FFFFFF),
    dark = true,
)

val LocalSales = staticCompositionLocalOf { LightSales }

fun Outcome.color(c: SalesColors): Color = when (this) {
    Outcome.SUCCESS -> c.ok
    Outcome.LEAD -> c.lead
    Outcome.PENDING -> c.hold
    Outcome.LOST -> c.lost
    Outcome.NONE -> c.neutral
}

@Composable
fun SalesTheme(dark: Boolean, content: @Composable () -> Unit) {
    val sales = if (dark) DarkSales else LightSales
    val scheme = if (dark) darkColorScheme(
        background = sales.bg, surface = sales.surface, onBackground = sales.ink,
        onSurface = sales.ink, primary = sales.ink, onPrimary = sales.onInk,
    ) else lightColorScheme(
        background = sales.bg, surface = sales.surface, onBackground = sales.ink,
        onSurface = sales.ink, primary = sales.ink, onPrimary = sales.onInk,
    )
    CompositionLocalProvider(LocalSales provides sales) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
