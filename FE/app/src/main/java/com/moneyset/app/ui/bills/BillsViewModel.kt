package com.moneyset.app.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyset.app.core.toUserMessage
import com.moneyset.app.data.repository.FinanceRepository
import com.moneyset.app.domain.model.Bill
import com.moneyset.app.domain.model.BillStatus
import com.moneyset.app.domain.model.BillType
import com.moneyset.app.domain.model.BillWithStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BillsUiState(
    val today: LocalDate = LocalDate.now(),
    val bills: List<BillWithStatus> = emptyList(),
    val message: String? = null,
) {
    val month: YearMonth get() = YearMonth.from(today)
    val total: Long get() = bills.sumOf { it.bill.amount }
    val paid: Long get() = bills.filter { it.status == BillStatus.Paid }.sumOf { it.bill.amount }
    val remaining: Long get() = total - paid
}

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val repository: FinanceRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<BillsUiState> = combine(repository.today, repository.bills, message) { today, bills, msg ->
        BillsUiState(today, bills, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsUiState())

    init {
        viewModelScope.launch { repository.ensureLoaded().onFailure { message.value = it.toUserMessage() } }
    }

    /** Giả lập ngày hôm nay để xem trạng thái quá hạn / sắp tới hạn. */
    fun setToday(date: LocalDate) {
        viewModelScope.launch { repository.setToday(date).onFailure { message.value = it.toUserMessage() } }
    }

    fun addBill(name: String, amount: Long, dueDay: Int, type: BillType) {
        viewModelScope.launch {
            repository.addBill(Bill(id = "", name = name, amount = amount, dueDay = dueDay, type = type))
                .onSuccess { message.value = "Đã thêm hóa đơn $name" }
                .onFailure { message.value = it.toUserMessage() }
        }
    }

    fun markPaid(billId: String) {
        viewModelScope.launch {
            repository.markBillPaid(billId)
                .onSuccess { message.value = "Đã đánh dấu thanh toán ✓" }
                .onFailure { message.value = it.toUserMessage() }
        }
    }

    fun messageShown() {
        message.value = null
    }
}
