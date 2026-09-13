package dev.chiksmedina.solar.linear.essentionalui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.chiksmedina.solar.linear.EssentionalUiGroup

val EssentionalUiGroup.Copy: ImageVector
    get() {
        if (_copy != null) {
            return _copy!!
        }
        _copy = Builder(
            name = "Copy", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(6.0f, 11.0f)
                curveTo(6.0f, 8.1716f, 6.0f, 6.7574f, 6.8787f, 5.8787f)
                curveTo(7.7574f, 5.0f, 9.1716f, 5.0f, 12.0f, 5.0f)
                horizontalLineTo(15.0f)
                curveTo(17.8284f, 5.0f, 19.2426f, 5.0f, 20.1213f, 5.8787f)
                curveTo(21.0f, 6.7574f, 21.0f, 8.1716f, 21.0f, 11.0f)
                verticalLineTo(16.0f)
                curveTo(21.0f, 18.8284f, 21.0f, 20.2426f, 20.1213f, 21.1213f)
                curveTo(19.2426f, 22.0f, 17.8284f, 22.0f, 15.0f, 22.0f)
                horizontalLineTo(12.0f)
                curveTo(9.1716f, 22.0f, 7.7574f, 22.0f, 6.8787f, 21.1213f)
                curveTo(6.0f, 20.2426f, 6.0f, 18.8284f, 6.0f, 16.0f)
                verticalLineTo(11.0f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(6.0f, 19.0f)
                curveTo(4.3432f, 19.0f, 3.0f, 17.6569f, 3.0f, 16.0f)
                verticalLineTo(10.0f)
                curveTo(3.0f, 6.2288f, 3.0f, 4.3432f, 4.1716f, 3.1716f)
                curveTo(5.3432f, 2.0f, 7.2288f, 2.0f, 11.0f, 2.0f)
                horizontalLineTo(15.0f)
                curveTo(16.6569f, 2.0f, 18.0f, 3.3431f, 18.0f, 5.0f)
            }
        }
            .build()
        return _copy!!
    }

private var _copy: ImageVector? = null
