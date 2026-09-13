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

/** A simple clock (circle + hands) in the Solar Linear style, for the time-tracking chip. */
val ClockIcon: ImageVector by lazy {
    fun stroke() = SolidColor(Color(0xFF000000))
    ImageVector.Builder(
        name = "Clock", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(stroke = stroke(), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 4f)
            curveTo(16.4183f, 4f, 20f, 7.5817f, 20f, 12f)
            curveTo(20f, 16.4183f, 16.4183f, 20f, 12f, 20f)
            curveTo(7.5817f, 20f, 4f, 16.4183f, 4f, 12f)
            curveTo(4f, 7.5817f, 7.5817f, 4f, 12f, 4f)
            close()
        }
        // Hands
        path(stroke = stroke(), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 8.5f); lineTo(12f, 12f); lineTo(14.5f, 13.5f)
        }
    }.build()
}

/** A sun (circle + rays) in the Solar Linear style. */
val SunIcon: ImageVector by lazy {
    fun stroke() = SolidColor(Color(0xFF000000))
    ImageVector.Builder(name = "Sun", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(stroke = stroke(), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 6.5f)
            curveTo(15.0376f, 6.5f, 17.5f, 8.9624f, 17.5f, 12f)
            curveTo(17.5f, 15.0376f, 15.0376f, 17.5f, 12f, 17.5f)
            curveTo(8.9624f, 17.5f, 6.5f, 15.0376f, 6.5f, 12f)
            curveTo(6.5f, 8.9624f, 8.9624f, 6.5f, 12f, 6.5f)
            close()
        }
        val rays = listOf(
            floatArrayOf(12f, 1.5f, 12f, 3.5f), floatArrayOf(12f, 20.5f, 12f, 22.5f),
            floatArrayOf(1.5f, 12f, 3.5f, 12f), floatArrayOf(20.5f, 12f, 22.5f, 12f),
            floatArrayOf(4.5f, 4.5f, 5.9f, 5.9f), floatArrayOf(18.1f, 18.1f, 19.5f, 19.5f),
            floatArrayOf(19.5f, 4.5f, 18.1f, 5.9f), floatArrayOf(5.9f, 18.1f, 4.5f, 19.5f),
        )
        rays.forEach { r ->
            path(stroke = stroke(), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(r[0], r[1]); lineTo(r[2], r[3])
            }
        }
    }.build()
}

/** A crescent moon in the Solar Linear style. */
val MoonIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Moon", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(stroke = SolidColor(Color(0xFF000000)), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(21f, 12.8f)
            curveTo(20.1f, 15.9f, 17.3f, 18.2f, 13.9f, 18.2f)
            curveTo(9.7f, 18.2f, 6.3f, 14.8f, 6.3f, 10.6f)
            curveTo(6.3f, 7.2f, 8.6f, 4.3f, 11.7f, 3.5f)
            curveTo(11.1f, 4.4f, 10.8f, 5.5f, 10.8f, 6.7f)
            curveTo(10.8f, 10.2f, 13.6f, 13f, 17.1f, 13f)
            curveTo(18.5f, 13f, 19.9f, 12.9f, 21f, 12.8f)
            close()
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
