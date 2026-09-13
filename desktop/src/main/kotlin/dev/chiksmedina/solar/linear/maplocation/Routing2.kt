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

val MapLocationGroup.Routing2: ImageVector
    get() {
        if (_routing2 != null) {
            return _routing2!!
        }
        _routing2 = Builder(
            name = "Routing2", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(2.0f, 5.2573f)
                curveTo(2.0f, 3.4583f, 3.567f, 2.0f, 5.5f, 2.0f)
                curveTo(7.433f, 2.0f, 9.0f, 3.4583f, 9.0f, 5.2573f)
                curveTo(9.0f, 7.0422f, 7.8829f, 9.125f, 6.14f, 9.8698f)
                curveTo(5.7337f, 10.0434f, 5.2663f, 10.0434f, 4.86f, 9.8698f)
                curveTo(3.1171f, 9.125f, 2.0f, 7.0422f, 2.0f, 5.2573f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(15.0f, 17.2573f)
                curveTo(15.0f, 15.4584f, 16.567f, 14.0f, 18.5f, 14.0f)
                curveTo(20.433f, 14.0f, 22.0f, 15.4584f, 22.0f, 17.2573f)
                curveTo(22.0f, 19.0422f, 20.8829f, 21.125f, 19.14f, 21.8698f)
                curveTo(18.7337f, 22.0434f, 18.2663f, 22.0434f, 17.86f, 21.8698f)
                curveTo(16.1171f, 21.125f, 15.0f, 19.0422f, 15.0f, 17.2573f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 2.0f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(18.4999f, 17.5f)
                horizontalLineTo(18.5089f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 2.0f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(5.4907f, 5.5f)
                horizontalLineTo(5.4997f)
            }
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(12.0f, 4.25f)
                curveTo(11.5858f, 4.25f, 11.25f, 4.5858f, 11.25f, 5.0f)
                curveTo(11.25f, 5.4142f, 11.5858f, 5.75f, 12.0f, 5.75f)
                verticalLineTo(4.25f)
                close()
                moveTo(12.0f, 19.0f)
                lineTo(12.5303f, 19.5303f)
                curveTo(12.8232f, 19.2374f, 12.8232f, 18.7626f, 12.5303f, 18.4697f)
                lineTo(12.0f, 19.0f)
                close()
                moveTo(17.2056f, 8.6873f)
                lineTo(17.6083f, 9.3201f)
                lineTo(17.6083f, 9.3201f)
                lineTo(17.2056f, 8.6873f)
                close()
                moveTo(6.7943f, 15.3127f)
                lineTo(7.197f, 15.9454f)
                lineTo(7.197f, 15.9454f)
                lineTo(6.7943f, 15.3127f)
                close()
                moveTo(11.0303f, 16.9697f)
                curveTo(10.7374f, 16.6768f, 10.2625f, 16.6768f, 9.9696f, 16.9697f)
                curveTo(9.6768f, 17.2626f, 9.6768f, 17.7374f, 9.9696f, 18.0303f)
                lineTo(11.0303f, 16.9697f)
                close()
                moveTo(9.9696f, 19.9697f)
                curveTo(9.6768f, 20.2626f, 9.6768f, 20.7374f, 9.9696f, 21.0303f)
                curveTo(10.2625f, 21.3232f, 10.7374f, 21.3232f, 11.0303f, 21.0303f)
                lineTo(9.9696f, 19.9697f)
                close()
                moveTo(16.1319f, 4.25f)
                horizontalLineTo(12.0f)
                verticalLineTo(5.75f)
                horizontalLineTo(16.1319f)
                verticalLineTo(4.25f)
                close()
                moveTo(12.0f, 18.25f)
                horizontalLineTo(7.8681f)
                verticalLineTo(19.75f)
                horizontalLineTo(12.0f)
                verticalLineTo(18.25f)
                close()
                moveTo(16.803f, 8.0546f)
                lineTo(6.3917f, 14.6799f)
                lineTo(7.197f, 15.9454f)
                lineTo(17.6083f, 9.3201f)
                lineTo(16.803f, 8.0546f)
                close()
                moveTo(12.5303f, 18.4697f)
                lineTo(11.0303f, 16.9697f)
                lineTo(9.9696f, 18.0303f)
                lineTo(11.4696f, 19.5303f)
                lineTo(12.5303f, 18.4697f)
                close()
                moveTo(11.4696f, 18.4697f)
                lineTo(9.9696f, 19.9697f)
                lineTo(11.0303f, 21.0303f)
                lineTo(12.5303f, 19.5303f)
                lineTo(11.4696f, 18.4697f)
                close()
                moveTo(7.8681f, 18.25f)
                curveTo(6.6175f, 18.25f, 6.142f, 16.6168f, 7.197f, 15.9454f)
                lineTo(6.3917f, 14.6799f)
                curveTo(4.0706f, 16.157f, 5.1168f, 19.75f, 7.8681f, 19.75f)
                verticalLineTo(18.25f)
                close()
                moveTo(16.1319f, 5.75f)
                curveTo(17.3824f, 5.75f, 17.858f, 7.3832f, 16.803f, 8.0546f)
                lineTo(17.6083f, 9.3201f)
                curveTo(19.9294f, 7.843f, 18.8831f, 4.25f, 16.1319f, 4.25f)
                verticalLineTo(5.75f)
                close()
            }
        }
            .build()
        return _routing2!!
    }

private var _routing2: ImageVector? = null
