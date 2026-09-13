package dev.chiksmedina.solar.linear.businessstatistic

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
import dev.chiksmedina.solar.linear.BusinessStatisticGroup

val BusinessStatisticGroup.Chart2: ImageVector
    get() {
        if (_chart2 != null) {
            return _chart2!!
        }
        _chart2 = Builder(
            name = "Chart2", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(3.0f, 22.0f)
                horizontalLineTo(21.0f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(3.0f, 11.0f)
                curveTo(3.0f, 10.0572f, 3.0f, 9.5858f, 3.2929f, 9.2929f)
                curveTo(3.5858f, 9.0f, 4.0572f, 9.0f, 5.0f, 9.0f)
                curveTo(5.9428f, 9.0f, 6.4142f, 9.0f, 6.7071f, 9.2929f)
                curveTo(7.0f, 9.5858f, 7.0f, 10.0572f, 7.0f, 11.0f)
                verticalLineTo(17.0f)
                curveTo(7.0f, 17.9428f, 7.0f, 18.4142f, 6.7071f, 18.7071f)
                curveTo(6.4142f, 19.0f, 5.9428f, 19.0f, 5.0f, 19.0f)
                curveTo(4.0572f, 19.0f, 3.5858f, 19.0f, 3.2929f, 18.7071f)
                curveTo(3.0f, 18.4142f, 3.0f, 17.9428f, 3.0f, 17.0f)
                verticalLineTo(11.0f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(10.0f, 7.0f)
                curveTo(10.0f, 6.0572f, 10.0f, 5.5858f, 10.2929f, 5.2929f)
                curveTo(10.5858f, 5.0f, 11.0572f, 5.0f, 12.0f, 5.0f)
                curveTo(12.9428f, 5.0f, 13.4142f, 5.0f, 13.7071f, 5.2929f)
                curveTo(14.0f, 5.5858f, 14.0f, 6.0572f, 14.0f, 7.0f)
                verticalLineTo(17.0f)
                curveTo(14.0f, 17.9428f, 14.0f, 18.4142f, 13.7071f, 18.7071f)
                curveTo(13.4142f, 19.0f, 12.9428f, 19.0f, 12.0f, 19.0f)
                curveTo(11.0572f, 19.0f, 10.5858f, 19.0f, 10.2929f, 18.7071f)
                curveTo(10.0f, 18.4142f, 10.0f, 17.9428f, 10.0f, 17.0f)
                verticalLineTo(7.0f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(17.0f, 4.0f)
                curveTo(17.0f, 3.0572f, 17.0f, 2.5858f, 17.2929f, 2.2929f)
                curveTo(17.5858f, 2.0f, 18.0572f, 2.0f, 19.0f, 2.0f)
                curveTo(19.9428f, 2.0f, 20.4142f, 2.0f, 20.7071f, 2.2929f)
                curveTo(21.0f, 2.5858f, 21.0f, 3.0572f, 21.0f, 4.0f)
                verticalLineTo(17.0f)
                curveTo(21.0f, 17.9428f, 21.0f, 18.4142f, 20.7071f, 18.7071f)
                curveTo(20.4142f, 19.0f, 19.9428f, 19.0f, 19.0f, 19.0f)
                curveTo(18.0572f, 19.0f, 17.5858f, 19.0f, 17.2929f, 18.7071f)
                curveTo(17.0f, 18.4142f, 17.0f, 17.9428f, 17.0f, 17.0f)
                verticalLineTo(4.0f)
                close()
            }
        }
            .build()
        return _chart2!!
    }

private var _chart2: ImageVector? = null
