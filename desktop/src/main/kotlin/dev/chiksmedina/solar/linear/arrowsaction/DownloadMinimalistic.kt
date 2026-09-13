package dev.chiksmedina.solar.linear.arrowsaction

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Round
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.chiksmedina.solar.linear.ArrowsActionGroup

val ArrowsActionGroup.DownloadMinimalistic: ImageVector
    get() {
        if (_downloadMinimalistic != null) {
            return _downloadMinimalistic!!
        }
        _downloadMinimalistic = Builder(
            name = "DownloadMinimalistic", defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp, viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(3.0f, 15.0f)
                curveTo(3.0f, 17.8284f, 3.0f, 19.2426f, 3.8787f, 20.1213f)
                curveTo(4.7574f, 21.0f, 6.1716f, 21.0f, 9.0f, 21.0f)
                horizontalLineTo(15.0f)
                curveTo(17.8284f, 21.0f, 19.2426f, 21.0f, 20.1213f, 20.1213f)
                curveTo(21.0f, 19.2426f, 21.0f, 17.8284f, 21.0f, 15.0f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(12.0f, 3.0f)
                verticalLineTo(16.0f)
                moveTo(12.0f, 16.0f)
                lineTo(16.0f, 11.625f)
                moveTo(12.0f, 16.0f)
                lineTo(8.0f, 11.625f)
            }
        }
            .build()
        return _downloadMinimalistic!!
    }

private var _downloadMinimalistic: ImageVector? = null
