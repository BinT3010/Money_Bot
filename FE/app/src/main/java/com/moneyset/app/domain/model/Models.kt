package com.moneyset.app.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class Category(val apiKey: String, val label: String, val shortLabel: String) {
    FOOD("food", "Ăn uống", "Ăn uống"),
    SHOPPING("shopping", "Tiêu dùng/Mua sắm", "Tiêu dùng"),
    BILLS("bills", "Hóa đơn tiện ích", "Tiện ích"),
    TRANSPORT("transport", "Đi lại", "Đi lại"),
    ENTERTAINMENT("entertainment", "Giải trí", "Giải trí"),
    OTHER("other", "Khác", "Khác");

    companion object {
        fun fromApi(key: String?): Category =
            entries.firstOrNull { it.apiKey.equals(key, ignoreCase = true) } ?: OTHER
    }
}

data class Transaction(
    val id: String,
    val amount: Long,
    val note: String,
    val category: Category,
    val date: LocalDate,
    val receiptUri: String? = null,
)

data class Budget(
    val month: YearMonth,
    val totalLimit: Long,
    val categoryLimits: Map<Category, Long>,
)

data class MonthlyOverview(
    val month: YearMonth,
    val budget: Long,
    val spent: Long,
) {
    val remaining: Long get() = budget - spent
    val usedRatio: Float get() = if (budget <= 0) 0f else spent.toFloat() / budget
}

enum class AlertLevel { NORMAL, WARNING, OVER }

data class CategorySpending(
    val category: Category,
    val spent: Long,
    val limit: Long,
) {
    val ratio: Float get() = if (limit <= 0) 0f else spent.toFloat() / limit
    val remaining: Long get() = limit - spent
    val level: AlertLevel
        get() = when {
            limit <= 0 -> AlertLevel.NORMAL
            ratio >= 1f -> AlertLevel.OVER
            ratio >= WARNING_RATIO -> AlertLevel.WARNING
            else -> AlertLevel.NORMAL
        }

    companion object {
        const val WARNING_RATIO = 0.8f
    }
}

enum class BillType(val apiKey: String, val label: String) {
    ELECTRICITY("electricity", "Điện"),
    WATER("water", "Nước"),
    INTERNET("internet", "Internet"),
    RENT("rent", "Nhà/Phòng trọ"),
    PHONE("phone", "Điện thoại"),
    OTHER("other", "Khác");

    companion object {
        fun fromApi(key: String?): BillType =
            entries.firstOrNull { it.apiKey.equals(key, ignoreCase = true) } ?: OTHER
    }
}

data class Bill(
    val id: String,
    val name: String,
    val amount: Long,
    /** Ngày đến hạn hằng tháng (1..31). Tháng ngắn hơn sẽ lấy ngày cuối tháng. */
    val dueDay: Int,
    val type: BillType,
    val paidMonths: Set<YearMonth> = emptySet(),
)

sealed interface BillStatus {
    val priority: Int

    data class Overdue(val days: Int) : BillStatus {
        override val priority = 0
    }

    data object DueToday : BillStatus {
        override val priority = 1
    }

    data class DueSoon(val days: Int) : BillStatus {
        override val priority = 2
    }

    data class Upcoming(val days: Int) : BillStatus {
        override val priority = 3
    }

    data object Paid : BillStatus {
        override val priority = 4
    }
}

val BillStatus.isUrgent: Boolean
    get() = this is BillStatus.Overdue || this is BillStatus.DueToday || this is BillStatus.DueSoon

data class BillWithStatus(val bill: Bill, val dueDate: LocalDate, val status: BillStatus)

private const val DUE_SOON_DAYS = 3

fun Bill.statusOn(today: LocalDate): BillWithStatus {
    val month = YearMonth.from(today)
    val due = month.atDay(dueDay.coerceIn(1, month.lengthOfMonth()))
    val status = if (month in paidMonths) {
        BillStatus.Paid
    } else {
        val days = ChronoUnit.DAYS.between(today, due).toInt()
        when {
            days < 0 -> BillStatus.Overdue(-days)
            days == 0 -> BillStatus.DueToday
            days <= DUE_SOON_DAYS -> BillStatus.DueSoon(days)
            else -> BillStatus.Upcoming(days)
        }
    }
    return BillWithStatus(this, due, status)
}

/** Kết quả AI bóc tách từ câu chat / ảnh hóa đơn, người dùng xác nhận trước khi lưu. */
data class ExpenseDraft(
    val amount: Long,
    val note: String,
    val category: Category,
    val date: LocalDate,
    /** 0..1 */
    val confidence: Float,
    val receiptUri: String? = null,
)
