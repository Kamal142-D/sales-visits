package dev.chiksmedina.solar.linear.messagesconversation

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
import dev.chiksmedina.solar.linear.MessagesConversationGroup

val MessagesConversationGroup.Plain2: ImageVector
    get() {
        if (_plain2 != null) {
            return _plain2!!
        }
        _plain2 = Builder(
            name = "Plain2", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Butt, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(17.4975f, 18.4851f)
                lineTo(20.6281f, 9.0937f)
                curveTo(21.8764f, 5.3487f, 22.5006f, 3.4762f, 21.5122f, 2.4878f)
                curveTo(20.5237f, 1.4994f, 18.6511f, 2.1236f, 14.906f, 3.3719f)
                lineTo(5.5748f, 6.4822f)
                curveTo(3.4929f, 7.1761f, 2.452f, 7.523f, 2.1361f, 8.2864f)
                curveTo(2.0618f, 8.4658f, 2.0169f, 8.656f, 2.0031f, 8.8496f)
                curveTo(1.9443f, 9.6736f, 2.7202f, 10.4495f, 4.2719f, 12.0011f)
                lineTo(4.5545f, 12.2837f)
                curveTo(4.8092f, 12.5384f, 4.9366f, 12.6658f, 5.0328f, 12.8075f)
                curveTo(5.2227f, 13.0871f, 5.3305f, 13.4143f, 5.3439f, 13.7519f)
                curveTo(5.3508f, 13.9232f, 5.324f, 14.1013f, 5.2706f, 14.4574f)
                curveTo(5.0749f, 15.7612f, 4.977f, 16.4131f, 5.0923f, 16.9147f)
                curveTo(5.3221f, 17.9146f, 6.096f, 18.6995f, 7.0926f, 18.9433f)
                curveTo(7.5925f, 19.0656f, 8.2458f, 18.977f, 9.5522f, 18.7997f)
                lineTo(9.6236f, 18.79f)
                curveTo(9.9919f, 18.74f, 10.1761f, 18.715f, 10.3529f, 18.7257f)
                curveTo(10.6738f, 18.745f, 10.9838f, 18.8496f, 11.251f, 19.0285f)
                curveTo(11.3981f, 19.1271f, 11.5295f, 19.2585f, 11.7923f, 19.5213f)
                lineTo(12.0436f, 19.7725f)
                curveTo(13.5539f, 21.2828f, 14.309f, 22.0379f, 15.1101f, 21.9985f)
                curveTo(15.3309f, 21.9877f, 15.5479f, 21.9365f, 15.7503f, 21.8474f)
                curveTo(16.4844f, 21.5244f, 16.8221f, 20.5113f, 17.4975f, 18.4851f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin = Miter,
                strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(6.0f, 18.0f)
                lineTo(21.0f, 3.0f)
            }
        }
            .build()
        return _plain2!!
    }

private var _plain2: ImageVector? = null
