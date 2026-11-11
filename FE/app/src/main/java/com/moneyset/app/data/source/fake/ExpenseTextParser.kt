package com.moneyset.app.data.source.fake

import com.moneyset.app.core.VietnamLocale
import com.moneyset.app.core.foldVietnamese
import com.moneyset.app.domain.model.Category
import com.moneyset.app.domain.model.ExpenseDraft
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * Bộ bóc tách câu chi tiêu tiếng Việt đơn giản chạy offline (dùng cho chế độ mock).
 * Ví dụ: "ăn trưa cơm tấm 55k" -> 55.000 đ, Ăn uống, "Ăn trưa cơm tấm".
 */
class ExpenseTextParser {

    private data class AmountMatch(val value: Long, val range: IntRange, val hasUnit: Boolean)

    fun parse(text: String, today: LocalDate): ExpenseDraft {
        val original = text.trim()
        val folded = original.foldVietnamese()
        val removed = mutableListOf<IntRange>()

        // 1. Ngày
        var date = today
        val explicit = DATE_REGEX.find(folded)
        if (explicit != null) {
            val day = explicit.groupValues[1].toInt()
            val month = explicit.groupValues[2].toInt()
            date = runCatching { LocalDate.of(today.year, month, day) }.getOrDefault(today)
            removed += explicit.range
        } else {
            RELATIVE_DATES.firstNotNullOfOrNull { (regex, offset) -> regex.find(folded)?.let { it to offset } }
                ?.let { (match, offset) ->
                    date = today.minusDays(offset)
                    removed += match.range
                }
        }

        // 2. Số tiền (bỏ qua phần đã nhận là ngày)
        val masked = mask(folded, removed)
        val candidates = AMOUNT_REGEX.findAll(masked).mapNotNull { m ->
            toAmount(m.groupValues[1], m.groupValues[2])?.let { AmountMatch(it, m.range, m.groupValues[2].isNotEmpty()) }
        }.toList()
        val amount = candidates.filter { it.hasUnit }.maxByOrNull { it.value } ?: candidates.maxByOrNull { it.value }
        amount?.let { removed += it.range }

        // 3. Danh mục theo từ khóa
        val category = KEYWORDS
            .map { (cat, regexes) -> cat to regexes.count { it.containsMatchIn(folded) } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first ?: Category.OTHER

        // 4. Ghi chú = phần còn lại
        val note = buildNote(original, removed).ifBlank { category.label }

        val confidence = when {
            amount != null && category != Category.OTHER -> 0.93f
            amount != null -> 0.75f
            else -> 0.35f
        }
        return ExpenseDraft(amount?.value ?: 0L, note, category, date, confidence)
    }

    private fun toAmount(number: String, unit: String): Long? {
        val value = if (THOUSANDS_GROUPED.matches(number)) {
            number.replace(".", "").replace(",", "").toDoubleOrNull()
        } else {
            number.replace(',', '.').toDoubleOrNull()
        } ?: return null
        val multiplier = when (unit) {
            "k", "nghin", "ngan" -> 1_000.0
            "tr", "trieu", "cu" -> 1_000_000.0
            "vnd", "dong", "d" -> 1.0
            else -> if (value < 1_000) 1_000.0 else 1.0
        }
        return (value * multiplier).roundToLong().takeIf { it > 0 }
    }

    private fun mask(text: String, ranges: List<IntRange>): String =
        String(CharArray(text.length) { i -> if (ranges.any { i in it }) ' ' else text[i] })

    private fun buildNote(original: String, ranges: List<IntRange>): String =
        mask(original, ranges)
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '-', '.', ':', ';')
            .replaceFirstChar { it.titlecase(VietnamLocale) }

    companion object {
        private val DATE_REGEX = Regex("""(?<!\d)(\d{1,2})/(\d{1,2})(?!\d)""")
        private val RELATIVE_DATES = listOf(
            Regex("""(?<![a-z])hom qua(?![a-z])""") to 1L,
            Regex("""(?<![a-z])hom kia(?![a-z])""") to 2L,
        )
        private val AMOUNT_REGEX =
            Regex("""(?<![\d.,])(\d{1,3}(?:[.,]\d{3})+|\d+(?:[.,]\d+)?)\s*(k|nghin|ngan|trieu|tr|cu|vnd|dong|d)?(?![a-z\d])""")
        private val THOUSANDS_GROUPED = Regex("""\d{1,3}(?:[.,]\d{3})+""")

        private fun words(vararg w: String) = w.map { Regex("(?<![a-z])${Regex.escape(it)}(?![a-z])") }

        // Thứ tự = độ ưu tiên khi hòa điểm
        private val KEYWORDS: List<Pair<Category, List<Regex>>> = listOf(
            Category.BILLS to words(
                "tien dien", "dien", "tien nuoc", "hoa don nuoc", "internet", "wifi", "mang", "gas",
                "dien thoai", "phong tro", "tien nha", "tien phong", "truyen hinh",
            ),
            Category.TRANSPORT to words(
                "xang", "do xang", "grab", "be", "taxi", "xe om", "gui xe", "bus", "xe buyt", "ve tau", "may bay", "rua xe",
            ),
            Category.FOOD to words(
                "an", "com", "pho", "bun", "mi", "banh", "cafe", "ca phe", "tra sua", "lau", "nuong", "bbq",
                "di cho", "sieu thi", "do an", "nha hang", "trua", "toi", "sang", "an vat", "nuoc mia", "sinh to",
            ),
            Category.ENTERTAINMENT to words(
                "phim", "game", "karaoke", "du lich", "concert", "netflix", "spotify", "xem", "choi",
            ),
            Category.SHOPPING to words(
                "mua", "ao", "quan", "giay", "dep", "shopee", "lazada", "tiki", "my pham", "do dung", "tui",
            ),
        )
    }
}
