package et.birrwise.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultCategoriesTest {

    @Test
    fun hasTheFifteenPlannedCategoriesWithUniqueIds() {
        val ids = DefaultCategories.definitions.map { it.id }
        assertEquals(15, ids.size)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyIdStartsWithTheSystemPrefix() {
        assertEquals(
            emptyList<String>(),
            DefaultCategories.definitions.map { it.id }.filterNot { it.startsWith("sys_") }
        )
    }
}
