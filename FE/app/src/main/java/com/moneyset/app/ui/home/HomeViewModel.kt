package com.moneyset.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyset.app.core.toUserMessage
import com.moneyset.app.data.auth.AuthRepository
import com.moneyset.app.data.repository.FinanceRepository
import com.moneyset.app.domain.model.AlertLevel
import com.moneyset.app.domain.model.BillWithStatus
import com.moneyset.app.domain.model.CategorySpending
import com.moneyset.app.domain.model.MonthlyOverview
import com.moneyset.app.domain.model.isUrgent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface QuickAlert {
    data class CategoryLimit(val spending: CategorySpending) : QuickAlert
    data class BillDue(val bill: BillWithStatus) : QuickAlert
}

data class HomeUiState(
    val userName: String = "",
    val today: LocalDate = LocalDate.now(),
    val overview: MonthlyOverview? = null,
    val alerts: List<QuickAlert> = emptyList(),
    val alertCount: Int = 0,
    val isLoading: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FinanceRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<HomeUiState> = combine(
        repository.overview,
        repository.categorySpending,
        repository.bills,
        repository.today,
        combine(repository.isLoading, message, authRepository.session) { loading, msg, session ->
            Triple(loading, msg, session?.name.orEmpty())
        },
    ) { overview, spending, bills, today, (loading, msg, userName) ->
        val categoryAlerts = spending
            .filter { it.level != AlertLevel.NORMAL }
            .sortedByDescending { it.ratio }
            .map { QuickAlert.CategoryLimit(it) }
        val billAlerts = bills.filter { it.status.isUrgent }.map { QuickAlert.BillDue(it) }
        // Ưu tiên 1 cảnh báo danh mục + 1 hóa đơn, thiếu thì lấy thêm loại còn lại
        val shown = buildList {
            categoryAlerts.firstOrNull()?.let { add(it) }
            billAlerts.firstOrNull()?.let { add(it) }
            (categoryAlerts.drop(1) + billAlerts.drop(1)).forEach { if (size < 2) add(it) }
        }
        HomeUiState(
            userName = userName,
            today = today,
            overview = overview,
            alerts = shown,
            alertCount = categoryAlerts.size + billAlerts.size,
            isLoading = loading,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            repository.ensureLoaded().onFailure { message.value = it.toUserMessage() }
        }
    }

    fun payBill(billId: String) {
        viewModelScope.launch {
            repository.markBillPaid(billId)
                .onSuccess { message.value = "Đã đánh dấu thanh toán ✓" }
                .onFailure { message.value = it.toUserMessage() }
        }
    }

    fun setMonthlyBudget(amount: Long) {
        viewModelScope.launch {
            repository.updateTotalBudget(amount)
                .onSuccess { message.value = "Đã đặt ngân sách tháng ✓" }
                .onFailure { message.value = it.toUserMessage() }
        }
    }

    fun messageShown() {
        message.value = null
    }
}
