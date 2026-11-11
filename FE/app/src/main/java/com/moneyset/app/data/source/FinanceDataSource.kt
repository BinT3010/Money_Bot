package com.moneyset.app.data.source

import com.moneyset.app.domain.model.Bill
import com.moneyset.app.domain.model.Budget
import com.moneyset.app.domain.model.Category
import com.moneyset.app.domain.model.ExpenseDraft
import com.moneyset.app.domain.model.Transaction
import java.time.LocalDate
import java.time.YearMonth

/**
 * Nguồn dữ liệu. Có 2 bản cài đặt:
 *  - [com.moneyset.app.data.source.remote.RemoteFinanceDataSource]: gọi backend qua Retrofit
 *  - [com.moneyset.app.data.source.fake.FakeFinanceDataSource]: dữ liệu giả trong bộ nhớ
 * Chọn bằng BuildConfig.USE_MOCK_API (xem DataModule).
 */
interface FinanceDataSource {
    suspend fun getTransactions(month: YearMonth): List<Transaction>
    suspend fun createTransaction(transaction: Transaction): Transaction

    suspend fun getBudget(month: YearMonth): Budget
    suspend fun updateTotalBudget(month: YearMonth, totalLimit: Long): Budget
    suspend fun updateCategoryLimit(month: YearMonth, category: Category, limit: Long): Budget
    suspend fun suggestLimits(month: YearMonth): Map<Category, Long>

    suspend fun getBills(): List<Bill>
    suspend fun createBill(bill: Bill): Bill
    /** Đánh dấu đã trả cho tháng của [paidOn]; backend nên tự tạo giao dịch chi tương ứng. */
    suspend fun markBillPaid(billId: String, paidOn: LocalDate): Bill

    suspend fun parseExpense(text: String, today: LocalDate): ExpenseDraft
    suspend fun scanReceipt(image: ByteArray, mimeType: String, today: LocalDate): ExpenseDraft
}
