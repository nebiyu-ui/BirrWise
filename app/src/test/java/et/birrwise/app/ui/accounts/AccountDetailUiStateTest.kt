package et.birrwise.app.ui.accounts

import et.birrwise.app.domain.TransactionType.EXPENSE
import et.birrwise.app.domain.account
import et.birrwise.app.domain.etb
import et.birrwise.app.domain.repository.TransactionsSnapshot
import et.birrwise.app.domain.tx
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AccountDetailUiStateTest {

    @Test
    fun listsOnlyTheLatestOwnTransactionsNewestFirst() {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        val own = (1..60).map { i ->
            tx("t$i", "A1", EXPENSE, 100L, occurredAt = base.plusSeconds(i * 60L))
        }
        val foreign = tx("other", "B1", EXPENSE, 100L, occurredAt = base.plusSeconds(10_000L))

        val state = accountDetailUiState(
            "A1",
            listOf(account("A1", openingBirr = 1000), account("B1")),
            TransactionsSnapshot(own + foreign, 0)
        ) as AccountDetailUiState.Loaded

        assertEquals(RECENT_TRANSACTIONS_LIMIT, state.recent.size)
        assertEquals("t60", state.recent.first().transaction.id)
        assertEquals(60, state.totalTransactions)
        // 60 expenses of 1.00 each leave 940.00 of the 1,000.00 opening balance.
        assertEquals(etb(940), state.summary.balance)
    }
}
