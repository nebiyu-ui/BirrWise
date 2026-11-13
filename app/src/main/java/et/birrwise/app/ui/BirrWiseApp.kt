package et.birrwise.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import et.birrwise.app.domain.EntryKind
import et.birrwise.app.domain.TransactionFilter
import et.birrwise.app.navigation.Routes
import et.birrwise.app.navigation.TopLevelDestination
import et.birrwise.app.ui.accounts.AccountDetailScreen
import et.birrwise.app.ui.accounts.appContainer
import et.birrwise.app.ui.accounts.AccountEditorScreen
import et.birrwise.app.ui.accounts.AccountsScreen
import et.birrwise.app.ui.screens.BudgetsScreen
import et.birrwise.app.ui.home.HomeScreen
import et.birrwise.app.ui.screens.PeopleScreen
import et.birrwise.app.ui.transactions.TransactionEditorScreen
import et.birrwise.app.ui.transactions.TransactionsScreen

@Composable
fun BirrWiseApp() {
    val navController = rememberNavController()
    val container = LocalContext.current.appContainer()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentDestination?.route }

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTab(destination.route) },
                            icon = { Icon(destination.icon(), contentDescription = null) },
                            label = {
                                Text(
                                    text = stringResource(destination.labelRes()),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onAddIncome = { navController.navigate(Routes.transactionNew(kind = "INCOME")) },
                    onAddExpense = { navController.navigate(Routes.transactionNew(kind = "EXPENSE")) },
                    onAddTransfer = { navController.navigate(Routes.transactionNew(kind = "TRANSFER")) },
                    onAddAccount = { navController.navigate(Routes.ACCOUNT_NEW) },
                    onViewTransactions = {
                        container.transactionFilter.value = TransactionFilter()
                        navigateToTab(TopLevelDestination.TRANSACTIONS.route)
                    },
                    onReviewPending = {
                        container.transactionFilter.value = TransactionFilter(reviewOnly = true)
                        navigateToTab(TopLevelDestination.TRANSACTIONS.route)
                    },
                    onReviewTransfers = {
                        container.transactionFilter.value = TransactionFilter(kind = EntryKind.TRANSFER)
                        navigateToTab(TopLevelDestination.TRANSACTIONS.route)
                    },
                    onOpenAccount = { id -> navController.navigate(Routes.accountDetail(id)) },
                    onOpenTransaction = { id -> navController.navigate(Routes.transactionEdit(id)) }
                )
            }
            composable(TopLevelDestination.TRANSACTIONS.route) {
                TransactionsScreen(
                    onOpenTransaction = { id -> navController.navigate(Routes.transactionEdit(id)) },
                    onAddTransaction = { navController.navigate(Routes.transactionNew()) },
                    onAddAccount = { navController.navigate(Routes.ACCOUNT_NEW) }
                )
            }
            composable(TopLevelDestination.ACCOUNTS.route) {
                AccountsScreen(
                    onOpenAccount = { id -> navController.navigate(Routes.accountDetail(id)) },
                    onAddAccount = { navController.navigate(Routes.ACCOUNT_NEW) }
                )
            }
            composable(TopLevelDestination.BUDGETS.route) { BudgetsScreen() }
            composable(TopLevelDestination.PEOPLE.route) { PeopleScreen() }

            composable(Routes.ACCOUNT_NEW) {
                AccountEditorScreen(
                    accountId = null,
                    onDone = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.ACCOUNT_DETAIL,
                arguments = listOf(navArgument(Routes.ACCOUNT_ARG) { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString(Routes.ACCOUNT_ARG).orEmpty()
                AccountDetailScreen(
                    accountId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.accountEdit(id)) },
                    onAddTransaction = { navController.navigate(Routes.transactionNew(id)) },
                    onOpenTransaction = { txId -> navController.navigate(Routes.transactionEdit(txId)) }
                )
            }
            composable(
                route = Routes.ACCOUNT_EDIT,
                arguments = listOf(navArgument(Routes.ACCOUNT_ARG) { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString(Routes.ACCOUNT_ARG).orEmpty()
                AccountEditorScreen(
                    accountId = id,
                    onDone = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.TRANSACTION_NEW,
                arguments = listOf(
                    navArgument(Routes.ACCOUNT_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Routes.KIND_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                TransactionEditorScreen(
                    transactionId = null,
                    presetAccountId = entry.arguments?.getString(Routes.ACCOUNT_ARG),
                    presetKind = entry.arguments?.getString(Routes.KIND_ARG),
                    onDone = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.TRANSACTION_EDIT,
                arguments = listOf(navArgument(Routes.TRANSACTION_ARG) { type = NavType.StringType })
            ) { entry ->
                TransactionEditorScreen(
                    transactionId = entry.arguments?.getString(Routes.TRANSACTION_ARG).orEmpty(),
                    presetAccountId = null,
                    presetKind = null,
                    onDone = { navController.popBackStack() }
                )
            }
        }
    }
}
