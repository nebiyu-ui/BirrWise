package et.birrwise.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import et.birrwise.app.R
import et.birrwise.app.navigation.TopLevelDestination
import et.birrwise.app.ui.components.EmptyState
import et.birrwise.app.ui.icon

@Composable
fun HomeScreen() {
    EmptyState(
        icon = TopLevelDestination.HOME.icon(),
        title = stringResource(R.string.home_empty_title),
        message = stringResource(R.string.home_empty_message)
    )
}

@Composable
fun TransactionsScreen() {
    EmptyState(
        icon = TopLevelDestination.TRANSACTIONS.icon(),
        title = stringResource(R.string.transactions_empty_title),
        message = stringResource(R.string.transactions_empty_message)
    )
}

@Composable
fun BudgetsScreen() {
    EmptyState(
        icon = TopLevelDestination.BUDGETS.icon(),
        title = stringResource(R.string.budgets_empty_title),
        message = stringResource(R.string.budgets_empty_message)
    )
}

@Composable
fun PeopleScreen() {
    EmptyState(
        icon = TopLevelDestination.PEOPLE.icon(),
        title = stringResource(R.string.people_empty_title),
        message = stringResource(R.string.people_empty_message)
    )
}
