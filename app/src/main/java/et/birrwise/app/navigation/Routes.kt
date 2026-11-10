package et.birrwise.app.navigation

/** Screens that are not one of the five main tabs. */
object Routes {
    const val ACCOUNT_ARG = "accountId"
    const val TRANSACTION_ARG = "transactionId"

    const val ACCOUNT_NEW = "account-new"
    const val ACCOUNT_DETAIL = "account/{$ACCOUNT_ARG}"
    const val ACCOUNT_EDIT = "account/{$ACCOUNT_ARG}/edit"

    /** The account id is optional: when present, the new transaction starts on that account. */
    const val TRANSACTION_NEW = "transaction-new?$ACCOUNT_ARG={$ACCOUNT_ARG}"
    const val TRANSACTION_EDIT = "transaction/{$TRANSACTION_ARG}"

    fun accountDetail(accountId: String): String = "account/$accountId"

    fun accountEdit(accountId: String): String = "account/$accountId/edit"

    fun transactionNew(accountId: String? = null): String =
        if (accountId == null) "transaction-new" else "transaction-new?$ACCOUNT_ARG=$accountId"

    fun transactionEdit(transactionId: String): String = "transaction/$transactionId"
}
