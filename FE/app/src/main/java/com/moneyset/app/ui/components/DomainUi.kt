package com.moneyset.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.moneyset.app.core.dayMonth
import com.moneyset.app.domain.model.AlertLevel
import com.moneyset.app.domain.model.BillStatus
import com.moneyset.app.domain.model.BillType
import com.moneyset.app.domain.model.BillWithStatus
import com.moneyset.app.domain.model.Category
import com.moneyset.app.ui.theme.AmberOrange
import com.moneyset.app.ui.theme.AmberSoft
import com.moneyset.app.ui.theme.AmberTint
import com.moneyset.app.ui.theme.AppGradients
import com.moneyset.app.ui.theme.Indigo
import com.moneyset.app.ui.theme.IndigoSoft
import com.moneyset.app.ui.theme.MintGreen
import com.moneyset.app.ui.theme.MintSoft
import com.moneyset.app.ui.theme.MintTint
import com.moneyset.app.ui.theme.RoseRed
import com.moneyset.app.ui.theme.RoseSoft
import com.moneyset.app.ui.theme.RoseTint
import androidx.compose.material.icons.rounded.Category as CategoryIcon

val Category.color: Color
    get() = when (this) {
        Category.FOOD -> Color(0xFFF59E0B)
        Category.SHOPPING -> Color(0xFFF43F5E)
        Category.BILLS -> Color(0xFF6366F1)
        Category.TRANSPORT -> Color(0xFF38BDF8)
        Category.ENTERTAINMENT -> Color(0xFFA855F7)
        Category.OTHER -> Color(0xFF14B8A6)
    }

val Category.icon: ImageVector
    get() = when (this) {
        Category.FOOD -> Icons.Rounded.Restaurant
        Category.SHOPPING -> Icons.Rounded.ShoppingBag
        Category.BILLS -> Icons.Rounded.Receipt
        Category.TRANSPORT -> Icons.Rounded.DirectionsCar
        Category.ENTERTAINMENT -> Icons.Rounded.SportsEsports
        Category.OTHER -> Icons.Rounded.CategoryIcon
    }

val BillType.icon: ImageVector
    get() = when (this) {
        BillType.ELECTRICITY -> Icons.Rounded.Bolt
        BillType.WATER -> Icons.Rounded.WaterDrop
        BillType.INTERNET -> Icons.Rounded.Wifi
        BillType.RENT -> Icons.Rounded.Home
        BillType.PHONE -> Icons.Rounded.PhoneAndroid
        BillType.OTHER -> Icons.Rounded.Receipt
    }

/** Bộ màu dùng cho trạng thái: màu chính, nền nhạt cho pill, nền thẻ, gradient. */
data class StatusPalette(val main: Color, val soft: Color, val tint: Color, val gradient: List<Color>)

val DangerPalette = StatusPalette(RoseRed, RoseSoft, RoseTint, AppGradients.Rose)
val WarningPalette = StatusPalette(AmberOrange, AmberSoft, AmberTint, AppGradients.Amber)
val SuccessPalette = StatusPalette(MintGreen, MintSoft, MintTint, AppGradients.Mint)
val NeutralPalette = StatusPalette(Indigo, IndigoSoft, Color.White, AppGradients.Button)

val AlertLevel.palette: StatusPalette
    get() = when (this) {
        AlertLevel.OVER -> DangerPalette
        AlertLevel.WARNING -> WarningPalette
        AlertLevel.NORMAL -> SuccessPalette
    }

val AlertLevel.label: String
    get() = when (this) {
        AlertLevel.OVER -> "Vượt hạn mức"
        AlertLevel.WARNING -> "Sắp chạm hạn mức"
        AlertLevel.NORMAL -> "Trong hạn mức"
    }

val BillStatus.palette: StatusPalette
    get() = when (this) {
        is BillStatus.Overdue, BillStatus.DueToday -> DangerPalette
        is BillStatus.DueSoon -> WarningPalette
        is BillStatus.Upcoming -> NeutralPalette
        BillStatus.Paid -> SuccessPalette
    }

/** Nhãn ngắn trên pill trạng thái. */
fun BillWithStatus.statusLabel(): String = when (val s = status) {
    is BillStatus.Overdue -> "Quá hạn ${s.days} ngày"
    BillStatus.DueToday -> "Đến hạn hôm nay"
    is BillStatus.DueSoon -> "Còn ${s.days} ngày (${dueDate.dayMonth()})"
    is BillStatus.Upcoming -> "Còn ${s.days} ngày"
    BillStatus.Paid -> "Đã thanh toán"
}

/** Cụm từ dùng trong câu nhắc: "đến hạn ngày mai", "đã quá hạn 2 ngày"... */
fun BillStatus.phrase(): String = when (this) {
    is BillStatus.Overdue -> "đã quá hạn $days ngày"
    BillStatus.DueToday -> "đến hạn hôm nay"
    is BillStatus.DueSoon -> if (days == 1) "đến hạn ngày mai" else "còn $days ngày"
    is BillStatus.Upcoming -> "còn $days ngày"
    BillStatus.Paid -> "đã thanh toán"
}
