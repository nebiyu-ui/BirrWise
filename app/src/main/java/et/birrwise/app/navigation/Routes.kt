package et.birrwise.app.navigation

/** Screens that are not one of the five main tabs. */
object Routes {
    const val ACCOUNT_ARG = "accountId"
    const val TRANSACTION_ARG = "transactionId"
    const val KIND_ARG = "kind"

    const val ACCOUNT_NEW = "account-new"
    const val ACCOUNT_DETAIL = "account/{$ACCOUNT_ARG}"
    const val ACCOUNT_EDIT = "account/{$ACCOUNT_ARG}/edit"

    /** Both arguments are optional: a starting account, and a starting kind of entry such as INCOME. */
    const val TRANSACTION_NEW = "transaction-new?$ACCOUNT_ARG={$ACCOUNT_ARG}&$KIND_ARG={$KIND_ARG}"
    const val TRANSACTION_EDIT = "transaction/{$TRANSACTION_ARG}"

    fun accountDetail(accountId: String): String = "account/$accountId"

    fun accountEdit(accountId: String): String = "account/$accountId/edit"

    fun transactionNew(accountId: String? = null, kind: String? = null): String {
        val query = listOfNotNull(
            accountId?.let { "$ACCOUNT_ARG=$it" },
            kind?.let { "$KIND_ARG=$it" }
        )
        return if (query.isEmpty()) "transaction-new" else "transaction-new?" + query.joinToString("&")
    }

    fun transactionEdit(transactionId: String): String = "transaction/$transactionId"
}
