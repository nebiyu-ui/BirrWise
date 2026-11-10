package et.birrwise.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {

    @Test
    fun buildsRoutesFromIds() {
        assertEquals("account/abc-123", Routes.accountDetail("abc-123"))
        assertEquals("account/abc-123/edit", Routes.accountEdit("abc-123"))
        assertEquals("transaction/t-9", Routes.transactionEdit("t-9"))
    }

    @Test
    fun newTransactionRouteCarriesAnOptionalAccount() {
        assertEquals("transaction-new", Routes.transactionNew())
        assertEquals("transaction-new?accountId=abc-123", Routes.transactionNew("abc-123"))
    }

    @Test
    fun patternsUseTheSharedArgumentNames() {
        assertEquals("account/{accountId}", Routes.ACCOUNT_DETAIL)
        assertEquals("account/{accountId}/edit", Routes.ACCOUNT_EDIT)
        assertEquals("transaction-new?accountId={accountId}", Routes.TRANSACTION_NEW)
        assertEquals("transaction/{transactionId}", Routes.TRANSACTION_EDIT)
        assertEquals("accountId", Routes.ACCOUNT_ARG)
        assertEquals("transactionId", Routes.TRANSACTION_ARG)
    }

    @Test
    fun extraRoutesNeverCollideWithTabsOrEachOther() {
        val extra = listOf(
            Routes.ACCOUNT_NEW,
            Routes.ACCOUNT_DETAIL,
            Routes.ACCOUNT_EDIT,
            Routes.TRANSACTION_NEW,
            Routes.TRANSACTION_EDIT
        )
        val tabs = TopLevelDestination.entries.map { it.route }
        assertTrue(extra.none { it in tabs })
        assertEquals(extra.size, extra.toSet().size)
    }
}
