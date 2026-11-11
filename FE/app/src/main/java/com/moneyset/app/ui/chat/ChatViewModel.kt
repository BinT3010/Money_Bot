package com.moneyset.app.ui.chat

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyset.app.core.suspendRunCatching
import com.moneyset.app.core.toUserMessage
import com.moneyset.app.core.toVnd
import com.moneyset.app.core.toPercentText
import com.moneyset.app.data.ImageStore
import com.moneyset.app.data.repository.FinanceRepository
import com.moneyset.app.domain.model.AlertLevel
import com.moneyset.app.domain.model.CategorySpending
import com.moneyset.app.domain.model.ExpenseDraft
import com.moneyset.app.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DraftState { EDITING, SAVING, CONFIRMED }

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String? = null,
    val imageUri: String? = null,
    val draft: ExpenseDraft? = null,
    val draftState: DraftState = DraftState.EDITING,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isThinking: Boolean = false,
    val transactions: List<Transaction> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val openScanner: Boolean = false,
    val message: String? = null,
)

private data class ChatLocalState(
    val messages: List<ChatMessage>,
    val isThinking: Boolean = false,
    val openScanner: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: FinanceRepository,
    private val imageStore: ImageStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var nextId = 0L
    private val local = MutableStateFlow(
        ChatLocalState(messages = listOf(ChatMessage(id = nextId++, fromUser = false, text = GREETING))),
    )

    val state: StateFlow<ChatUiState> = combine(local, repository.transactions, repository.today) { l, txs, today ->
        ChatUiState(l.messages, l.isThinking, txs, today, l.openScanner, l.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState(messages = local.value.messages))

    init {
        viewModelScope.launch { repository.ensureLoaded() }
        // Tham số điều hướng từ màn Home: chỉ xử lý một lần
        if (savedStateHandle.get<Boolean>(KEY_HANDLED) != true) {
            savedStateHandle[KEY_HANDLED] = true
            val prefill = savedStateHandle.get<String>(ARG_PREFILL).orEmpty()
            if (prefill.isNotBlank()) send(prefill)
            if (savedStateHandle.get<Boolean>(ARG_SCAN) == true) local.update { it.copy(openScanner = true) }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || local.value.isThinking) return
        append(ChatMessage(nextId++, fromUser = true, text = trimmed))
        runAi(intro = "Mình đọc được thế này, bạn kiểm tra giúp mình nha 👇") { repository.parseExpense(trimmed) }
    }

    fun sendImage(uri: Uri) {
        if (local.value.isThinking) return
        append(ChatMessage(nextId++, fromUser = true, imageUri = uri.toString()))
        runAi(intro = "Mình đọc xong hóa đơn rồi nè! Bạn kiểm tra giúp mình nha 👇") {
            val bytes = suspendRunCatching { imageStore.readBytes(uri) }.getOrElse { return@runAi Result.failure(it) }
            repository.scanReceipt(bytes, imageStore.mimeType(uri)).map { it.copy(receiptUri = uri.toString()) }
        }
    }

    fun sendBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            suspendRunCatching { imageStore.saveBitmap(bitmap) }
                .onSuccess { sendImage(it) }
                .onFailure { e -> local.update { it.copy(message = e.toUserMessage()) } }
        }
    }

    private fun runAi(intro: String, block: suspend () -> Result<ExpenseDraft>) {
        viewModelScope.launch {
            local.update { it.copy(isThinking = true) }
            val result = block()
            local.update { it.copy(isThinking = false) }
            result
                .onSuccess { draft ->
                    val text = if (draft.amount <= 0) "Mình chưa thấy số tiền, bạn nhập giúp mình ở thẻ bên dưới nha 🙏" else intro
                    append(ChatMessage(nextId++, fromUser = false, text = text, draft = draft))
                }
                .onFailure {
                    append(ChatMessage(nextId++, fromUser = false, text = "Ui, mình gặp lỗi: ${it.toUserMessage()}. Bạn thử lại nhé!"))
                }
        }
    }

    fun updateDraft(messageId: Long, transform: (ExpenseDraft) -> ExpenseDraft) {
        local.update { s ->
            s.copy(messages = s.messages.map { m ->
                if (m.id == messageId && m.draft != null && m.draftState == DraftState.EDITING) m.copy(draft = transform(m.draft)) else m
            })
        }
    }

    fun confirm(messageId: Long) {
        val msg = local.value.messages.firstOrNull { it.id == messageId } ?: return
        val draft = msg.draft ?: return
        if (msg.draftState != DraftState.EDITING) return
        if (draft.amount <= 0) {
            local.update { it.copy(message = "Bạn nhập số tiền giúp mình nha") }
            return
        }
        setDraftState(messageId, DraftState.SAVING)
        viewModelScope.launch {
            repository.addTransaction(draft)
                .onSuccess { tx ->
                    setDraftState(messageId, DraftState.CONFIRMED)
                    val spending = repository.categorySpending.first().firstOrNull { it.category == tx.category }
                    append(ChatMessage(nextId++, fromUser = false, text = feedbackFor(tx, spending)))
                }
                .onFailure { e ->
                    setDraftState(messageId, DraftState.EDITING)
                    local.update { it.copy(message = e.toUserMessage()) }
                }
        }
    }

    private fun feedbackFor(tx: Transaction, spending: CategorySpending?): String {
        val label = tx.category.shortLabel
        if (spending == null || spending.limit <= 0 || YearMonth.from(tx.date) != repository.currentMonth) {
            return "Đã lưu ${tx.amount.toVnd()} vào mục $label ✅"
        }
        return when (spending.level) {
            AlertLevel.OVER ->
                "Ôi, khoản này làm mục $label vượt hạn mức ${(-spending.remaining).toVnd()} rồi. Mình cùng hãm lại nha 🫣"
            AlertLevel.WARNING ->
                "Bạn ơi, khoản này làm mục $label chạm ${spending.ratio.toPercentText()} hạn mức. Còn ${spending.remaining.toVnd()} thôi nè!"
            AlertLevel.NORMAL ->
                "Đã lưu! Mục $label còn ${spending.remaining.toVnd()} cho tháng này 💪"
        }
    }

    fun scannerOpened() = local.update { it.copy(openScanner = false) }

    fun messageShown() = local.update { it.copy(message = null) }

    private fun setDraftState(id: Long, state: DraftState) {
        local.update { s -> s.copy(messages = s.messages.map { if (it.id == id) it.copy(draftState = state) else it }) }
    }

    private fun append(message: ChatMessage) {
        local.update { it.copy(messages = it.messages + message) }
    }

    companion object {
        const val ARG_PREFILL = "prefill"
        const val ARG_SCAN = "scan"
        private const val KEY_HANDLED = "nav_args_handled"
        private const val GREETING =
            "Chào bạn! Cứ nhắn kiểu “ăn trưa cơm tấm 55k” hoặc gửi ảnh hóa đơn, mình sẽ ghi giúp chỉ với 1 chạm ✨"
    }
}
