package et.birrwise.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {

    @Test
    fun buildsRoutesFromIds() {
        assertEquals("account/abc-123", Routes.accountDetail("abc-123"))
        assertEquals("account/abc-123/edit", Routes.accountEdit("abc-123"))
    }

    @Test
    fun patternsUseTheSharedArgumentName() {
        assertEquals("account/{accountId}", Routes.ACCOUNT_DETAIL)
        assertEquals("account/{accountId}/edit", Routes.ACCOUNT_EDIT)
        assertEquals("accountId", Routes.ACCOUNT_ARG)
    }

    @Test
    fun extraRoutesNeverCollideWithTabsOrEachOther() {
        val extra = listOf(Routes.ACCOUNT_NEW, Routes.ACCOUNT_DETAIL, Routes.ACCOUNT_EDIT)
        val tabs = TopLevelDestination.entries.map { it.route }
        assertTrue(extra.none { it in tabs })
        assertEquals(extra.size, extra.toSet().size)
    }
}
