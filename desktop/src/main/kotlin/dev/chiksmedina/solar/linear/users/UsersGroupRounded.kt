package dev.chiksmedina.solar.linear.users

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
import dev.chiksmedina.solar.linear.UsersGroup

val UsersGroup.UsersGroupRounded: ImageVector
    get() {
        if (_usersGroupRounded != null) {
            return _usersGroupRounded!!
        }
        _usersGroupRounded = Builder(
            name = "UsersGroupRounded", defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp, viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(9.0f, 6.0f)
                moveToRelative(-4.0f, 0.0f)
                arcToRelative(4.0f, 4.0f, 0.0f, true, true, 8.0f, 0.0f)
                arcToRelative(4.0f, 4.0f, 0.0f, true, true, -8.0f, 0.0f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(15.0f, 9.0f)
                curveTo(16.6569f, 9.0f, 18.0f, 7.6568f, 18.0f, 6.0f)
                curveTo(18.0f, 4.3432f, 16.6569f, 3.0f, 15.0f, 3.0f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(2.0f, 17.0f)
                arcToRelative(7.0f, 4.0f, 0.0f, true, false, 14.0f, 0.0f)
                arcToRelative(7.0f, 4.0f, 0.0f, true, false, -14.0f, 0.0f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(18.0f, 14.0f)
                curveTo(19.7542f, 14.3847f, 21.0f, 15.3589f, 21.0f, 16.5f)
                curveTo(21.0f, 17.5293f, 19.9863f, 18.4229f, 18.5f, 18.8704f)
            }
        }
            .build()
        return _usersGroupRounded!!
    }

private var _usersGroupRounded: ImageVector? = null
