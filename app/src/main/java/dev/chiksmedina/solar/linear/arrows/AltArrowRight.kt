package dev.chiksmedina.solar.linear.arrows

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Round
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.chiksmedina.solar.linear.ArrowsGroup

val ArrowsGroup.AltArrowRight: ImageVector
    get() {
        if (_altArrowRight != null) {
            return _altArrowRight!!
        }
        _altArrowRight = Builder(
            name = "AltArrowRight", defaultWidth = 24.0.dp, defaultHeight =
            24.0.dp, viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(9.0f, 5.0f)
                lineTo(15.0f, 12.0f)
                lineTo(9.0f, 19.0f)
            }
        }
            .build()
        return _altArrowRight!!
    }

private var _altArrowRight: ImageVector? = null
