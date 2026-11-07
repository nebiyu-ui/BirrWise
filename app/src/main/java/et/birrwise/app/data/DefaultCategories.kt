package et.birrwise.app.data

import et.birrwise.app.data.db.CategoryEntity
import et.birrwise.app.domain.CategoryType

/**
 * The built-in categories. Ids never change, so adding them again is harmless. The localization key
 * lets a later version show the name in Amharic.
 */
object DefaultCategories {

    class Definition(
        val id: String,
        val name: String,
        val localizationKey: String,
        val type: CategoryType
    )

    val definitions: List<Definition> = listOf(
        Definition("sys_food_groceries", "Food and groceries", "category_food_groceries", CategoryType.EXPENSE),
        Definition("sys_restaurants", "Restaurants", "category_restaurants", CategoryType.EXPENSE),
        Definition("sys_transport", "Transport", "category_transport", CategoryType.EXPENSE),
        Definition("sys_fuel", "Fuel", "category_fuel", CategoryType.EXPENSE),
        Definition("sys_housing", "Housing", "category_housing", CategoryType.EXPENSE),
        Definition("sys_utilities", "Utilities", "category_utilities", CategoryType.EXPENSE),
        Definition("sys_healthcare", "Healthcare", "category_healthcare", CategoryType.EXPENSE),
        Definition("sys_education", "Education", "category_education", CategoryType.EXPENSE),
        Definition("sys_shopping", "Shopping", "category_shopping", CategoryType.EXPENSE),
        Definition("sys_entertainment", "Entertainment", "category_entertainment", CategoryType.EXPENSE),
        Definition("sys_family_support", "Family support", "category_family_support", CategoryType.EXPENSE),
        Definition("sys_bank_fees", "Bank fees", "category_bank_fees", CategoryType.EXPENSE),
        Definition("sys_transfers", "Transfers", "category_transfers", CategoryType.TRANSFER),
        Definition("sys_income", "Income", "category_income", CategoryType.INCOME),
        Definition("sys_other", "Other", "category_other", CategoryType.EXPENSE)
    )

    internal fun entities(nowMillis: Long): List<CategoryEntity> = definitions.map {
        CategoryEntity(
            id = it.id,
            name = it.name,
            localizedNameKey = it.localizationKey,
            categoryType = it.type,
            parentCategoryId = null,
            iconId = null,
            colorId = null,
            isSystemCategory = true,
            createdAt = nowMillis,
            updatedAt = nowMillis
        )
    }
}
