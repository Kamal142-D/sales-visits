package dev.chiksmedina.solar.linear.maplocation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.chiksmedina.solar.linear.MapLocationGroup

val MapLocationGroup.Map: ImageVector
    get() {
        if (_map != null) {
            return _map!!
        }
        _map = Builder(
            name = "Map", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp, viewportWidth
            = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(3.0f, 8.7094f)
                curveTo(3.0f, 7.2358f, 3.0f, 6.4991f, 3.3926f, 6.0693f)
                curveTo(3.532f, 5.9168f, 3.7015f, 5.7947f, 3.8903f, 5.7107f)
                curveTo(4.4221f, 5.4741f, 5.1211f, 5.707f, 6.519f, 6.173f)
                curveTo(7.5863f, 6.5288f, 8.1199f, 6.7066f, 8.6591f, 6.6882f)
                curveTo(8.8571f, 6.6815f, 9.054f, 6.6551f, 9.2469f, 6.6095f)
                curveTo(9.7719f, 6.4854f, 10.2399f, 6.1734f, 11.176f, 5.5494f)
                lineTo(12.5583f, 4.6278f)
                curveTo(13.7574f, 3.8284f, 14.3569f, 3.4288f, 15.0451f, 3.3366f)
                curveTo(15.7333f, 3.2444f, 16.4168f, 3.4723f, 17.7839f, 3.928f)
                lineTo(18.9487f, 4.3162f)
                curveTo(19.9387f, 4.6462f, 20.4337f, 4.8113f, 20.7169f, 5.2041f)
                curveTo(21.0f, 5.5969f, 21.0f, 6.1187f, 21.0f, 7.1623f)
                verticalLineTo(15.2907f)
                curveTo(21.0f, 16.7642f, 21.0f, 17.501f, 20.6074f, 17.9307f)
                curveTo(20.468f, 18.0833f, 20.2985f, 18.2054f, 20.1097f, 18.2894f)
                curveTo(19.5779f, 18.526f, 18.8789f, 18.293f, 17.481f, 17.827f)
                curveTo(16.4137f, 17.4713f, 15.8801f, 17.2934f, 15.3409f, 17.3118f)
                curveTo(15.1429f, 17.3186f, 14.946f, 17.3449f, 14.7532f, 17.3905f)
                curveTo(14.2281f, 17.5146f, 13.7601f, 17.8266f, 12.824f, 18.4507f)
                lineTo(11.4417f, 19.3722f)
                curveTo(10.2426f, 20.1716f, 9.6431f, 20.5713f, 8.9549f, 20.6634f)
                curveTo(8.2667f, 20.7556f, 7.5832f, 20.5277f, 6.2161f, 20.072f)
                lineTo(5.0513f, 19.6838f)
                curveTo(4.0613f, 19.3538f, 3.5663f, 19.1888f, 3.2831f, 18.7959f)
                curveTo(3.0f, 18.4031f, 3.0f, 17.8813f, 3.0f, 16.8377f)
                verticalLineTo(8.7094f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(9.0f, 6.6387f)
                verticalLineTo(20.5f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(15.0f, 3.0f)
                verticalLineTo(17.0f)
            }
        }
            .build()
        return _map!!
    }

private var _map: ImageVector? = null
