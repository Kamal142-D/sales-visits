package dev.chiksmedina.solar.linear.videoaudiosound

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Round
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.chiksmedina.solar.linear.VideoAudioSoundGroup

val VideoAudioSoundGroup.GalleryAdd: ImageVector
    get() {
        if (_galleryAdd != null) {
            return _galleryAdd!!
        }
        _galleryAdd = Builder(
            name = "GalleryAdd", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(22.0f, 12.0f)
                curveTo(22.0f, 16.714f, 22.0f, 19.0711f, 20.5355f, 20.5355f)
                curveTo(19.0711f, 22.0f, 16.714f, 22.0f, 12.0f, 22.0f)
                curveTo(7.286f, 22.0f, 4.9289f, 22.0f, 3.4645f, 20.5355f)
                curveTo(2.0f, 19.0711f, 2.0f, 16.714f, 2.0f, 12.0f)
                curveTo(2.0f, 7.286f, 2.0f, 4.9289f, 3.4645f, 3.4645f)
                curveTo(4.9289f, 2.0f, 7.286f, 2.0f, 12.0f, 2.0f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(2.0f, 12.5001f)
                lineTo(3.7516f, 10.9675f)
                curveTo(4.6629f, 10.1702f, 6.0363f, 10.2159f, 6.8925f, 11.0721f)
                lineTo(11.1822f, 15.3618f)
                curveTo(11.8694f, 16.0491f, 12.9512f, 16.1428f, 13.7464f, 15.5839f)
                lineTo(14.0446f, 15.3744f)
                curveTo(15.1888f, 14.5702f, 16.7369f, 14.6634f, 17.7765f, 15.599f)
                lineTo(21.0f, 18.5001f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(15.0f, 5.5f)
                horizontalLineTo(18.5f)
                moveTo(18.5f, 5.5f)
                horizontalLineTo(22.0f)
                moveTo(18.5f, 5.5f)
                verticalLineTo(9.0f)
                moveTo(18.5f, 5.5f)
                verticalLineTo(2.0f)
            }
        }
            .build()
        return _galleryAdd!!
    }

private var _galleryAdd: ImageVector? = null
