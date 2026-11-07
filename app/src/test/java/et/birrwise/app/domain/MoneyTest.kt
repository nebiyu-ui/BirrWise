package et.birrwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun additionIsExactWhereFloatingPointIsNot() {
        // 0.10 + 0.20 is 0.30000000000000004 as a Double. As minor units it is exactly 0.30.
        assertEquals(Money(30L, ETB), Money(10L, ETB) + Money(20L, ETB))
    }

    @Test
    fun subtractionAndNegationWork() {
        assertEquals(Money(-50L, ETB), Money(100L, ETB) - Money(150L, ETB))
        assertEquals(Money(-100L, ETB), -Money(100L, ETB))
    }

    @Test
    fun mixingCurrenciesIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(1L, ETB) + Money(1L, "USD")
        }
    }

    @Test
    fun unsupportedCurrencyIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Money(1L, "ABCD") }
    }

    @Test
    fun overflowFailsInsteadOfWrapping() {
        assertThrows(ArithmeticException::class.java) {
            Money(Long.MAX_VALUE, ETB) + Money(1L, ETB)
        }
        assertThrows(ArithmeticException::class.java) {
            -Money(Long.MIN_VALUE, ETB)
        }
    }

    @Test
    fun largeAmountsAddUpExactly() {
        // One thousand amounts of 10 trillion birr each.
        val total = (1..1000).fold(Money.zero(ETB)) { sum, _ -> sum + Money(1_000_000_000_000_000L, ETB) }
        assertEquals(Money(1_000_000_000_000_000_000L, ETB), total)
    }

    @Test
    fun signHelpersReportCorrectly() {
        assertTrue(Money(0L, ETB).isZero)
        assertTrue(Money(-1L, ETB).isNegative)
        assertTrue(Money(1L, ETB).isPositive)
    }
}

class MoneyFormatterTest {

    @Test
    fun formatsWithGroupingAndTwoDecimals() {
        assertEquals("1,234.56 ETB", MoneyFormatter.format(Money(123_456L, ETB)))
        assertEquals("1,000,000,000.00 ETB", MoneyFormatter.format(Money(100_000_000_000L, ETB)))
    }

    @Test
    fun formatsSmallAndZeroAmounts() {
        assertEquals("0.05 ETB", MoneyFormatter.format(Money(5L, ETB)))
        assertEquals("0.00 ETB", MoneyFormatter.format(Money(0L, ETB)))
        assertEquals("12.00 ETB", MoneyFormatter.format(Money(1_200L, ETB)))
    }

    @Test
    fun formatsNegativeAmounts() {
        assertEquals("-1,234.56 ETB", MoneyFormatter.format(Money(-123_456L, ETB)))
    }

    @Test
    fun canLeaveOutTheCurrency() {
        assertEquals("1,234.56", MoneyFormatter.format(Money(123_456L, ETB), includeCurrency = false))
    }

    @Test
    fun respectsCurrenciesWithoutDecimals() {
        assertEquals("500 JPY", MoneyFormatter.format(Money(500L, "JPY")))
        assertEquals("1,500 JPY", MoneyFormatter.format(Money(1_500L, "JPY")))
    }

    @Test
    fun formatsTheLargestPossibleAmount() {
        assertEquals(
            "92,233,720,368,547,758.07 ETB",
            MoneyFormatter.format(Money(Long.MAX_VALUE, ETB))
        )
    }
}

class MoneyParserTest {

    @Test
    fun parsesPlainAndGroupedAmounts() {
        assertEquals(Money(125_050L, ETB), MoneyParser.parse("1250.50", ETB))
        assertEquals(Money(125_050L, ETB), MoneyParser.parse("1,250.50", ETB))
        assertEquals(Money(125_000L, ETB), MoneyParser.parse("1,250", ETB))
        assertEquals(Money(1_500L, ETB), MoneyParser.parse(" 15 ", ETB))
        assertEquals(Money(1_250L, ETB), MoneyParser.parse("12.5", ETB))
        assertEquals(Money(5L, ETB), MoneyParser.parse("0.05", ETB))
    }

    @Test
    fun rejectsAmbiguousCommas() {
        // "12,50" could mean 12.50 or 1,250. It is rejected so the person must retype it.
        assertNull(MoneyParser.parse("12,50", ETB))
        assertNull(MoneyParser.parse("1,2500", ETB))
    }

    @Test
    fun rejectsInvalidText() {
        assertNull(MoneyParser.parse("", ETB))
        assertNull(MoneyParser.parse("abc", ETB))
        assertNull(MoneyParser.parse("-5", ETB))
        assertNull(MoneyParser.parse("1e3", ETB))
        assertNull(MoneyParser.parse("1.2.3", ETB))
        assertNull(MoneyParser.parse("5.", ETB))
        assertNull(MoneyParser.parse(".5", ETB))
    }

    @Test
    fun rejectsAmountsThatNeedRounding() {
        assertNull(MoneyParser.parse("1.234", ETB))
        assertNull(MoneyParser.parse("500.5", "JPY"))
        assertEquals(Money(500L, "JPY"), MoneyParser.parse("500", "JPY"))
    }

    @Test
    fun rejectsAmountsTooLargeToStore() {
        assertNull(MoneyParser.parse("99999999999999999999", ETB))
        assertEquals(
            Money(Long.MAX_VALUE, ETB),
            MoneyParser.parse("92,233,720,368,547,758.07", ETB)
        )
    }
}
