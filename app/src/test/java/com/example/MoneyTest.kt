package com.example

import com.example.core.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun testMillimesStorageAndFormatting() {
        val oneAndHalf = Money(1500L)
        assertEquals("1.500 د.ت", oneAndHalf.formatArabic())
        assertEquals("1.500 TND", oneAndHalf.formatStandard())

        val smallAmount = Money(800L)
        assertEquals("0.800 د.ت", smallAmount.formatArabic())

        val largeAmount = Money(25750L)
        assertEquals("25.750 د.ت", largeAmount.formatArabic())
    }

    @Test
    fun testArithmeticOperations() {
        val price = Money(2200L) // 2.200 TND (Express coffee + cake)
        val tip = Money(300L)     // 0.300 TND
        val total = price + tip
        assertEquals(2500L, total.millimes)
        assertEquals("2.500 د.ت", total.formatArabic())

        val change = Money(5000L) - total
        assertEquals(2500L, change.millimes)

        val doubled = price * 2
        assertEquals(4400L, doubled.millimes)

        val split = total / 2
        assertEquals(1250L, split.millimes)
    }

    @Test
    fun testPercentageDiscount() {
        val orderTotal = Money(20000L) // 20.000 TND
        val discount = orderTotal.percentage(10) // 10%
        assertEquals(2000L, discount.millimes)
        val discountedTotal = orderTotal - discount
        assertEquals(18000L, discountedTotal.millimes)
    }

    @Test
    fun testParsingFromString() {
        val parsed1 = Money.fromDinarsString("1.500")
        assertEquals(1500L, parsed1.millimes)

        val parsed2 = Money.fromDinarsString("2.5 د.ت")
        assertEquals(2500L, parsed2.millimes)

        val parsed3 = Money.fromDinarsString("10")
        assertEquals(10000L, parsed3.millimes)
    }

    @Test
    fun testComparison() {
        val a = Money(1500L)
        val b = Money(2000L)
        assertTrue(a < b)
        assertTrue(b > a)
    }
}
