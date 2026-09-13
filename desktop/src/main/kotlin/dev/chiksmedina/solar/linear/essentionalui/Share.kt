package dev.chiksmedina.solar.linear.essentionalui

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
import dev.chiksmedina.solar.linear.EssentionalUiGroup

val EssentionalUiGroup.Share: ImageVector
    get() {
        if (_share != null) {
            return _share!!
        }
        _share = Builder(
            name = "Share", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(9.0f, 12.0f)
                curveTo(9.0f, 13.3807f, 7.8807f, 14.5f, 6.5f, 14.5f)
                curveTo(5.1193f, 14.5f, 4.0f, 13.3807f, 4.0f, 12.0f)
                curveTo(4.0f, 10.6193f, 5.1193f, 9.5f, 6.5f, 9.5f)
                curveTo(7.8807f, 9.5f, 9.0f, 10.6193f, 9.0f, 12.0f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(14.0f, 6.5f)
                lineTo(9.0f, 10.0f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(14.0f, 17.5f)
                lineTo(9.0f, 14.0f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(19.0f, 18.5f)
                curveTo(19.0f, 19.8807f, 17.8807f, 21.0f, 16.5f, 21.0f)
                curveTo(15.1193f, 21.0f, 14.0f, 19.8807f, 14.0f, 18.5f)
                curveTo(14.0f, 17.1193f, 15.1193f, 16.0f, 16.5f, 16.0f)
                curveTo(17.8807f, 16.0f, 19.0f, 17.1193f, 19.0f, 18.5f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(19.0f, 5.5f)
                curveTo(19.0f, 6.8807f, 17.8807f, 8.0f, 16.5f, 8.0f)
                curveTo(15.1193f, 8.0f, 14.0f, 6.8807f, 14.0f, 5.5f)
                curveTo(14.0f, 4.1193f, 15.1193f, 3.0f, 16.5f, 3.0f)
                curveTo(17.8807f, 3.0f, 19.0f, 4.1193f, 19.0f, 5.5f)
                close()
            }
        }
            .build()
        return _share!!
    }

private var _share: ImageVector? = null
