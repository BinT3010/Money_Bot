package com.moneyset.app.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyset.app.core.formatMoney
import com.moneyset.app.core.label
import com.moneyset.app.core.toPercentText
import com.moneyset.app.core.toVnd
import com.moneyset.app.domain.model.AlertLevel
import com.moneyset.app.domain.model.Category
import com.moneyset.app.domain.model.CategorySpending
import com.moneyset.app.ui.components.AmountDialog
import com.moneyset.app.ui.components.CircleIconButton
import com.moneyset.app.ui.components.DonutChart
import com.moneyset.app.ui.components.DonutSlice
import com.moneyset.app.ui.components.GradientButton
import com.moneyset.app.ui.components.GradientProgressBar
import com.moneyset.app.ui.components.IconTile
import com.moneyset.app.ui.components.MascotBanner
import com.moneyset.app.ui.components.MessageEffect
import com.moneyset.app.ui.components.MoneyTextField
import com.moneyset.app.ui.components.Pill
import com.moneyset.app.ui.components.ScreenTopBar
import com.moneyset.app.ui.components.SectionTitle
import com.moneyset.app.ui.components.WhiteCard
import com.moneyset.app.ui.components.color
import com.moneyset.app.ui.components.icon
import com.moneyset.app.ui.components.label
import com.moneyset.app.ui.components.palette
import com.moneyset.app.ui.theme.AppGradients
import com.moneyset.app.ui.theme.Ink
import com.moneyset.app.ui.theme.InkSoft
import com.moneyset.app.ui.theme.MintGreen
import com.moneyset.app.ui.theme.RoseRed

@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    onOpenBills: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    var editing by remember { mutableStateOf<CategorySpending?>(null) }
    var editingTotal by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            ScreenTopBar(overline = "Ngân sách & Cảnh báo", title = state.month.label(), onBack = onBack) {
                CircleIconButton(Icons.Rounded.CalendarMonth, "Hóa đơn định kỳ", onOpenBills)
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BudgetAlertBanner(state.spending)
                TotalBudgetCard(state, onEdit = { editingTotal = true })
                DistributionCard(state, onSuggest = viewModel::requestSuggestions)
                SectionTitle("Hạn mức danh mục") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Edit, null, tint = InkSoft, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Chạm số để sửa", color = InkSoft, style = MaterialTheme.typography.labelMedium)
                    }
                }
                state.visibleSpending.forEach { s ->
                    CategoryLimitCard(s, onEditLimit = { editing = s })
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (editingTotal) {
        AmountDialog(
            title = "Ngân sách ${state.month.label().lowercase()}",
            label = "Tổng ngân sách",
            initial = state.overview?.budget ?: 0L,
            description = "Số tiền tối đa bạn dự định chi trong tháng này.",
            onDismiss = { editingTotal = false },
            onSave = {
                viewModel.updateTotalBudget(it)
                editingTotal = false
            },
        )
    }
    editing?.let { s ->
        EditLimitDialog(
            spending = s,
            onDismiss = { editing = null },
            onSave = {
                viewModel.updateLimit(s.category, it)
                editing = null
            },
        )
    }
    state.suggestions?.let {
        SuggestionsDialog(
            suggestions = it,
            current = state.spending,
            onApply = viewModel::applySuggestions,
            onDismiss = viewModel::dismissSuggestions,
        )
    }
}

@Composable
private fun BudgetAlertBanner(spending: List<CategorySpending>) {
    val over = spending.filter { it.level == AlertLevel.OVER }.maxByOrNull { it.ratio }
    val warning = spending.filter { it.level == AlertLevel.WARNING }.maxByOrNull { it.ratio }
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    when {
        over != null -> MascotBanner(
            colors = AppGradients.Rose,
            badge = "Cảnh báo hạn mức",
            badgeIcon = Icons.Rounded.Warning,
            message = buildAnnotatedString {
                withStyle(bold.copy(color = RoseRed)) { append("Báo động: ") }
                append("${over.category.shortLabel} đã vượt hạn mức ")
                withStyle(bold) { append((-over.remaining).toVnd()) }
                append(", bạn nhớ hãm lại nha! 🫣")
            },
        )
        warning != null -> MascotBanner(
            colors = AppGradients.Amber,
            badge = "Sắp chạm hạn mức",
            badgeIcon = Icons.Rounded.Campaign,
            message = buildAnnotatedString {
                append("Mục ${warning.category.shortLabel} đã dùng ")
                withStyle(bold) { append(warning.ratio.toPercentText()) }
                append(" hạn mức, chỉ còn ")
                withStyle(bold) { append(warning.remaining.toVnd()) }
                append(" thôi nè.")
            },
        )
        spending.none { it.limit > 0 } -> MascotBanner(
            colors = AppGradients.Primary,
            badge = "Bắt đầu nào",
            badgeIcon = Icons.Rounded.AutoAwesome,
            message = buildAnnotatedString {
                append("Đặt ")
                withStyle(bold) { append("ngân sách tháng") }
                append(" và ")
                withStyle(bold) { append("hạn mức từng danh mục") }
                append(" để mình cảnh báo khi bạn sắp chi quá tay nha!")
            },
        )
        else -> MascotBanner(
            colors = AppGradients.Primary,
            badge = "Mọi thứ ổn áp",
            badgeIcon = Icons.Rounded.ThumbUp,
            message = buildAnnotatedString {
                append("Tuyệt! Các danh mục đều ")
                withStyle(bold.copy(color = MintGreen)) { append("trong hạn mức") }
                append(". Cứ thế phát huy nha ✨")
            },
        )
    }
}

@Composable
private fun TotalBudgetCard(state: BudgetUiState, onEdit: () -> Unit) {
    val overview = state.overview
    val budget = overview?.budget ?: 0L
    WhiteCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ngân sách tháng", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (budget > 0) budget.toVnd() else "Chưa đặt",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (budget > 0) Ink else InkSoft,
                )
            }
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE5E7EB), CircleShape)
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Edit, null, tint = InkSoft, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (budget > 0) "Sửa" else "Đặt ngân sách", color = Ink, fontWeight = FontWeight.SemiBold)
            }
        }
        if (overview != null && budget > 0) {
            GradientProgressBar(overview.usedRatio, AppGradients.Progress)
            Row {
                Text("Đã chi ${overview.spent.toVnd()}", color = InkSoft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    if (overview.remaining >= 0) "Còn lại ${overview.remaining.toVnd()}" else "Vượt ${(-overview.remaining).toVnd()}",
                    color = if (overview.remaining >= 0) MintGreen else RoseRed,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DistributionCard(state: BudgetUiState, onSuggest: () -> Unit) {
    val withSpending = state.spending.filter { it.spent > 0 }
    val total = state.totalSpent
    WhiteCard(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Phân bổ chi tiêu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${withSpending.size} danh mục", color = InkSoft, style = MaterialTheme.typography.labelMedium)
        }
        if (withSpending.isEmpty()) {
            Text(
                "Chưa có khoản chi nào trong tháng này. Ghi chi tiêu ở Trang chủ hoặc chat với Money Set để xem biểu đồ.",
                color = InkSoft,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = withSpending.map { DonutSlice(it.category.color, it.spent.toFloat()) },
                modifier = Modifier.size(150.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        (state.overview?.usedRatio ?: 0f).toPercentText(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Ink,
                    )
                    Text("đã chi", color = InkSoft, style = MaterialTheme.typography.labelSmall)
                    Text(total.toVnd(), color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                withSpending.sortedByDescending { it.spent }.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(s.category.color))
                        Spacer(Modifier.width(8.dp))
                        Text(s.category.shortLabel, color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            (if (total > 0) s.spent.toFloat() / total else 0f).toPercentText(),
                            color = Ink,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        GradientButton(
            text = "Đề xuất hạn mức từ lịch sử tháng",
            onClick = onSuggest,
            colors = AppGradients.Primary,
            icon = Icons.Rounded.AutoAwesome,
            loading = state.isSuggesting,
        )
    }
}

@Composable
private fun CategoryLimitCard(s: CategorySpending, onEditLimit: () -> Unit) {
    val palette = s.level.palette
    val highlighted = s.level != AlertLevel.NORMAL
    WhiteCard(
        containerColor = if (highlighted) palette.tint else Color.White,
        borderColor = if (highlighted) palette.main.copy(alpha = 0.5f) else null,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(s.category.icon, listOf(s.category.color.copy(alpha = 0.7f), s.category.color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.category.label, style = MaterialTheme.typography.titleMedium, color = Ink)
                if (s.limit > 0) {
                    Pill(s.level.label, palette.main, palette.soft)
                } else {
                    Pill("Chưa đặt hạn mức", InkSoft, Color(0xFFF3F4F6))
                }
            }
            Text(
                s.ratio.toPercentText(),
                style = MaterialTheme.typography.headlineSmall,
                color = if (highlighted) palette.main else Ink,
            )
        }
        GradientProgressBar(
            s.ratio,
            if (highlighted) palette.gradient else listOf(s.category.color.copy(alpha = 0.6f), s.category.color),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Đã chi ", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
            Text(s.spent.toVnd(), color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE5E7EB), CircleShape)
                    .clickable(onClick = onEditLimit)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("/ ", color = InkSoft)
                Text(if (s.limit > 0) s.limit.toVnd() else "Đặt hạn mức", color = Ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Edit, "Sửa hạn mức", tint = InkSoft, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun EditLimitDialog(spending: CategorySpending, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var value by remember { mutableStateOf(if (spending.limit > 0) spending.limit.toString() else "") }
    val amount = value.toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Hạn mức ${spending.category.shortLabel}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tháng này đã chi ${spending.spent.toVnd()}", color = InkSoft)
                MoneyTextField(value, { value = it }, label = "Hạn mức mới")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(amount) }, enabled = amount > 0) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

@Composable
private fun SuggestionsDialog(
    suggestions: Map<Category, Long>,
    current: List<CategorySpending>,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = { Icon(Icons.Rounded.AutoAwesome, null) },
        title = { Text("Đề xuất hạn mức") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Dựa trên nhịp chi tiêu của bạn, Money Set gợi ý:", color = InkSoft)
                suggestions.forEach { (category, suggested) ->
                    val old = current.firstOrNull { it.category == category }?.limit ?: 0L
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(category.color))
                        Spacer(Modifier.width(8.dp))
                        Text(category.shortLabel, modifier = Modifier.weight(1f), color = Ink)
                        Text(old.formatMoney(), color = InkSoft, style = MaterialTheme.typography.bodySmall)
                        Text("  →  ", color = InkSoft)
                        Text(
                            suggested.formatMoney(),
                            fontWeight = FontWeight.Bold,
                            color = if (suggested > old) RoseRed else MintGreen,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("Áp dụng tất cả") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Để sau") } },
    )
}
