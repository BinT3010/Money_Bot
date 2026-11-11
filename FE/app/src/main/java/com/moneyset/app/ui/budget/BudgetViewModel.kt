package com.moneyset.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyset.app.core.toUserMessage
import com.moneyset.app.data.repository.FinanceRepository
import com.moneyset.app.domain.model.Category
import com.moneyset.app.domain.model.CategorySpending
import com.moneyset.app.domain.model.MonthlyOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BudgetUiState(
    val month: YearMonth = YearMonth.now(),
    val overview: MonthlyOverview? = null,
    val spending: List<CategorySpending> = emptyList(),
    val isSuggesting: Boolean = false,
    /** != null -> đang hiện hộp thoại đề xuất */
    val suggestions: Map<Category, Long>? = null,
    val message: String? = null,
) {
    /** Hiện đủ mọi danh mục để người dùng đặt hạn mức; mục nguy cơ vượt lên đầu. */
    val visibleSpending: List<CategorySpending>
        get() = spending.sortedByDescending { it.ratio }
    val totalSpent: Long get() = spending.sumOf { it.spent }
}

private data class BudgetLocal(
    val isSuggesting: Boolean = false,
    val suggestions: Map<Category, Long>? = null,
    val message: String? = null,
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val repository: FinanceRepository,
) : ViewModel() {

    private val local = MutableStateFlow(BudgetLocal())

    val state: StateFlow<BudgetUiState> =
        combine(repository.overview, repository.categorySpending, local) { overview, spending, l ->
            BudgetUiState(overview.month, overview, spending, l.isSuggesting, l.suggestions, l.message)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    init {
        viewModelScope.launch {
            repository.ensureLoaded().onFailure { e -> local.update { it.copy(message = e.toUserMessage()) } }
        }
    }

    fun updateTotalBudget(amount: Long) {
        viewModelScope.launch {
            repository.updateTotalBudget(amount)
                .onSuccess { local.update { it.copy(message = "Đã cập nhật ngân sách tháng") } }
                .onFailure { e -> local.update { it.copy(message = e.toUserMessage()) } }
        }
    }

    fun updateLimit(category: Category, limit: Long) {
        viewModelScope.launch {
            repository.updateCategoryLimit(category, limit)
                .onSuccess { local.update { it.copy(message = "Đã cập nhật hạn mức ${category.shortLabel}") } }
                .onFailure { e -> local.update { it.copy(message = e.toUserMessage()) } }
        }
    }

    fun requestSuggestions() {
        if (local.value.isSuggesting) return
        viewModelScope.launch {
            local.update { it.copy(isSuggesting = true) }
            repository.suggestLimits()
                .onSuccess { s ->
                    local.update {
                        if (s.isEmpty()) {
                            it.copy(isSuggesting = false, message = "Chưa đủ dữ liệu chi tiêu để đề xuất. Hãy ghi vài khoản chi trước nhé!")
                        } else {
                            it.copy(isSuggesting = false, suggestions = s)
                        }
                    }
                }
                .onFailure { e -> local.update { it.copy(isSuggesting = false, message = e.toUserMessage()) } }
        }
    }

    fun applySuggestions() {
        val suggestions = local.value.suggestions ?: return
        local.update { it.copy(suggestions = null) }
        viewModelScope.launch {
            repository.applyLimits(suggestions)
                .onSuccess { local.update { it.copy(message = "Đã áp dụng hạn mức đề xuất ✨") } }
                .onFailure { e -> local.update { it.copy(message = e.toUserMessage()) } }
        }
    }

    fun dismissSuggestions() = local.update { it.copy(suggestions = null) }

    fun messageShown() = local.update { it.copy(message = null) }
}
