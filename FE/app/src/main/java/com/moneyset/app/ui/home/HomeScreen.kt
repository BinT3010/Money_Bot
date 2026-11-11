package com.moneyset.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyset.app.core.dayMonth
import com.moneyset.app.core.toPercentText
import com.moneyset.app.core.toVnd
import com.moneyset.app.core.weekdayVi
import com.moneyset.app.domain.model.AlertLevel
import com.moneyset.app.domain.model.MonthlyOverview
import com.moneyset.app.ui.components.AmountDialog
import com.moneyset.app.ui.components.CircleIconButton
import com.moneyset.app.ui.components.GradientButton
import com.moneyset.app.ui.components.GradientCard
import com.moneyset.app.ui.components.GradientProgressBar
import com.moneyset.app.ui.components.MascotBanner
import com.moneyset.app.ui.components.MessageEffect
import com.moneyset.app.ui.components.Pill
import com.moneyset.app.ui.components.SectionTitle
import com.moneyset.app.ui.components.WhiteCard
import com.moneyset.app.ui.components.icon
import com.moneyset.app.ui.components.palette
import com.moneyset.app.ui.components.phrase
import com.moneyset.app.ui.components.rememberSpeechInput
import com.moneyset.app.ui.theme.AppGradients
import com.moneyset.app.ui.theme.Indigo
import com.moneyset.app.ui.theme.IndigoSoft
import com.moneyset.app.ui.theme.Ink
import com.moneyset.app.ui.theme.InkSoft
import com.moneyset.app.ui.theme.MintGreen
import com.moneyset.app.ui.theme.RoseRed
import com.moneyset.app.ui.theme.RoseSoft
import com.moneyset.app.ui.theme.Violet

@Composable
fun HomeScreen(
    onOpenChat: (prefill: String?, scan: Boolean) -> Unit,
    onOpenBudget: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    var showBudgetDialog by remember { mutableStateOf(false) }

    if (showBudgetDialog) {
        AmountDialog(
            title = "Ngân sách tháng ${state.today.monthValue}",
            label = "Tổng ngân sách",
            initial = state.overview?.budget ?: 0L,
            description = "Số tiền tối đa bạn dự định chi trong tháng này.",
            onDismiss = { showBudgetDialog = false },
            onSave = {
                viewModel.setMonthlyBudget(it)
                showBudgetDialog = false
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HomeHeader(state, onBellClick = onOpenBudget)
            GreetingBanner(state, onClick = { onOpenChat(null, false) })
            state.overview?.let {
                OverviewCard(it, onDetail = onOpenBudget, onSetBudget = { showBudgetDialog = true })
            }
            SectionTitle("Cảnh báo nhanh") {
                if (state.alertCount > 0) Pill("${state.alertCount} mục", RoseRed, RoseSoft)
            }
            QuickAlertsRow(state.alerts, onOpenBudget = onOpenBudget, onPayBill = viewModel::payBill)
        }

        QuickInputBar(
            onSubmit = { onOpenChat(it, false) },
            onCamera = { onOpenChat(null, true) },
            modifier = Modifier.align(Alignment.BottomCenter).imePadding(),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, onBellClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(AppGradients.Primary)),
            contentAlignment = Alignment.Center,
        ) {
            Text(state.userName.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${state.today.weekdayVi()}, ${state.today.dayMonth()}", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
            Text("Xin chào, ${state.userName} 👋", style = MaterialTheme.typography.titleLarge, color = Ink)
        }
        CircleIconButton(Icons.Rounded.Notifications, "Cảnh báo", onBellClick, showBadge = state.alertCount > 0)
    }
}

@Composable
private fun GreetingBanner(state: HomeUiState, onClick: () -> Unit) {
    val overview = state.overview
    val message = buildAnnotatedString {
        withStyle(SpanStyle(color = Violet, fontWeight = FontWeight.Bold)) { append("Chào ${state.userName}! ") }
        when {
            overview == null -> append("Mình đang tải dữ liệu tháng này nha...")
            overview.budget <= 0L && overview.spent == 0L ->
                append("Bắt đầu bằng việc đặt ngân sách tháng này, rồi nhắn mình khoản chi đầu tiên nha!")
            overview.budget <= 0L -> {
                append("Tháng này bạn đã chi ")
                withStyle(SpanStyle(color = RoseRed, fontWeight = FontWeight.Bold)) { append(overview.spent.toVnd()) }
                append(". Đặt ngân sách để mình theo dõi giúp nha!")
            }
            overview.remaining >= 0 -> {
                append("Tháng ${overview.month.monthValue} bạn còn dư ")
                withStyle(SpanStyle(color = MintGreen, fontWeight = FontWeight.Bold)) { append(overview.remaining.toVnd()) }
                append(". Hôm nay mình ghi chi tiêu gì nào?")
            }
            else -> {
                append("Tháng này đã vượt ngân sách ")
                withStyle(SpanStyle(color = RoseRed, fontWeight = FontWeight.Bold)) { append((-overview.remaining).toVnd()) }
                append(" rồi, mình cùng siết lại nha!")
            }
        }
    }
    MascotBanner(
        colors = AppGradients.Primary,
        badge = "Money Set · Đang online",
        badgeIcon = Icons.Rounded.Waves,
        message = message,
        onClick = onClick,
    )
}

@Composable
private fun OverviewCard(overview: MonthlyOverview, onDetail: () -> Unit, onSetBudget: () -> Unit) {
    if (overview.budget <= 0L) {
        WhiteCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tổng quan tháng ${overview.month.monthValue}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Bạn chưa đặt ngân sách cho tháng này. Đặt ngân sách để theo dõi đã chi bao nhiêu và còn lại bao nhiêu.",
                color = InkSoft,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (overview.spent > 0) {
                Row {
                    Text("Đã chi: ", color = InkSoft)
                    Text(overview.spent.toVnd(), color = Ink, fontWeight = FontWeight.Bold)
                }
            }
            GradientButton(text = "Đặt ngân sách tháng", onClick = onSetBudget, icon = Icons.Rounded.Savings)
        }
        return
    }
    WhiteCard(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(IndigoSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.DataUsage, null, tint = Indigo, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Text("Tổng quan tháng ${overview.month.monthValue}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Row(Modifier.clip(CircleShape).clickable(onClick = onDetail).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Chi tiết", color = InkSoft, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = InkSoft, modifier = Modifier.size(18.dp))
            }
        }
        Row {
            Column(Modifier.weight(1f)) {
                Text("Ngân sách", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                Text(overview.budget.toVnd(), style = MaterialTheme.typography.headlineSmall, color = Ink)
            }
            Box(Modifier.width(1.dp).height(48.dp).background(Color(0xFFE5E7EB)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Đã chi", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                Text(
                    overview.spent.toVnd(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (overview.usedRatio >= 0.8f) RoseRed else Ink,
                )
            }
        }
        GradientProgressBar(overview.usedRatio, AppGradients.Progress, height = 12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("${overview.usedRatio.toPercentText()} đã dùng", Indigo, IndigoSoft, icon = Icons.Rounded.DataUsage)
            Spacer(Modifier.weight(1f))
            Text("Còn lại ", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
            Text(
                overview.remaining.toVnd(),
                color = if (overview.remaining >= 0) MintGreen else RoseRed,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun QuickAlertsRow(alerts: List<QuickAlert>, onOpenBudget: () -> Unit, onPayBill: (String) -> Unit) {
    if (alerts.isEmpty()) {
        GradientCard(AppGradients.Mint, Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Mọi thứ đều ổn! Không có cảnh báo nào.", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        alerts.forEach { alert ->
            val cardModifier = Modifier.weight(1f).fillMaxHeight()
            when (alert) {
                is QuickAlert.CategoryLimit -> CategoryAlertCard(alert, cardModifier, onOpenBudget)
                is QuickAlert.BillDue -> BillAlertCard(alert, cardModifier, onPayBill)
            }
        }
    }
}

@Composable
private fun CategoryAlertCard(alert: QuickAlert.CategoryLimit, modifier: Modifier, onClick: () -> Unit) {
    val s = alert.spending
    val colors = s.level.palette.gradient
    GradientCard(colors, modifier, shape = RoundedCornerShape(24.dp), onClick = onClick) {
        Column(Modifier.fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AlertIcon(s.category.icon)
            Text(
                if (s.level == AlertLevel.OVER) "${s.category.shortLabel} đã vượt ${s.ratio.toPercentText()} hạn mức"
                else "${s.category.shortLabel} đã chạm ${s.ratio.toPercentText()} hạn mức",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            GradientProgressBar(s.ratio, listOf(Color.White, Color.White), height = 6.dp, trackColor = Color.White.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun BillAlertCard(alert: QuickAlert.BillDue, modifier: Modifier, onPay: (String) -> Unit) {
    val b = alert.bill
    GradientCard(b.status.palette.gradient, modifier, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AlertIcon(b.bill.type.icon)
            Text(
                "Hóa đơn ${b.bill.name} ${b.bill.amount.toVnd()} ${b.status.phrase()}",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Thanh toán",
                color = b.status.palette.main,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onPay(b.bill.id) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun AlertIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun QuickInputBar(onSubmit: (String) -> Unit, onCamera: () -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onSubmit(text.trim())
            text = ""
        }
    }
    val startSpeech = rememberSpeechInput { text = it }
    val fieldShape = RoundedCornerShape(30.dp)

    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(60.dp)
                .shadow(10.dp, fieldShape, spotColor = Indigo.copy(alpha = 0.3f))
                .clip(fieldShape)
                .background(Color.White)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Violet, modifier = Modifier.size(20.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("Nhập nhanh: ăn trưa 45k...", color = InkSoft, fontSize = 15.sp, maxLines = 1)
                        inner()
                    }
                },
            )
            IconButton(onClick = startSpeech) { Icon(Icons.Rounded.Mic, "Nói", tint = InkSoft) }
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(AppGradients.Mint))
                    .clickable { submit() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.Send, "Gửi", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        Box(
            Modifier
                .size(64.dp)
                .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = AppGradients.Camera.last())
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(AppGradients.Camera))
                .clickable(onClick = onCamera),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PhotoCamera, "Quét hóa đơn", tint = Color.White, modifier = Modifier.size(28.dp)) }
    }
}
