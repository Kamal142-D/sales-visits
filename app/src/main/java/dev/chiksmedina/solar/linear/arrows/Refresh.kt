package dev.chiksmedina.solar.linear.arrows

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.chiksmedina.solar.linear.ArrowsGroup

val ArrowsGroup.Refresh: ImageVector
    get() {
        if (_refresh != null) {
            return _refresh!!
        }
        _refresh = Builder(
            name = "Refresh", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(3.6798f, 11.3333f)
                lineTo(2.9298f, 11.3333f)
                lineTo(2.9298f, 11.3333f)
                lineTo(3.6798f, 11.3333f)
                close()
                moveTo(3.6798f, 13.0f)
                lineTo(3.1516f, 13.5324f)
                curveTo(3.444f, 13.8225f, 3.9156f, 13.8225f, 4.208f, 13.5324f)
                lineTo(3.6798f, 13.0f)
                close()
                moveTo(5.8879f, 11.8657f)
                curveTo(6.1819f, 11.574f, 6.1838f, 11.0991f, 5.892f, 10.8051f)
                curveTo(5.6003f, 10.511f, 5.1254f, 10.5092f, 4.8314f, 10.8009f)
                lineTo(5.8879f, 11.8657f)
                close()
                moveTo(2.5282f, 10.8009f)
                curveTo(2.2342f, 10.5092f, 1.7593f, 10.511f, 1.4676f, 10.8051f)
                curveTo(1.1759f, 11.0991f, 1.1777f, 11.574f, 1.4718f, 11.8657f)
                lineTo(2.5282f, 10.8009f)
                close()
                moveTo(18.6156f, 7.3928f)
                curveTo(18.8325f, 7.7456f, 19.2944f, 7.8559f, 19.6473f, 7.6389f)
                curveTo(20.0001f, 7.422f, 20.1103f, 6.9601f, 19.8934f, 6.6072f)
                lineTo(18.6156f, 7.3928f)
                close()
                moveTo(12.0789f, 2.25f)
                curveTo(7.0315f, 2.25f, 2.9298f, 6.3112f, 2.9298f, 11.3333f)
                horizontalLineTo(4.4298f)
                curveTo(4.4298f, 7.1507f, 7.8488f, 3.75f, 12.0789f, 3.75f)
                verticalLineTo(2.25f)
                close()
                moveTo(2.9298f, 11.3333f)
                lineTo(2.9298f, 13.0f)
                lineTo(4.4298f, 13.0f)
                lineTo(4.4298f, 11.3333f)
                lineTo(2.9298f, 11.3333f)
                close()
                moveTo(4.208f, 13.5324f)
                lineTo(5.8879f, 11.8657f)
                lineTo(4.8314f, 10.8009f)
                lineTo(3.1516f, 12.4676f)
                lineTo(4.208f, 13.5324f)
                close()
                moveTo(4.208f, 12.4676f)
                lineTo(2.5282f, 10.8009f)
                lineTo(1.4718f, 11.8657f)
                lineTo(3.1516f, 13.5324f)
                lineTo(4.208f, 12.4676f)
                close()
                moveTo(19.8934f, 6.6072f)
                curveTo(18.287f, 3.9943f, 15.3873f, 2.25f, 12.0789f, 2.25f)
                verticalLineTo(3.75f)
                curveTo(14.8484f, 3.75f, 17.2727f, 5.2084f, 18.6156f, 7.3928f)
                lineTo(19.8934f, 6.6072f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(20.3139f, 11.0f)
                lineTo(20.8411f, 10.4666f)
                curveTo(20.549f, 10.1778f, 20.0788f, 10.1778f, 19.7867f, 10.4666f)
                lineTo(20.3139f, 11.0f)
                close()
                moveTo(18.1004f, 12.1333f)
                curveTo(17.8058f, 12.4244f, 17.8031f, 12.8993f, 18.0942f, 13.1939f)
                curveTo(18.3854f, 13.4885f, 18.8603f, 13.4913f, 19.1549f, 13.2001f)
                lineTo(18.1004f, 12.1333f)
                close()
                moveTo(21.4729f, 13.2001f)
                curveTo(21.7675f, 13.4913f, 22.2424f, 13.4885f, 22.5335f, 13.1939f)
                curveTo(22.8247f, 12.8993f, 22.822f, 12.4244f, 22.5274f, 12.1332f)
                lineTo(21.4729f, 13.2001f)
                close()
                moveTo(5.3179f, 16.6061f)
                curveTo(5.1004f, 16.2536f, 4.6383f, 16.1442f, 4.2858f, 16.3618f)
                curveTo(3.9333f, 16.5793f, 3.8239f, 17.0414f, 4.0414f, 17.3939f)
                lineTo(5.3179f, 16.6061f)
                close()
                moveTo(11.8827f, 21.75f)
                curveTo(16.9451f, 21.75f, 21.0639f, 17.6915f, 21.0639f, 12.6667f)
                horizontalLineTo(19.5639f)
                curveTo(19.5639f, 16.8466f, 16.1332f, 20.25f, 11.8827f, 20.25f)
                verticalLineTo(21.75f)
                close()
                moveTo(21.0639f, 12.6667f)
                verticalLineTo(11.0f)
                horizontalLineTo(19.5639f)
                verticalLineTo(12.6667f)
                horizontalLineTo(21.0639f)
                close()
                moveTo(19.7867f, 10.4666f)
                lineTo(18.1004f, 12.1333f)
                lineTo(19.1549f, 13.2001f)
                lineTo(20.8411f, 11.5334f)
                lineTo(19.7867f, 10.4666f)
                close()
                moveTo(19.7867f, 11.5334f)
                lineTo(21.4729f, 13.2001f)
                lineTo(22.5274f, 12.1332f)
                lineTo(20.8411f, 10.4666f)
                lineTo(19.7867f, 11.5334f)
                close()
                moveTo(4.0414f, 17.3939f)
                curveTo(5.654f, 20.007f, 8.564f, 21.75f, 11.8827f, 21.75f)
                verticalLineTo(20.25f)
                curveTo(9.1002f, 20.25f, 6.6658f, 18.7903f, 5.3179f, 16.6061f)
                lineTo(4.0414f, 17.3939f)
                close()
            }
        }
            .build()
        return _refresh!!
    }

private var _refresh: ImageVector? = null
