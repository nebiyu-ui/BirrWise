package et.birrwise.app.navigation

/**
 * The five main destinations shown in the bottom navigation bar, in display order.
 * Labels and icons live in the UI layer so this list stays easy to unit test.
 */
enum class TopLevelDestination(val route: String) {
    HOME("home"),
    TRANSACTIONS("transactions"),
    ACCOUNTS("accounts"),
    BUDGETS("budgets"),
    PEOPLE("people")
}
