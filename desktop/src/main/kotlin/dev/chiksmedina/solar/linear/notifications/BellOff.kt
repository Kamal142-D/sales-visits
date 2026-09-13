package dev.chiksmedina.solar.linear.notifications

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeCap.Companion.Round
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.chiksmedina.solar.linear.NotificationsGroup

val NotificationsGroup.BellOff: ImageVector
    get() {
        if (_bellOff != null) {
            return _bellOff!!
        }
        _bellOff = Builder(
            name = "BellOff", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(18.7491f, 9.7096f)
                verticalLineTo(9.005f)
                curveTo(18.7491f, 5.1362f, 15.7274f, 2.0f, 12.0f, 2.0f)
                curveTo(8.2726f, 2.0f, 5.2509f, 5.1362f, 5.2509f, 9.005f)
                verticalLineTo(9.7096f)
                curveTo(5.2509f, 10.5552f, 5.0097f, 11.3818f, 4.5578f, 12.0854f)
                lineTo(3.4504f, 13.8095f)
                curveTo(2.4388f, 15.3843f, 3.2111f, 17.5249f, 4.9704f, 18.0229f)
                curveTo(9.5727f, 19.3257f, 14.4273f, 19.3257f, 19.0296f, 18.0229f)
                curveTo(20.789f, 17.5249f, 21.5612f, 15.3843f, 20.5496f, 13.8095f)
                lineTo(19.4422f, 12.0854f)
                curveTo(18.9903f, 11.3818f, 18.7491f, 10.5552f, 18.7491f, 9.7096f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(10.0f, 9.0f)
                horizontalLineTo(14.0f)
                lineTo(10.0f, 13.0f)
                horizontalLineTo(14.0f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(7.5f, 19.0f)
                curveTo(8.155f, 20.7478f, 9.9225f, 22.0f, 12.0f, 22.0f)
                curveTo(14.0775f, 22.0f, 15.845f, 20.7478f, 16.5f, 19.0f)
            }
        }
            .build()
        return _bellOff!!
    }

private var _bellOff: ImageVector? = null
