package et.birrwise.app.domain

/** A transaction together with the names a list row needs. */
data class TransactionListItem(
    val transaction: Transaction,
    val accountName: String,
    val categoryName: String?
)

object TransactionList {

    /** Newest first. Transactions at the same moment keep a fixed order by id. */
    fun build(
        transactions: List<Transaction>,
        accounts: List<Account>,
        categories: List<Category>
    ): List<TransactionListItem> {
        val accountNames = accounts.associate { it.id to it.name }
        val categoryNames = categories.associate { it.id to it.name }
        return transactions
            .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenBy { it.id })
            .map { transaction ->
                TransactionListItem(
                    transaction = transaction,
                    accountName = accountNames[transaction.accountId].orEmpty(),
                    categoryName = transaction.categoryId?.let { categoryNames[it] }
                )
            }
    }
}
