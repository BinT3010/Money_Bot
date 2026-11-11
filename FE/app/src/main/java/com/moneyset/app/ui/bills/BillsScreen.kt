package com.moneyset.app.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyset.app.core.dayMonth
import com.moneyset.app.core.label
import com.moneyset.app.core.toVnd
import com.moneyset.app.domain.model.BillStatus
import com.moneyset.app.domain.model.BillType
import com.moneyset.app.domain.model.BillWithStatus
import com.moneyset.app.domain.model.isUrgent
import com.moneyset.app.ui.components.DatePickerModal
import com.moneyset.app.ui.components.GradientButton
import com.moneyset.app.ui.components.IconTile
import com.moneyset.app.ui.components.MascotBanner
import com.moneyset.app.ui.components.MessageEffect
import com.moneyset.app.ui.components.MoneyTextField
import com.moneyset.app.ui.components.Pill
import com.moneyset.app.ui.components.ScreenTopBar
import com.moneyset.app.ui.components.SectionTitle
import com.moneyset.app.ui.components.WhiteCard
import com.moneyset.app.ui.components.icon
import com.moneyset.app.ui.components.palette
import com.moneyset.app.ui.components.phrase
import com.moneyset.app.ui.components.statusLabel
import com.moneyset.app.ui.theme.AppGradients
import com.moneyset.app.ui.theme.Indigo
import com.moneyset.app.ui.theme.Ink
import com.moneyset.app.ui.theme.InkSoft
import com.moneyset.app.ui.theme.MintGreen
import com.moneyset.app.ui.theme.RoseRed

@Composable
fun BillsScreen(onBack: () -> Unit, viewModel: BillsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            ScreenTopBar(overline = "Hóa đơn định kỳ", title = state.month.label(), onBack = onBack) {
                SimulatedDateChip(state, onClick = { showDatePicker = true })
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BillsAlertBanner(state.bills)
                SummaryCard(state)
                GradientButton(
                    text = "Thêm hóa đơn định kỳ",
                    onClick = { showAddDialog = true },
                    icon = Icons.Rounded.Add,
                )
                SectionTitle("Danh sách hóa đơn")
                if (state.bills.isEmpty()) {
                    Text("Chưa có hóa đơn nào", color = InkSoft)
                }
                state.bills.forEach { BillCard(it, onMarkPaid = { viewModel.markPaid(it.bill.id) }) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (showDatePicker) {
        DatePickerModal(state.today, onDismiss = { showDatePicker = false }, onConfirm = viewModel::setToday)
    }
    if (showAddDialog) {
        AddBillDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, amount, day, type ->
                viewModel.addBill(name, amount, day, type)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun SimulatedDateChip(state: BillsUiState, onClick: () -> Unit) {
    Row(
        Modifier
            .shadow(4.dp, CircleShape, spotColor = Indigo.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CalendarMonth, null, tint = Ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Giả lập: ${state.today.dayMonth()}", style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

@Composable
private fun BillsAlertBanner(bills: List<BillWithStatus>) {
    val urgent = bills.filter { it.status.isUrgent }
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    if (urgent.isEmpty()) {
        MascotBanner(
            colors = AppGradients.Mint,
            badge = "Nhắc hạn thanh toán",
            badgeIcon = Icons.Rounded.Celebration,
            message = buildAnnotatedString {
                append("Không có hóa đơn nào sắp đến hạn. ")
                withStyle(bold.copy(color = MintGreen)) { append("Thảnh thơi nha!") }
                append(" 🎉")
            },
        )
        return
    }
    val overdue = urgent.filter { it.status is BillStatus.Overdue }
    val dueSoon = urgent - overdue.toSet()
    MascotBanner(
        colors = if (overdue.isNotEmpty()) AppGradients.Rose else AppGradients.Amber,
        badge = "Nhắc hạn thanh toán",
        badgeIcon = Icons.Rounded.NotificationsActive,
        message = buildAnnotatedString {
            withStyle(bold.copy(color = RoseRed)) { append(if (dueSoon.isNotEmpty()) "Sắp tới hạn! " else "Quá hạn rồi! ") }
            val parts = (dueSoon + overdue).take(2)
            parts.forEachIndexed { i, b ->
                if (i > 0) append(" và ")
                append("${b.bill.name} ")
                withStyle(bold) { append(b.bill.amount.toVnd()) }
                append(" ${b.status.phrase()}")
            }
            if (urgent.size > parts.size) append(" (+${urgent.size - parts.size} hóa đơn khác)")
            append("!")
        },
    )
}

@Composable
private fun SummaryCard(state: BillsUiState) {
    WhiteCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tổng hóa đơn tháng ${state.month.monthValue}: ", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(state.total.toVnd(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Đã trả: ", color = InkSoft)
            Text(state.paid.toVnd(), color = MintGreen, fontWeight = FontWeight.Bold)
            Text("   |   Còn đóng: ", color = InkSoft)
            Text(state.remaining.toVnd(), color = if (state.remaining > 0) RoseRed else MintGreen, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BillCard(item: BillWithStatus, onMarkPaid: () -> Unit) {
    val palette = item.status.palette
    val isPaid = item.status == BillStatus.Paid
    WhiteCard(
        containerColor = palette.tint,
        borderColor = palette.main.copy(alpha = 0.45f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(item.bill.type.icon, palette.gradient, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.bill.name, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1)
                Pill(item.statusLabel(), Color.White, palette.main)
                Text("Hạn ngày ${item.bill.dueDay} hằng tháng", color = InkSoft, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.bill.amount.toVnd(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Ink)
                if (isPaid) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, palette.main, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = palette.main, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Đã trả", color = palette.main, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Text(
                        "Đánh dấu đã trả",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(palette.gradient))
                            .clickable(onClick = onMarkPaid)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddBillDialog(onDismiss: () -> Unit, onAdd: (String, Long, Int, BillType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(BillType.ELECTRICITY) }
    val dueDay = day.toIntOrNull()
    val amountValue = amount.toLongOrNull() ?: 0L
    val valid = name.isNotBlank() && amountValue > 0 && dueDay != null && dueDay in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Thêm hóa đơn định kỳ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên hóa đơn") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyTextField(amount, { amount = it }, label = "Số tiền")
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    label = { Text("Hạn ngày (1–31) hằng tháng") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Loại", color = InkSoft, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BillType.entries.forEach { t ->
                        FilterChip(
                            selected = t == type,
                            onClick = { type = t },
                            label = { Text(t.label) },
                            leadingIcon = { Icon(t.icon, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onAdd(name.trim(), amountValue, dueDay, type) }, enabled = valid) { Text("Thêm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}
