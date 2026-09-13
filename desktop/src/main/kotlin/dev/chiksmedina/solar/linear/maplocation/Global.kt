package dev.chiksmedina.solar.linear.maplocation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeCap.Companion.Round
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.chiksmedina.solar.linear.MapLocationGroup

val MapLocationGroup.Global: ImageVector
    get() {
        if (_global != null) {
            return _global!!
        }
        _global = Builder(
            name = "Global", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(22.0f, 12.0f)
                curveTo(22.0f, 13.3132f, 21.7413f, 14.6136f, 21.2388f, 15.8268f)
                curveTo(20.7362f, 17.0401f, 19.9997f, 18.1425f, 19.0711f, 19.0711f)
                curveTo(18.1425f, 19.9997f, 17.0401f, 20.7362f, 15.8268f, 21.2388f)
                curveTo(14.6136f, 21.7413f, 13.3132f, 22.0f, 12.0f, 22.0f)
                curveTo(10.6868f, 22.0f, 9.3864f, 21.7413f, 8.1732f, 21.2388f)
                curveTo(6.9599f, 20.7362f, 5.8575f, 19.9997f, 4.9289f, 19.0711f)
                curveTo(4.0003f, 18.1425f, 3.2638f, 17.0401f, 2.7612f, 15.8268f)
                curveTo(2.2587f, 14.6136f, 2.0f, 13.3132f, 2.0f, 12.0f)
                curveTo(2.0f, 10.6868f, 2.2587f, 9.3864f, 2.7612f, 8.1732f)
                curveTo(3.2638f, 6.9599f, 4.0003f, 5.8575f, 4.9289f, 4.9289f)
                curveTo(5.8575f, 4.0003f, 6.9599f, 3.2638f, 8.1732f, 2.7612f)
                curveTo(9.3864f, 2.2587f, 10.6868f, 2.0f, 12.0f, 2.0f)
                curveTo(13.3132f, 2.0f, 14.6136f, 2.2587f, 15.8268f, 2.7612f)
                curveTo(17.0401f, 3.2638f, 18.1425f, 4.0003f, 19.0711f, 4.9289f)
                curveTo(19.9997f, 5.8575f, 20.7362f, 6.9599f, 21.2388f, 8.1732f)
                curveTo(21.7413f, 9.3864f, 22.0f, 10.6868f, 22.0f, 12.0f)
                lineTo(22.0f, 12.0f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(16.0f, 12.0f)
                curveTo(16.0f, 13.3132f, 15.8965f, 14.6136f, 15.6955f, 15.8268f)
                curveTo(15.4945f, 17.0401f, 15.1999f, 18.1425f, 14.8284f, 19.0711f)
                curveTo(14.457f, 19.9997f, 14.016f, 20.7362f, 13.5307f, 21.2388f)
                curveTo(13.0454f, 21.7413f, 12.5253f, 22.0f, 12.0f, 22.0f)
                curveTo(11.4747f, 22.0f, 10.9546f, 21.7413f, 10.4693f, 21.2388f)
                curveTo(9.984f, 20.7362f, 9.543f, 19.9997f, 9.1716f, 19.0711f)
                curveTo(8.8001f, 18.1425f, 8.5055f, 17.0401f, 8.3045f, 15.8268f)
                curveTo(8.1035f, 14.6136f, 8.0f, 13.3132f, 8.0f, 12.0f)
                curveTo(8.0f, 10.6868f, 8.1035f, 9.3864f, 8.3045f, 8.1732f)
                curveTo(8.5055f, 6.9599f, 8.8001f, 5.8575f, 9.1716f, 4.9289f)
                curveTo(9.543f, 4.0003f, 9.984f, 3.2638f, 10.4693f, 2.7612f)
                curveTo(10.9546f, 2.2587f, 11.4747f, 2.0f, 12.0f, 2.0f)
                curveTo(12.5253f, 2.0f, 13.0454f, 2.2587f, 13.5307f, 2.7612f)
                curveTo(14.016f, 3.2638f, 14.457f, 4.0003f, 14.8284f, 4.9289f)
                curveTo(15.1999f, 5.8575f, 15.4945f, 6.9599f, 15.6955f, 8.1732f)
                curveTo(15.8965f, 9.3864f, 16.0f, 10.6868f, 16.0f, 12.0f)
                lineTo(16.0f, 12.0f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(2.0f, 12.0f)
                horizontalLineTo(22.0f)
            }
        }
            .build()
        return _global!!
    }

private var _global: ImageVector? = null
