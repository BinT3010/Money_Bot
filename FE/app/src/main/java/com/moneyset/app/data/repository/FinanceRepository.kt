package com.moneyset.app.data.repository

import com.moneyset.app.core.suspendRunCatching
import com.moneyset.app.data.source.FinanceDataSource
import com.moneyset.app.domain.model.Bill
import com.moneyset.app.domain.model.BillWithStatus
import com.moneyset.app.domain.model.Budget
import com.moneyset.app.domain.model.Category
import com.moneyset.app.domain.model.CategorySpending
import com.moneyset.app.domain.model.ExpenseDraft
import com.moneyset.app.domain.model.MonthlyOverview
import com.moneyset.app.domain.model.Transaction
import com.moneyset.app.domain.model.statusOn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Nguồn sự thật duy nhất cho UI: cache dữ liệu tháng hiện tại trong StateFlow,
 * các ViewModel chỉ cần collect Flow và gọi hàm suspend để thay đổi.
 */
@Singleton
class FinanceRepository @Inject constructor(
    private val dataSource: FinanceDataSource,
) {
    /** "Hôm nay" — có thể giả lập ở màn Hóa đơn để thử các trạng thái đến hạn. */
    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _budget = MutableStateFlow<Budget?>(null)
    private val _bills = MutableStateFlow<List<Bill>>(emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var loadedMonth: YearMonth? = null
    private val refreshMutex = Mutex()

    val currentMonth: YearMonth get() = YearMonth.from(_today.value)

    val overview: Flow<MonthlyOverview> = combine(_transactions, _budget, _today) { txs, budget, today ->
        val month = YearMonth.from(today)
        MonthlyOverview(
            month = month,
            budget = budget?.totalLimit ?: 0L,
            spent = txs.filter { YearMonth.from(it.date) == month }.sumOf { it.amount },
        )
    }

    val categorySpending: Flow<List<CategorySpending>> = combine(_transactions, _budget) { txs, budget ->
        Category.entries.map { category ->
            CategorySpending(
                category = category,
                spent = txs.filter { it.category == category }.sumOf { it.amount },
                limit = budget?.categoryLimits?.get(category) ?: 0L,
            )
        }
    }

    val bills: Flow<List<BillWithStatus>> = combine(_bills, _today) { bills, today ->
        bills.map { it.statusOn(today) }
            .sortedWith(compareBy({ it.status.priority }, { it.dueDate }))
    }

    suspend fun ensureLoaded(): Result<Unit> =
        if (loadedMonth == currentMonth) Result.success(Unit) else refresh()

    suspend fun refresh(): Result<Unit> = refreshMutex.withLock {
        val month = currentMonth
        _isLoading.value = true
        try {
            suspendRunCatching {
                coroutineScope {
                    val txs = async { dataSource.getTransactions(month) }
                    val budget = async { dataSource.getBudget(month) }
                    val bills = async { dataSource.getBills() }
                    _transactions.value = txs.await().sortedByDescending { it.date }
                    _budget.value = budget.await()
                    _bills.value = bills.await()
                }
                loadedMonth = month
            }
        } finally {
            _isLoading.value = false
        }
    }

    /** Xóa cache khi đăng xuất / đổi tài khoản; lần ensureLoaded() sau sẽ tải lại. */
    suspend fun clear() = refreshMutex.withLock {
        loadedMonth = null
        _transactions.value = emptyList()
        _budget.value = null
        _bills.value = emptyList()
        _today.value = LocalDate.now()
    }

    suspend fun setToday(date: LocalDate): Result<Unit> {
        val monthChanged = YearMonth.from(date) != currentMonth
        _today.value = date
        return if (monthChanged) refresh() else Result.success(Unit)
    }

    suspend fun addTransaction(draft: ExpenseDraft): Result<Transaction> = suspendRunCatching {
        val created = dataSource.createTransaction(
            Transaction(
                id = "",
                amount = draft.amount,
                note = draft.note.ifBlank { draft.category.label },
                category = draft.category,
                date = draft.date,
                receiptUri = draft.receiptUri,
            ),
        )
        if (YearMonth.from(created.date) == loadedMonth) {
            _transactions.update { (listOf(created) + it).sortedByDescending { t -> t.date } }
        }
        created
    }

    suspend fun updateTotalBudget(totalLimit: Long): Result<Unit> = suspendRunCatching {
        _budget.value = dataSource.updateTotalBudget(currentMonth, totalLimit)
    }

    suspend fun updateCategoryLimit(category: Category, limit: Long): Result<Unit> = suspendRunCatching {
        _budget.value = dataSource.updateCategoryLimit(currentMonth, category, limit)
    }

    suspend fun applyLimits(limits: Map<Category, Long>): Result<Unit> = suspendRunCatching {
        val month = currentMonth
        limits.forEach { (category, limit) ->
            _budget.value = dataSource.updateCategoryLimit(month, category, limit)
        }
    }

    suspend fun suggestLimits(): Result<Map<Category, Long>> = suspendRunCatching {
        dataSource.suggestLimits(currentMonth)
    }

    suspend fun parseExpense(text: String): Result<ExpenseDraft> = suspendRunCatching {
        dataSource.parseExpense(text, _today.value)
    }

    suspend fun scanReceipt(image: ByteArray, mimeType: String): Result<ExpenseDraft> = suspendRunCatching {
        dataSource.scanReceipt(image, mimeType, _today.value)
    }

    suspend fun addBill(bill: Bill): Result<Unit> = suspendRunCatching {
        val created = dataSource.createBill(bill)
        _bills.update { it + created }
    }

    suspend fun markBillPaid(billId: String): Result<Unit> = suspendRunCatching {
        val updated = dataSource.markBillPaid(billId, _today.value)
        _bills.update { list -> list.map { if (it.id == billId) updated else it } }
        // Thanh toán hóa đơn sinh ra một giao dịch chi -> tải lại danh sách giao dịch
        _transactions.value = dataSource.getTransactions(currentMonth).sortedByDescending { it.date }
    }
}
