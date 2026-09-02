package com.sales.visits

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val ThmanyahDisplayFont = FontFamily(
    Font(R.font.thmanyah_serif_display_medium, FontWeight.Medium),
    Font(R.font.thmanyah_serif_display_bold, FontWeight.Bold),
    Font(R.font.thmanyah_serif_display_black, FontWeight.Black),
)

val ThmanyahTextFont = FontFamily(
    Font(R.font.thmanyah_serif_text_regular, FontWeight.Normal),
    Font(R.font.thmanyah_serif_text_medium, FontWeight.Medium),
    Font(R.font.thmanyah_serif_text_bold, FontWeight.Bold),
)

val GoogleSansFlexFont = FontFamily(
    Font(R.font.google_sans_flex, FontWeight.Normal),
    Font(R.font.google_sans_flex, FontWeight.Medium),
    Font(R.font.google_sans_flex, FontWeight.SemiBold),
    Font(R.font.google_sans_flex, FontWeight.Bold),
    Font(R.font.google_sans_flex, FontWeight.ExtraBold),
)

val LocalAppFont = staticCompositionLocalOf { ThmanyahTextFont }
val LocalDisplayFont = staticCompositionLocalOf { ThmanyahDisplayFont }

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

private fun appTypography(bodyFont: FontFamily, displayFont: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = displayFont),
        displayMedium = base.displayMedium.copy(fontFamily = displayFont),
        displaySmall = base.displaySmall.copy(fontFamily = displayFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = displayFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = displayFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = displayFont),
        titleLarge = base.titleLarge.copy(fontFamily = displayFont),
        titleMedium = base.titleMedium.copy(fontFamily = bodyFont),
        titleSmall = base.titleSmall.copy(fontFamily = bodyFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = bodyFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = bodyFont),
        bodySmall = base.bodySmall.copy(fontFamily = bodyFont),
        labelLarge = base.labelLarge.copy(fontFamily = bodyFont),
        labelMedium = base.labelMedium.copy(fontFamily = bodyFont),
        labelSmall = base.labelSmall.copy(fontFamily = bodyFont),
    )
}

@Composable
fun SalesTheme(dark: Boolean, en: Boolean, content: @Composable () -> Unit) {
    val sales = if (dark) DarkSales else LightSales
    val bodyFont = if (en) GoogleSansFlexFont else ThmanyahTextFont
    val displayFont = if (en) GoogleSansFlexFont else ThmanyahDisplayFont
    val scheme = if (dark) darkColorScheme(
        background = sales.bg, surface = sales.surface, onBackground = sales.ink,
        onSurface = sales.ink, primary = sales.ink, onPrimary = sales.onInk,
    ) else lightColorScheme(
        background = sales.bg, surface = sales.surface, onBackground = sales.ink,
        onSurface = sales.ink, primary = sales.ink, onPrimary = sales.onInk,
    )
    CompositionLocalProvider(
        LocalSales provides sales,
        LocalAppFont provides bodyFont,
        LocalDisplayFont provides displayFont,
    ) {
        MaterialTheme(colorScheme = scheme, typography = appTypography(bodyFont, displayFont)) {
            ProvideTextStyle(TextStyle(fontFamily = bodyFont), content = content)
        }
    }
}
