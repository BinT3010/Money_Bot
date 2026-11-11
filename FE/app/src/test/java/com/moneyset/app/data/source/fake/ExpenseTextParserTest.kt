package com.moneyset.app.data.source.fake

import com.moneyset.app.domain.model.Category
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseTextParserTest {

    private val parser = ExpenseTextParser()
    private val today = LocalDate.of(2026, 10, 16)

    @Test
    fun `parses amount with k, food category and note`() {
        val draft = parser.parse("ăn trưa cơm tấm 55k", today)
        assertEquals(55_000L, draft.amount)
        assertEquals(Category.FOOD, draft.category)
        assertEquals("Ăn trưa cơm tấm", draft.note)
        assertEquals(today, draft.date)
    }

    @Test
    fun `parses grouped thousands`() {
        val draft = parser.parse("đổ xăng 80.000", today)
        assertEquals(80_000L, draft.amount)
        assertEquals(Category.TRANSPORT, draft.category)
        assertEquals("Đổ xăng", draft.note)
    }

    @Test
    fun `parses millions with decimal`() {
        val draft = parser.parse("tiền điện 1,2tr", today)
        assertEquals(1_200_000L, draft.amount)
        assertEquals(Category.BILLS, draft.category)
    }

    @Test
    fun `bare small number means thousands and yesterday is understood`() {
        val draft = parser.parse("hôm qua cafe 45", today)
        assertEquals(45_000L, draft.amount)
        assertEquals(today.minusDays(1), draft.date)
        assertEquals("Cafe", draft.note)
    }

    @Test
    fun `explicit date is not taken as amount`() {
        val draft = parser.parse("mua áo 250k 12/10", today)
        assertEquals(250_000L, draft.amount)
        assertEquals(LocalDate.of(2026, 10, 12), draft.date)
        assertEquals(Category.SHOPPING, draft.category)
    }

    @Test
    fun `missing amount gives zero`() {
        val draft = parser.parse("đi xem phim", today)
        assertEquals(0L, draft.amount)
        assertEquals(Category.ENTERTAINMENT, draft.category)
    }
}
