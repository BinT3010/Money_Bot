package com.moneyset.app.data.source.fake

import com.moneyset.app.data.auth.SessionStore
import com.moneyset.app.data.source.FinanceDataSource
import com.moneyset.app.domain.model.Bill
import com.moneyset.app.domain.model.Budget
import com.moneyset.app.domain.model.Category
import com.moneyset.app.domain.model.ExpenseDraft
import com.moneyset.app.domain.model.Transaction
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Backend giả chạy trong máy, dùng tạm cho tới khi có backend thật.
 * - Mỗi tài khoản có dữ liệu riêng và BẮT ĐẦU TRỐNG (không ngân sách, không giao dịch, không hóa đơn).
 * - Dữ liệu chỉ nằm trong bộ nhớ: tắt hẳn app là mất.
 */
@Singleton
class FakeFinanceDataSource @Inject constructor(
    private val sessionStore: SessionStore,
) : FinanceDataSource {

    private class UserData {
        val transactions = mutableListOf<Transaction>()
        val budgets = mutableMapOf<YearMonth, Budget>()
        val bills = mutableListOf<Bill>()
    }

    private val mutex = Mutex()
    private val parser = ExpenseTextParser()
    private val users = mutableMapOf<String, UserData>()
    private var idCounter = 0

    private fun nextId(prefix: String) = "$prefix-${++idCounter}"

    /** Dữ liệu của tài khoản đang đăng nhập (giống backend xác định user qua token). */
    private suspend fun <T> withUser(block: (UserData) -> T): T {
        val email = sessionStore.session.first()?.email ?: error("Bạn chưa đăng nhập")
        return mutex.withLock { block(users.getOrPut(email.lowercase()) { UserData() }) }
    }

    private fun UserData.budgetFor(month: YearMonth): Budget =
        budgets.getOrPut(month) { Budget(month, totalLimit = 0L, categoryLimits = emptyMap()) }

    override suspend fun getTransactions(month: YearMonth): List<Transaction> {
        delay(200)
        return withUser { u -> u.transactions.filter { YearMonth.from(it.date) == month } }
    }

    override suspend fun createTransaction(transaction: Transaction): Transaction {
        delay(300)
        return withUser { u -> transaction.copy(id = nextId("tx")).also { u.transactions += it } }
    }

    override suspend fun getBudget(month: YearMonth): Budget {
        delay(150)
        return withUser { it.budgetFor(month) }
    }

    override suspend fun updateTotalBudget(month: YearMonth, totalLimit: Long): Budget {
        delay(200)
        return withUser { u -> u.budgetFor(month).copy(totalLimit = totalLimit).also { u.budgets[month] = it } }
    }

    override suspend fun updateCategoryLimit(month: YearMonth, category: Category, limit: Long): Budget {
        delay(200)
        return withUser { u ->
            val current = u.budgetFor(month)
            current.copy(categoryLimits = current.categoryLimits + (category to limit)).also { u.budgets[month] = it }
        }
    }

    /** Ước tính chi cả tháng theo tốc độ chi hiện tại (+5%), chỉ cho danh mục đã có chi tiêu. */
    override suspend fun suggestLimits(month: YearMonth): Map<Category, Long> {
        delay(900)
        return withUser { u ->
            val today = LocalDate.now()
            val elapsedDays = if (YearMonth.from(today) == month) today.dayOfMonth else month.lengthOfMonth()
            u.transactions
                .filter { YearMonth.from(it.date) == month }
                .groupBy { it.category }
                .mapValues { (_, txs) ->
                    val projected = txs.sumOf { it.amount }.toDouble() / elapsedDays * month.lengthOfMonth() * 1.05
                    roundUpTo50k(maxOf(projected.toLong(), 50_000L))
                }
        }
    }

    private fun roundUpTo50k(value: Long) = (value + 49_999) / 50_000 * 50_000

    override suspend fun getBills(): List<Bill> {
        delay(150)
        return withUser { it.bills.toList() }
    }

    override suspend fun createBill(bill: Bill): Bill {
        delay(300)
        return withUser { u -> bill.copy(id = nextId("bill")).also { u.bills += it } }
    }

    override suspend fun markBillPaid(billId: String, paidOn: LocalDate): Bill {
        delay(300)
        return withUser { u ->
            val index = u.bills.indexOfFirst { it.id == billId }
            require(index >= 0) { "Không tìm thấy hóa đơn" }
            val month = YearMonth.from(paidOn)
            val bill = u.bills[index]
            if (month in bill.paidMonths) return@withUser bill
            val updated = bill.copy(paidMonths = bill.paidMonths + month)
            u.bills[index] = updated
            u.transactions += Transaction(nextId("tx"), bill.amount, "Thanh toán ${bill.name}", Category.BILLS, paidOn)
            updated
        }
    }

    override suspend fun parseExpense(text: String, today: LocalDate): ExpenseDraft {
        delay(500)
        return parser.parse(text, today)
    }

    /**
     * Chưa có AI đọc ảnh thật: trả về bản nháp trống để người dùng tự nhập số tiền.
     * Khi có backend, endpoint ai/scan-receipt sẽ trả về kết quả thật.
     */
    override suspend fun scanReceipt(image: ByteArray, mimeType: String, today: LocalDate): ExpenseDraft {
        delay(800)
        return ExpenseDraft(amount = 0, note = "Hóa đơn", category = Category.OTHER, date = today, confidence = 0f)
    }
}
