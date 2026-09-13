package com.sales.visits

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset

/** How far the FrostedScaffold header is collapsed (0 = fully expanded large title, 1 = compact bar). */
val LocalHeaderCollapse = compositionLocalOf { 0f }

/**
 * Handle for the "poured" theme-switch animation. [dark] is the current resolved theme, and
 * [toggle] flips light↔dark with a circular ink-reveal expanding from the given window pivot.
 */
class ThemeRevealHandle(val dark: Boolean, val toggle: (Offset) -> Unit)

val LocalThemeReveal = staticCompositionLocalOf { ThemeRevealHandle(false) {} }
