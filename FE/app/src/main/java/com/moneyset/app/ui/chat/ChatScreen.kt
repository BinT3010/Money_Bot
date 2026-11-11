package com.moneyset.app.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.moneyset.app.core.fullDate
import com.moneyset.app.core.toVnd
import com.moneyset.app.domain.model.Category
import com.moneyset.app.domain.model.ExpenseDraft
import com.moneyset.app.domain.model.Transaction
import com.moneyset.app.ui.components.CircleIconButton
import com.moneyset.app.ui.components.DatePickerModal
import com.moneyset.app.ui.components.GradientButton
import com.moneyset.app.ui.components.Mascot
import com.moneyset.app.ui.components.MessageEffect
import com.moneyset.app.ui.components.Pill
import com.moneyset.app.ui.components.ReceiptCaptureButton
import com.moneyset.app.ui.components.ThousandsSeparatorTransformation
import com.moneyset.app.ui.components.TransactionRow
import com.moneyset.app.ui.components.color
import com.moneyset.app.ui.components.icon
import com.moneyset.app.ui.components.rememberSpeechInput
import com.moneyset.app.ui.theme.AppGradients
import com.moneyset.app.ui.theme.Indigo
import com.moneyset.app.ui.theme.IndigoSoft
import com.moneyset.app.ui.theme.Ink
import com.moneyset.app.ui.theme.InkSoft
import com.moneyset.app.ui.theme.MintGreen
import com.moneyset.app.ui.theme.MintSoft
import com.moneyset.app.ui.theme.Violet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun ChatScreen(onBack: () -> Unit, viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)

    var showHistory by rememberSaveable { mutableStateOf(false) }
    var scannerMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.openScanner) {
        if (state.openScanner) {
            scannerMenuOpen = true
            viewModel.scannerOpened()
        }
    }

    val listState = rememberLazyListState()
    val itemCount = 1 + state.messages.size + if (state.isThinking) 1 else 0
    LaunchedEffect(itemCount) { listState.animateScrollToItem(itemCount - 1) }

    Box(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            ChatTopBar(onBack = onBack, onHistory = { showHistory = true })
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { DateSeparator("Hôm nay") }
                items(state.messages, key = { it.id }) { msg ->
                    if (msg.fromUser) {
                        UserMessage(msg)
                    } else {
                        AssistantMessage(
                            message = msg,
                            onAmountChange = { v -> viewModel.updateDraft(msg.id) { it.copy(amount = v) } },
                            onNoteChange = { v -> viewModel.updateDraft(msg.id) { it.copy(note = v) } },
                            onCategoryChange = { v -> viewModel.updateDraft(msg.id) { it.copy(category = v) } },
                            onDateChange = { v -> viewModel.updateDraft(msg.id) { it.copy(date = v) } },
                            onConfirm = { viewModel.confirm(msg.id) },
                        )
                    }
                }
                if (state.isThinking) item { TypingIndicator() }
            }
            ChatInputBar(
                enabled = !state.isThinking,
                scannerMenuOpen = scannerMenuOpen,
                onScannerMenuChange = { scannerMenuOpen = it },
                onSend = viewModel::send,
                onBitmap = viewModel::sendBitmap,
                onImage = viewModel::sendImage,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    if (showHistory) {
        HistorySheet(state.transactions, state.today, onDismiss = { showHistory = false })
    }
}

@Composable
private fun ChatTopBar(onBack: () -> Unit, onHistory: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại", onBack)
        Spacer(Modifier.width(12.dp))
        Box {
            Mascot(Modifier.size(46.dp).clip(CircleShape).background(IndigoSoft))
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MintGreen),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Money Set", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text("Đang online · Chi tiêu chỉ một chạm", style = MaterialTheme.typography.bodySmall, color = MintGreen)
        }
        CircleIconButton(Icons.Rounded.History, "Lịch sử giao dịch", onHistory)
    }
}

@Composable
private fun DateSeparator(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = Color(0xFFE5E7EB))
        Text(text, Modifier.padding(horizontal = 12.dp), color = InkSoft, style = MaterialTheme.typography.labelMedium)
        HorizontalDivider(Modifier.weight(1f), color = Color(0xFFE5E7EB))
    }
}

@Composable
private fun UserMessage(message: ChatMessage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        message.text?.let {
            Text(
                it,
                color = Color.White,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .background(Brush.linearGradient(AppGradients.Button))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        message.imageUri?.let {
            AsyncImage(
                model = it,
                contentDescription = "Ảnh hóa đơn",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 180.dp, height = 140.dp)
                    .shadow(6.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .border(3.dp, Color.White, RoundedCornerShape(20.dp)),
            )
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    onAmountChange: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onCategoryChange: (Category) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Mascot(Modifier.size(36.dp).clip(CircleShape).background(IndigoSoft))
            Spacer(Modifier.width(8.dp))
            message.text?.let {
                Text(
                    it,
                    color = Ink,
                    modifier = Modifier
                        .widthIn(max = 290.dp)
                        .shadow(3.dp, RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp), spotColor = Indigo.copy(alpha = 0.2f))
                        .clip(RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp))
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        message.draft?.let { draft ->
            ExpenseDraftCard(
                messageId = message.id,
                draft = draft,
                state = message.draftState,
                onAmountChange = onAmountChange,
                onNoteChange = onNoteChange,
                onCategoryChange = onCategoryChange,
                onDateChange = onDateChange,
                onConfirm = onConfirm,
                modifier = Modifier.padding(start = 44.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseDraftCard(
    messageId: Long,
    draft: ExpenseDraft,
    state: DraftState,
    onAmountChange: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onCategoryChange: (Category) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editable = state == DraftState.EDITING
    var amountText by remember(messageId) { mutableStateOf(if (draft.amount > 0) draft.amount.toString() else "") }
    var noteText by remember(messageId) { mutableStateOf(draft.note) }
    var showDatePicker by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, spotColor = Indigo.copy(alpha = 0.2f))
            .clip(shape)
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(IndigoSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Receipt, null, tint = Indigo, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Xác nhận giao dịch", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Pill("AI ${(draft.confidence * 100).toInt()}%", MintGreen, MintSoft, icon = Icons.Rounded.AutoAwesome)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(IndigoSoft)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text("Số tiền", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                val amountStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                BasicTextField(
                    value = amountText,
                    onValueChange = { v ->
                        val digits = v.filter(Char::isDigit).take(12)
                        amountText = digits
                        onAmountChange(digits.toLongOrNull() ?: 0L)
                    },
                    enabled = editable,
                    singleLine = true,
                    textStyle = amountStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandsSeparatorTransformation,
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                if (amountText.isEmpty()) Text("0", style = amountStyle.copy(color = InkSoft.copy(alpha = 0.4f)))
                                inner()
                            }
                            Text(" đ", style = amountStyle.copy(fontSize = 18.sp))
                        }
                    },
                )
            }
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(IndigoSoft)
                    .clickable(enabled = editable) { showDatePicker = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = Indigo, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(draft.date.fullDate(), color = Indigo, style = MaterialTheme.typography.labelLarge)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ghi chú", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                BasicTextField(
                    value = noteText,
                    onValueChange = {
                        noteText = it
                        onNoteChange(it)
                    },
                    enabled = editable,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = Ink),
                )
            }
            Icon(Icons.Rounded.Edit, null, tint = InkSoft, modifier = Modifier.size(18.dp))
        }

        Text("Danh mục", style = MaterialTheme.typography.labelLarge, color = InkSoft)
        FlowRow(
            maxItemsInEachRow = 3,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Category.entries.forEach { c ->
                val selected = c == draft.category
                Row(
                    Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (selected) c.color else c.color.copy(alpha = 0.1f))
                        .clickable(enabled = editable) { onCategoryChange(c) }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(c.icon, null, tint = if (selected) Color.White else c.color, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        c.shortLabel,
                        color = if (selected) Color.White else c.color,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }

        when (state) {
            DraftState.CONFIRMED -> Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MintSoft).padding(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MintGreen)
                Spacer(Modifier.width(8.dp))
                Text("Đã lưu giao dịch", color = MintGreen, fontWeight = FontWeight.Bold)
            }
            else -> GradientButton(
                text = "Xác nhận lưu",
                onClick = onConfirm,
                colors = AppGradients.Mint,
                icon = Icons.Rounded.CheckCircle,
                loading = state == DraftState.SAVING,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bolt, null, tint = MintGreen, modifier = Modifier.size(14.dp))
            Text(
                " Chỉ cần 1 chạm để lưu — thay vì 3 chạm",
                style = MaterialTheme.typography.labelSmall,
                color = MintGreen,
            )
        }
    }

    if (showDatePicker) {
        DatePickerModal(draft.date, onDismiss = { showDatePicker = false }, onConfirm = onDateChange)
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Mascot(Modifier.size(36.dp).clip(CircleShape).background(IndigoSoft))
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { i ->
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse, StartOffset(i * 160)),
                    label = "dot$i",
                )
                Box(Modifier.size(8.dp).clip(CircleShape).background(Violet.copy(alpha = alpha)))
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    enabled: Boolean,
    scannerMenuOpen: Boolean,
    onScannerMenuChange: (Boolean) -> Unit,
    onSend: (String) -> Unit,
    onBitmap: (android.graphics.Bitmap) -> Unit,
    onImage: (android.net.Uri) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank() && enabled) {
            onSend(text)
            text = ""
        }
    }
    val startSpeech = rememberSpeechInput { text = it }
    val shape = RoundedCornerShape(26.dp)

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReceiptCaptureButton(
            expanded = scannerMenuOpen,
            onExpandedChange = onScannerMenuChange,
            onBitmap = onBitmap,
            onUri = onImage,
        )
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .shadow(6.dp, shape, spotColor = Indigo.copy(alpha = 0.25f))
                .clip(shape)
                .background(Color.White)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                maxLines = 4,
                textStyle = TextStyle(fontSize = 15.sp, color = Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("Nhắn cho Money Set...", color = InkSoft, fontSize = 15.sp)
                        inner()
                    }
                },
            )
            IconButton(onClick = startSpeech) { Icon(Icons.Rounded.Mic, "Nói", tint = InkSoft) }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(AppGradients.Button), alpha = if (enabled) 1f else 0.5f)
                    .clickable(enabled = enabled) { submit() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.Send, "Gửi", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
    }
}

private enum class HistoryFilter(val label: String) {
    ALL("Tất cả"), TODAY("Hôm nay"), WEEK("Tuần này"), FOOD("Ăn uống"), BILLS("Hóa đơn");

    fun matches(t: Transaction, today: LocalDate): Boolean = when (this) {
        ALL -> true
        TODAY -> t.date == today
        WEEK -> !t.date.isBefore(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) && !t.date.isAfter(today)
        FOOD -> t.category == Category.FOOD
        BILLS -> t.category == Category.BILLS
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(transactions: List<Transaction>, today: LocalDate, onDismiss: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    val filtered = transactions.filter { filter.matches(it, today) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, null, tint = Ink)
                Spacer(Modifier.width(8.dp))
                Text("Lịch sử giao dịch", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${filter.label}: -${filtered.sumOf { it.amount }.toVnd()}", color = InkSoft, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HistoryFilter.entries) { f ->
                    FilterChip(
                        selected = f == filter,
                        onClick = { filter = f },
                        label = { Text(f.label) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Ink,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Text(
                    "Chưa có giao dịch nào",
                    color = InkSoft,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(filtered) { TransactionRow(it) }
                }
            }
        }
    }
}
