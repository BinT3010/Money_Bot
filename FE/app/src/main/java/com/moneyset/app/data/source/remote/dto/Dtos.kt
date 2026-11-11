package com.moneyset.app.data.source.remote.dto

import com.moneyset.app.domain.model.Bill
import com.moneyset.app.domain.model.BillType
import com.moneyset.app.domain.model.Budget
import com.moneyset.app.domain.model.Category
import com.moneyset.app.domain.model.ExpenseDraft
import com.moneyset.app.domain.model.Transaction
import java.time.LocalDate
import java.time.YearMonth

// Mọi field nullable để Gson không tạo object "non-null" chứa null khi backend thiếu field.

data class TransactionDto(
    val id: String? = null,
    val amount: Long? = null,
    val note: String? = null,
    val category: String? = null,
    val date: String? = null,
    val receiptUrl: String? = null,
)

data class CategoryLimitDto(
    val category: String? = null,
    val limit: Long? = null,
)

data class BudgetDto(
    val month: String? = null,
    val totalLimit: Long? = null,
    val categories: List<CategoryLimitDto>? = null,
)

data class LimitDto(val limit: Long)

data class TotalBudgetDto(val totalLimit: Long)

data class BillDto(
    val id: String? = null,
    val name: String? = null,
    val amount: Long? = null,
    val dueDay: Int? = null,
    val type: String? = null,
    val paidMonths: List<String>? = null,
)

data class PayBillRequest(val month: String, val paidOn: String)

data class ParseExpenseRequest(val text: String, val today: String)

data class ExpenseDraftDto(
    val amount: Long? = null,
    val note: String? = null,
    val category: String? = null,
    val date: String? = null,
    val confidence: Float? = null,
)

// ---- Mappers ----

private fun String?.toLocalDateOr(default: LocalDate): LocalDate =
    this?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: default

fun TransactionDto.toDomain() = Transaction(
    id = id.orEmpty(),
    amount = amount ?: 0L,
    note = note.orEmpty(),
    category = Category.fromApi(category),
    date = date.toLocalDateOr(LocalDate.now()),
    receiptUri = receiptUrl,
)

fun Transaction.toDto() = TransactionDto(
    id = id.ifBlank { null },
    amount = amount,
    note = note,
    category = category.apiKey,
    date = date.toString(),
    receiptUrl = receiptUri,
)

fun BudgetDto.toDomain(fallbackMonth: YearMonth) = Budget(
    month = month?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: fallbackMonth,
    totalLimit = totalLimit ?: 0L,
    categoryLimits = categories.orEmpty().associate { Category.fromApi(it.category) to (it.limit ?: 0L) },
)

fun BillDto.toDomain() = Bill(
    id = id.orEmpty(),
    name = name.orEmpty(),
    amount = amount ?: 0L,
    dueDay = dueDay ?: 1,
    type = BillType.fromApi(type),
    paidMonths = paidMonths.orEmpty().mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }.toSet(),
)

fun Bill.toDto() = BillDto(
    id = id.ifBlank { null },
    name = name,
    amount = amount,
    dueDay = dueDay,
    type = type.apiKey,
    paidMonths = paidMonths.map { it.toString() },
)

fun ExpenseDraftDto.toDomain(today: LocalDate) = ExpenseDraft(
    amount = amount ?: 0L,
    note = note.orEmpty(),
    category = Category.fromApi(category),
    date = date.toLocalDateOr(today),
    confidence = confidence ?: 0f,
)
