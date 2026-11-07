package et.birrwise.app.domain

import org.junit.Assert.assertThrows
import org.junit.Test

class ModelsTest {

    @Test
    fun transactionAmountMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            tx("t1", "A", TransactionType.EXPENSE, 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            tx("t1", "A", TransactionType.EXPENSE, -100L)
        }
    }

    @Test
    fun idsMustNotBeBlank() {
        assertThrows(IllegalArgumentException::class.java) {
            tx(" ", "A", TransactionType.EXPENSE, 100L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            tx("t1", "", TransactionType.EXPENSE, 100L)
        }
    }

    @Test
    fun openingBalanceMustMatchTheAccountCurrency() {
        assertThrows(IllegalArgumentException::class.java) {
            Account("A", "Cash", ETB, Money(100L, "USD"))
        }
    }
}
