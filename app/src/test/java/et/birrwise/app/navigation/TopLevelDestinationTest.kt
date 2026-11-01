package et.birrwise.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun hasFiveDestinationsInDisplayOrder() {
        assertEquals(
            listOf("home", "transactions", "accounts", "budgets", "people"),
            TopLevelDestination.entries.map { it.route }
        )
    }

    @Test
    fun routesAreUnique() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }
}
