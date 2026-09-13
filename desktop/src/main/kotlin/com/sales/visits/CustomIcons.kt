package com.sales.visits

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Microphone icon in the Solar Linear style (capsule + grille + holder + stand), matching the
 * rest of the app's iconography. Rendered via `Icon(tint = …)`, so the stroke colour is a placeholder.
 */
/** A plain checkmark (no surrounding circle), for use inside a circular button. */
val CheckIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Check", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(stroke = SolidColor(Color(0xFF000000)), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 6.5f)
        }
    }.build()
}

/** A plain X (no surrounding circle), for use inside a circular button. */
val CloseXIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CloseX", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(stroke = SolidColor(Color(0xFF000000)), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(7f, 7f); lineTo(17f, 17f)
        }
        path(stroke = SolidColor(Color(0xFF000000)), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(17f, 7f); lineTo(7f, 17f)
        }
    }.build()
}

val MicSimpleIcon: ImageVector by lazy {
    fun stroke() = SolidColor(Color(0xFF000000))
    ImageVector.Builder(
        name = "Microphone", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        // Capsule (mic body)
        path(stroke = stroke(), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Butt, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(7f, 8f)
            curveTo(7f, 5.2386f, 9.2386f, 3f, 12f, 3f)
            curveTo(14.7614f, 3f, 17f, 5.2386f, 17f, 8f)
            verticalLineTo(11f)
            curveTo(17f, 13.7614f, 14.7614f, 16f, 12f, 16f)
            curveTo(9.2386f, 16f, 7f, 13.7614f, 7f, 11f)
            verticalLineTo(8f)
            close()
        }
        // Grille lines
        path(stroke = stroke(), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(13f, 8f); lineTo(17f, 8f)
        }
        path(stroke = stroke(), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(13f, 11f); lineTo(17f, 11f)
        }
        // Holder arc
        path(stroke = stroke(), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(20f, 10f)
            verticalLineTo(11f)
            curveTo(20f, 15.4183f, 16.4183f, 19f, 12f, 19f)
            curveTo(7.5817f, 19f, 4f, 15.4183f, 4f, 11f)
            verticalLineTo(10f)
        }
        // Stand stem
        path(stroke = stroke(), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(12f, 19f); lineTo(12f, 22f)
        }
    }.build()
}
