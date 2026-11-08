package et.birrwise.app.domain

import et.birrwise.app.domain.TransactionStatus.PENDING_REVIEW
import et.birrwise.app.domain.TransactionType.EXPENSE
import et.birrwise.app.domain.TransactionType.INCOME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSummariesTest {

    @Test
    fun summaryCarriesBalanceCountsAndLastActivity() {
        val a = account("A", openingBirr = 1000)
        val b = account("B")
        val transactions = listOf(
            tx("t1", "A", INCOME, 50_000L, occurredAt = at("2026-01-10T10:00:00Z")),
            tx("t2", "A", EXPENSE, 10_000L, occurredAt = at("2026-01-20T10:00:00Z")),
            tx("t3", "A", INCOME, 99_900L, occurredAt = at("2026-01-25T10:00:00Z"), status = PENDING_REVIEW)
        )

        val result = AccountSummaries.build(listOf(a, b), transactions)

        val summaryA = result[0]
        assertEquals(etb(1400), summaryA.balance)
        assertEquals(2, summaryA.countedTransactions)
        assertEquals(1, summaryA.pendingTransactionsExcluded)
        assertEquals(0, summaryA.warningCount)
        // A pending transaction is still activity.
        assertEquals(at("2026-01-25T10:00:00Z"), summaryA.lastActivityAt)

        val summaryB = result[1]
        assertEquals(etb(0), summaryB.balance)
        assertNull(summaryB.lastActivityAt)
    }

    @Test
    fun warningsAreCounted() {
        val a = account("A", openingBirr = 100)
        val transactions = listOf(tx("usd", "A", INCOME, 5_000L, currency = "USD"))
        val summary = AccountSummaries.build(listOf(a), transactions).single()
        assertEquals(etb(100), summary.balance)
        assertEquals(1, summary.warningCount)
    }

    @Test
    fun totalsAreGroupedByCurrencyAndSkipArchivedAccounts() {
        val summaries = AccountSummaries.build(
            listOf(
                account("A1", openingBirr = 1000),
                account("A2", openingBirr = 500),
                account("A3", openingBirr = 300).copy(archived = true),
                account("U1", openingBirr = 20, currency = "USD")
            ),
            emptyList()
        )
        assertEquals(
            listOf(etb(1500), Money(2_000L, "USD")),
            AccountSummaries.totalsByCurrency(summaries)
        )
    }

    @Test
    fun noActiveAccountsMeansNoTotals() {
        val summaries = AccountSummaries.build(
            listOf(account("A1", openingBirr = 10).copy(archived = true)),
            emptyList()
        )
        assertEquals(emptyList<Money>(), AccountSummaries.totalsByCurrency(summaries))
    }
}
