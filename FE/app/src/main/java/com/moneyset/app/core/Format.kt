package com.moneyset.app.core

import java.io.IOException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import retrofit2.HttpException

val VietnamLocale: Locale = Locale.forLanguageTag("vi-VN")

private fun moneyFormat() = DecimalFormat(
    "#,##0",
    DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    },
)

/** 7000000 -> "7.000.000" */
fun Long.formatMoney(): String = moneyFormat().format(this)

/** 7000000 -> "7.000.000 đ" */
fun Long.toVnd(): String = "${formatMoney()} đ"

/** 0.726f -> "72,6%", 0.84f -> "84%" */
fun Float.toPercentText(): String {
    val rounded = Math.round(this * 1000f) / 10f
    return if (rounded % 1f == 0f) "${rounded.toInt()}%" else String.format(VietnamLocale, "%.1f%%", rounded)
}

private val dayMonthFormatter = DateTimeFormatter.ofPattern("dd/MM")
private val fullDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun LocalDate.dayMonth(): String = format(dayMonthFormatter)
fun LocalDate.fullDate(): String = format(fullDateFormatter)
fun YearMonth.label(): String = "Tháng $monthValue/$year"

fun LocalDate.weekdayVi(): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "Thứ Hai"
    DayOfWeek.TUESDAY -> "Thứ Ba"
    DayOfWeek.WEDNESDAY -> "Thứ Tư"
    DayOfWeek.THURSDAY -> "Thứ Năm"
    DayOfWeek.FRIDAY -> "Thứ Sáu"
    DayOfWeek.SATURDAY -> "Thứ Bảy"
    DayOfWeek.SUNDAY -> "Chủ Nhật"
    null -> ""
}

/**
 * Bỏ dấu tiếng Việt + chữ thường, GIỮ NGUYÊN độ dài chuỗi (mỗi ký tự -> đúng 1 ký tự)
 * để chỉ số tìm được trên chuỗi đã bỏ dấu dùng lại được trên chuỗi gốc.
 */
fun String.foldVietnamese(): String = buildString(length) {
    for (c in this@foldVietnamese) {
        val base = when (c) {
            'đ' -> 'd'
            'Đ' -> 'd'
            else -> Normalizer.normalize(c.toString(), Normalizer.Form.NFD)[0]
        }
        append(base.lowercaseChar())
    }
}

/** Giống runCatching nhưng không nuốt CancellationException của coroutine. */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> "Máy chủ báo lỗi (${code()})"
    is IOException -> "Không kết nối được máy chủ"
    else -> message ?: "Đã có lỗi xảy ra"
}
