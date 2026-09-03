package dev.chiksmedina.solar.linear.maplocation

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
import dev.chiksmedina.solar.linear.MapLocationGroup

val MapLocationGroup.Route: ImageVector
    get() {
        if (_route != null) {
            return _route!!
        }
        _route = Builder(
            name = "Route", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(6.1421f, 6.1421f)
                curveTo(8.9036f, 3.3807f, 10.2843f, 2.0f, 12.0f, 2.0f)
                curveTo(13.7157f, 2.0f, 15.0964f, 3.3807f, 17.8579f, 6.1421f)
                curveTo(20.6193f, 8.9036f, 22.0f, 10.2843f, 22.0f, 12.0f)
                curveTo(22.0f, 13.7157f, 20.6193f, 15.0964f, 17.8579f, 17.8579f)
                curveTo(15.0964f, 20.6193f, 13.7157f, 22.0f, 12.0f, 22.0f)
                curveTo(10.2843f, 22.0f, 8.9036f, 20.6193f, 6.1421f, 17.8579f)
                curveTo(3.3807f, 15.0964f, 2.0f, 13.7157f, 2.0f, 12.0f)
                curveTo(2.0f, 10.2843f, 3.3807f, 8.9036f, 6.1421f, 6.1421f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(16.0f, 11.5f)
                lineTo(13.3333f, 9.0f)
                moveTo(16.0f, 11.5f)
                lineTo(13.3333f, 14.0f)
                moveTo(16.0f, 11.5f)
                lineTo(10.6667f, 11.5f)
                curveTo(9.7778f, 11.5f, 8.0f, 12.0f, 8.0f, 14.0f)
            }
        }
            .build()
        return _route!!
    }

private var _route: ImageVector? = null
