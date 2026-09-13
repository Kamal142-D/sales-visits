package dev.chiksmedina.solar.linear.designtools

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
import dev.chiksmedina.solar.linear.DesignToolsGroup

val DesignToolsGroup.Palette: ImageVector
    get() {
        if (_palette != null) {
            return _palette!!
        }
        _palette = Builder(
            name = "Palette", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(2.0f, 6.0f)
                curveTo(2.0f, 4.5999f, 2.0f, 3.8998f, 2.2725f, 3.365f)
                curveTo(2.5122f, 2.8946f, 2.8946f, 2.5122f, 3.365f, 2.2725f)
                curveTo(3.8998f, 2.0f, 4.5999f, 2.0f, 6.0f, 2.0f)
                curveTo(7.4001f, 2.0f, 8.1002f, 2.0f, 8.635f, 2.2725f)
                curveTo(9.1054f, 2.5122f, 9.4878f, 2.8946f, 9.7275f, 3.365f)
                curveTo(10.0f, 3.8998f, 10.0f, 4.5999f, 10.0f, 6.0f)
                verticalLineTo(18.0f)
                curveTo(10.0f, 19.4001f, 10.0f, 20.1002f, 9.7275f, 20.635f)
                curveTo(9.4878f, 21.1054f, 9.1054f, 21.4878f, 8.635f, 21.7275f)
                curveTo(8.1002f, 22.0f, 7.4001f, 22.0f, 6.0f, 22.0f)
                curveTo(4.5999f, 22.0f, 3.8998f, 22.0f, 3.365f, 21.7275f)
                curveTo(2.8946f, 21.4878f, 2.5122f, 21.1054f, 2.2725f, 20.635f)
                curveTo(2.0f, 20.1002f, 2.0f, 19.4001f, 2.0f, 18.0f)
                verticalLineTo(6.0f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(7.0f, 19.0f)
                horizontalLineTo(5.0f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(13.3137f, 4.9291f)
                lineTo(11.1716f, 7.0712f)
                curveTo(10.5935f, 7.6493f, 10.3045f, 7.9383f, 10.1522f, 8.3059f)
                curveTo(10.0f, 8.6734f, 10.0f, 9.0821f, 10.0f, 9.8996f)
                lineTo(10.0f, 19.5565f)
                lineTo(18.9705f, 10.586f)
                curveTo(19.9606f, 9.5959f, 20.4556f, 9.1009f, 20.6411f, 8.5301f)
                curveTo(20.8042f, 8.028f, 20.8042f, 7.4871f, 20.6411f, 6.985f)
                curveTo(20.4556f, 6.4142f, 19.9606f, 5.9192f, 18.9705f, 4.9291f)
                curveTo(17.9805f, 3.9391f, 17.4855f, 3.4441f, 16.9146f, 3.2586f)
                curveTo(16.4125f, 3.0954f, 15.8717f, 3.0954f, 15.3695f, 3.2586f)
                curveTo(14.7987f, 3.4441f, 14.3037f, 3.9391f, 13.3137f, 4.9291f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(6.0f, 22.0f)
                lineTo(18.0f, 22.0f)
                curveTo(19.4001f, 22.0f, 20.1002f, 22.0f, 20.635f, 21.7275f)
                curveTo(21.1054f, 21.4878f, 21.4878f, 21.1054f, 21.7275f, 20.635f)
                curveTo(22.0f, 20.1002f, 22.0f, 19.4001f, 22.0f, 18.0f)
                curveTo(22.0f, 16.5999f, 22.0f, 15.8998f, 21.7275f, 15.365f)
                curveTo(21.4878f, 14.8946f, 21.1054f, 14.5122f, 20.635f, 14.2725f)
                curveTo(20.1002f, 14.0f, 19.4001f, 14.0f, 18.0f, 14.0f)
                lineTo(15.5f, 14.0f)
            }
        }
            .build()
        return _palette!!
    }

private var _palette: ImageVector? = null
