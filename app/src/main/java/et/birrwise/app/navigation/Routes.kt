package et.birrwise.app.navigation

/** Screens that are not one of the five main tabs. */
object Routes {
    const val ACCOUNT_ARG = "accountId"

    const val ACCOUNT_NEW = "account-new"
    const val ACCOUNT_DETAIL = "account/{$ACCOUNT_ARG}"
    const val ACCOUNT_EDIT = "account/{$ACCOUNT_ARG}/edit"

    fun accountDetail(accountId: String): String = "account/$accountId"

    fun accountEdit(accountId: String): String = "account/$accountId/edit"
}
