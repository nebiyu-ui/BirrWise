package et.birrwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetMathTest {

    @Test
    fun matchesTheWorkedExample() {
        // Limit 5,000 ETB, spent 4,000 ETB: 80 percent used, 1,000 ETB remaining.
        val result = BudgetMath.progress(etb(5000), etb(4000))
        assertEquals(80, result.percentUsed)
        assertEquals(etb(1000), result.remaining)
        assertEquals(etb(0), result.overspent)
        assertFalse(result.isOverBudget)
        assertTrue(result.thresholdReached)
    }

    @Test
    fun percentIsRoundedDownAndThresholdIsExact() {
        // 3,999.99 of 5,000 is 79.9998 percent: shown as 79 and below an 80 percent threshold.
        val result = BudgetMath.progress(etb(5000), etb(3999, 99))
        assertEquals(79, result.percentUsed)
        assertFalse(result.thresholdReached)
    }

    @Test
    fun spendingAtTheLimitIsNotOverBudget() {
        val result = BudgetMath.progress(etb(5000), etb(5000))
        assertEquals(100, result.percentUsed)
        assertEquals(etb(0), result.remaining)
        assertEquals(etb(0), result.overspent)
        assertFalse(result.isOverBudget)
    }

    @Test
    fun overspendingIsReportedSeparately() {
        val result = BudgetMath.progress(etb(5000), etb(6000))
        assertEquals(120, result.percentUsed)
        assertEquals(etb(0), result.remaining)
        assertEquals(etb(1000), result.overspent)
        assertTrue(result.isOverBudget)
        assertTrue(result.thresholdReached)
    }

    @Test
    fun netRefundsCountAsZeroSpent() {
        val result = BudgetMath.progress(etb(5000), etb(-5))
        assertEquals(0, result.percentUsed)
        assertEquals(etb(5000), result.remaining)
        assertEquals(etb(0), result.spent)
    }

    @Test
    fun customThresholdIsRespected() {
        assertTrue(BudgetMath.progress(etb(1000), etb(500), alertThresholdPercent = 50).thresholdReached)
        assertFalse(BudgetMath.progress(etb(1000), etb(499), alertThresholdPercent = 50).thresholdReached)
    }

    @Test
    fun invalidInputIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BudgetMath.progress(etb(0), etb(10)) }
        assertThrows(IllegalArgumentException::class.java) {
            BudgetMath.progress(etb(100), Money(1L, "USD"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BudgetMath.progress(etb(100), etb(10), alertThresholdPercent = 0)
        }
    }
}
