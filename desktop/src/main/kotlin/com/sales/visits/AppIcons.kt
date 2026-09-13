package com.sales.visits

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import dev.chiksmedina.solar.linear.ArrowsActionGroup
import dev.chiksmedina.solar.linear.ArrowsGroup
import dev.chiksmedina.solar.linear.BusinessStatisticGroup
import dev.chiksmedina.solar.linear.CallGroup
import dev.chiksmedina.solar.linear.DesignToolsGroup
import dev.chiksmedina.solar.linear.EssentionalUiGroup
import dev.chiksmedina.solar.linear.ListGroup
import dev.chiksmedina.solar.linear.MapLocationGroup
import dev.chiksmedina.solar.linear.MessagesConversationGroup
import dev.chiksmedina.solar.linear.NotesGroup
import dev.chiksmedina.solar.linear.NotificationsGroup
import dev.chiksmedina.solar.linear.SearchGroup
import dev.chiksmedina.solar.linear.SettingsFineTuningGroup
import dev.chiksmedina.solar.linear.ShoppingEcommerceGroup
import dev.chiksmedina.solar.linear.TimeGroup
import dev.chiksmedina.solar.linear.UsersGroup
import dev.chiksmedina.solar.linear.VideoAudioSoundGroup
import dev.chiksmedina.solar.linear.arrows.AltArrowLeft
import dev.chiksmedina.solar.linear.arrows.AltArrowRight
import dev.chiksmedina.solar.linear.arrows.Refresh
import dev.chiksmedina.solar.linear.arrowsaction.DownloadMinimalistic
import dev.chiksmedina.solar.linear.arrowsaction.UploadMinimalistic
import dev.chiksmedina.solar.linear.businessstatistic.Chart2
import dev.chiksmedina.solar.linear.call.PhoneRounded
import dev.chiksmedina.solar.linear.designtools.Palette
import dev.chiksmedina.solar.linear.maplocation.Global
import dev.chiksmedina.solar.linear.time.CalendarMinimalistic
import dev.chiksmedina.solar.linear.essentionalui.CheckCircle
import dev.chiksmedina.solar.linear.essentionalui.HomeAngle2
import dev.chiksmedina.solar.linear.essentionalui.CloseCircle
import dev.chiksmedina.solar.linear.essentionalui.Copy
import dev.chiksmedina.solar.linear.essentionalui.InfoCircle
import dev.chiksmedina.solar.linear.essentionalui.MenuDots
import dev.chiksmedina.solar.linear.essentionalui.Share
import dev.chiksmedina.solar.linear.essentionalui.TrashBinMinimalistic
import dev.chiksmedina.solar.linear.list.ListCheckMinimalistic
import dev.chiksmedina.solar.linear.maplocation.Map
import dev.chiksmedina.solar.linear.maplocation.MapPoint
import dev.chiksmedina.solar.linear.maplocation.Route
import dev.chiksmedina.solar.linear.maplocation.Routing2
import dev.chiksmedina.solar.linear.messagesconversation.Inbox
import dev.chiksmedina.solar.linear.messagesconversation.Letter
import dev.chiksmedina.solar.linear.messagesconversation.Plain2
import dev.chiksmedina.solar.linear.notes.DocumentText
import dev.chiksmedina.solar.linear.notifications.BellBing
import dev.chiksmedina.solar.linear.notifications.BellOff
import dev.chiksmedina.solar.linear.search.Magnifer
import dev.chiksmedina.solar.linear.settingsfinetuning.SettingsMinimalistic
import dev.chiksmedina.solar.linear.shoppingecommerce.ShopMinimalistic
import dev.chiksmedina.solar.linear.users.User
import dev.chiksmedina.solar.linear.users.UserPlus
import dev.chiksmedina.solar.linear.users.UsersGroupRounded
import dev.chiksmedina.solar.linear.videoaudiosound.GalleryAdd

/** The single Solar Linear style used throughout the app. */
object AppIcons {
    val Add: ImageVector get() = Icons.Filled.Add
    val ArrowBack: ImageVector get() = ArrowsGroup.AltArrowLeft
    val ArrowForward: ImageVector get() = ArrowsGroup.AltArrowRight
    val Chart: ImageVector get() = BusinessStatisticGroup.Chart2
    val Check: ImageVector get() = EssentionalUiGroup.CheckCircle
    val Close: ImageVector get() = EssentionalUiGroup.CloseCircle
    val Copy: ImageVector get() = EssentionalUiGroup.Copy
    val Delete: ImageVector get() = EssentionalUiGroup.TrashBinMinimalistic
    val Directions: ImageVector get() = MapLocationGroup.Routing2
    val Download: ImageVector get() = ArrowsActionGroup.DownloadMinimalistic
    val Upload: ImageVector get() = ArrowsActionGroup.UploadMinimalistic
    val Inbox: ImageVector get() = MessagesConversationGroup.Inbox
    val Info: ImageVector get() = EssentionalUiGroup.InfoCircle
    val More: ImageVector get() = EssentionalUiGroup.MenuDots
    val Notifications: ImageVector get() = NotificationsGroup.BellBing
    val NotificationsOff: ImageVector get() = NotificationsGroup.BellOff
    val People: ImageVector get() = UsersGroup.UsersGroupRounded
    val PersonAdd: ImageVector get() = UsersGroup.UserPlus
    val Person: ImageVector get() = UsersGroup.User
    val Phone: ImageVector get() = CallGroup.PhoneRounded
    val Email: ImageVector get() = MessagesConversationGroup.Letter
    val Image: ImageVector get() = VideoAudioSoundGroup.GalleryAdd
    val Place: ImageVector get() = MapLocationGroup.MapPoint
    val Plan: ImageVector get() = ListGroup.ListCheckMinimalistic
    val Search: ImageVector get() = SearchGroup.Magnifer
    val Settings: ImageVector get() = SettingsFineTuningGroup.SettingsMinimalistic
    val Share: ImageVector get() = EssentionalUiGroup.Share
    val Update: ImageVector get() = ArrowsGroup.Refresh
    val Insights: ImageVector get() = BusinessStatisticGroup.Chart2
    val Map: ImageVector get() = MapLocationGroup.Route
    val Mic: ImageVector get() = MicSimpleIcon
    val Plane: ImageVector get() = MessagesConversationGroup.Plain2
    val Home: ImageVector get() = EssentionalUiGroup.HomeAngle2
    val Palette: ImageVector get() = DesignToolsGroup.Palette
    val Language: ImageVector get() = MapLocationGroup.Global
    val Calendar: ImageVector get() = TimeGroup.CalendarMinimalistic
    val Visits: ImageVector get() = ShoppingEcommerceGroup.ShopMinimalistic
    val Report: ImageVector get() = NotesGroup.DocumentText
}
