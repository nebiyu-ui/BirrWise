package et.birrwise.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetFormValidatorTest {

    private val categories = listOf(
        Category(id = "food", name = "Food", type = CategoryType.EXPENSE),
        Category(id = "fuel", name = "Fuel", type = CategoryType.EXPENSE),
        Category(id = "pay", name = "Pay", type = CategoryType.INCOME)
    ).associateBy { it.id }

    private fun validate(
        categoryId: String? = "food",
        limit: String = "5000",
        threshold: Int = 80,
        withBudget: Set<String> = emptySet(),
        editing: Boolean = false
    ): BudgetFormResult = BudgetFormValidator.validate(
        BudgetFormInput(categoryId, limit, threshold), categories, withBudget, editing
    )

    private fun errors(result: BudgetFormResult): Set<BudgetFormError> {
        assertTrue("Expected errors but got $result", result is BudgetFormResult.Invalid)
        return (result as BudgetFormResult.Invalid).errors
    }

    @Test
    fun aGoodFormIsAccepted() {
        val result = validate(limit = "1,250.50", threshold = 90) as BudgetFormResult.Valid
        assertEquals("food", result.categoryId)
        assertEquals(Money(125_050L, ETB), result.limit)
        assertEquals(90, result.thresholdPercent)
    }

    @Test
    fun theCategoryMustExistAndBeAnExpenseCategory() {
        assertEquals(setOf(BudgetFormError.CATEGORY_REQUIRED), errors(validate(categoryId = null)))
        assertEquals(setOf(BudgetFormError.CATEGORY_REQUIRED), errors(validate(categoryId = "unknown")))
        assertEquals(setOf(BudgetFormError.CATEGORY_NOT_EXPENSE), errors(validate(categoryId = "pay")))
    }

    @Test
    fun eachCategoryHasOneBudgetButEditingIsAllowed() {
        assertEquals(
            setOf(BudgetFormError.CATEGORY_HAS_BUDGET),
            errors(validate(withBudget = setOf("food")))
        )
        assertTrue(validate(withBudget = setOf("food"), editing = true) is BudgetFormResult.Valid)
        assertTrue(validate(categoryId = "fuel", withBudget = setOf("food")) is BudgetFormResult.Valid)
    }

    @Test
    fun theLimitMustBeAPositiveAmount() {
        assertEquals(setOf(BudgetFormError.LIMIT_REQUIRED), errors(validate(limit = "  ")))
        assertEquals(setOf(BudgetFormError.LIMIT_INVALID), errors(validate(limit = "abc")))
        assertEquals(setOf(BudgetFormError.LIMIT_INVALID), errors(validate(limit = "12,50")))
        assertEquals(setOf(BudgetFormError.LIMIT_INVALID), errors(validate(limit = "-5")))
        assertEquals(setOf(BudgetFormError.LIMIT_ZERO), errors(validate(limit = "0")))
    }

    @Test
    fun theAlertShareMustBeBetweenOneAndOneHundred() {
        assertEquals(setOf(BudgetFormError.THRESHOLD_INVALID), errors(validate(threshold = 0)))
        assertEquals(setOf(BudgetFormError.THRESHOLD_INVALID), errors(validate(threshold = 101)))
        assertTrue(validate(threshold = 1) is BudgetFormResult.Valid)
        assertTrue(validate(threshold = 100) is BudgetFormResult.Valid)
    }

    @Test
    fun severalProblemsAreReportedTogether() {
        assertEquals(
            setOf(BudgetFormError.CATEGORY_REQUIRED, BudgetFormError.LIMIT_REQUIRED),
            errors(validate(categoryId = null, limit = ""))
        )
    }
}
