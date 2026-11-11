package com.moneyset.app.data.source.remote

import com.moneyset.app.data.source.FinanceDataSource
import com.moneyset.app.data.source.remote.dto.LimitDto
import com.moneyset.app.data.source.remote.dto.ParseExpenseRequest
import com.moneyset.app.data.source.remote.dto.PayBillRequest
import com.moneyset.app.data.source.remote.dto.TotalBudgetDto
import com.moneyset.app.data.source.remote.dto.toDomain
import com.moneyset.app.data.source.remote.dto.toDto
import com.moneyset.app.domain.model.Bill
import com.moneyset.app.domain.model.Budget
import com.moneyset.app.domain.model.Category
import com.moneyset.app.domain.model.ExpenseDraft
import com.moneyset.app.domain.model.Transaction
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class RemoteFinanceDataSource @Inject constructor(
    private val api: FinanceApi,
) : FinanceDataSource {

    override suspend fun getTransactions(month: YearMonth): List<Transaction> =
        api.getTransactions(month.toString()).map { it.toDomain() }

    override suspend fun createTransaction(transaction: Transaction): Transaction =
        api.createTransaction(transaction.toDto()).toDomain()

    override suspend fun getBudget(month: YearMonth): Budget =
        api.getBudget(month.toString()).toDomain(month)

    override suspend fun updateTotalBudget(month: YearMonth, totalLimit: Long): Budget =
        api.updateTotalBudget(month.toString(), TotalBudgetDto(totalLimit)).toDomain(month)

    override suspend fun updateCategoryLimit(month: YearMonth, category: Category, limit: Long): Budget =
        api.updateCategoryLimit(month.toString(), category.apiKey, LimitDto(limit)).toDomain(month)

    override suspend fun suggestLimits(month: YearMonth): Map<Category, Long> =
        api.suggestLimits(month.toString()).associate { Category.fromApi(it.category) to (it.limit ?: 0L) }

    override suspend fun getBills(): List<Bill> = api.getBills().map { it.toDomain() }

    override suspend fun createBill(bill: Bill): Bill = api.createBill(bill.toDto()).toDomain()

    override suspend fun markBillPaid(billId: String, paidOn: LocalDate): Bill =
        api.markBillPaid(billId, PayBillRequest(YearMonth.from(paidOn).toString(), paidOn.toString())).toDomain()

    override suspend fun parseExpense(text: String, today: LocalDate): ExpenseDraft =
        api.parseExpense(ParseExpenseRequest(text, today.toString())).toDomain(today)

    override suspend fun scanReceipt(image: ByteArray, mimeType: String, today: LocalDate): ExpenseDraft {
        val body = image.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("image", "receipt.jpg", body)
        val todayPart = today.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        return api.scanReceipt(part, todayPart).toDomain(today)
    }
}
