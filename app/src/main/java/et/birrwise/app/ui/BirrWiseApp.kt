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
import et.birrwise.app.navigation.Routes
import et.birrwise.app.navigation.TopLevelDestination
import et.birrwise.app.ui.accounts.AccountDetailScreen
import et.birrwise.app.ui.accounts.AccountEditorScreen
import et.birrwise.app.ui.accounts.AccountsScreen
import et.birrwise.app.ui.screens.BudgetsScreen
import et.birrwise.app.ui.screens.HomeScreen
import et.birrwise.app.ui.screens.PeopleScreen
import et.birrwise.app.ui.screens.TransactionsScreen

@Composable
fun BirrWiseApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentDestination?.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
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
            composable(TopLevelDestination.HOME.route) { HomeScreen() }
            composable(TopLevelDestination.TRANSACTIONS.route) { TransactionsScreen() }
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
                    onEdit = { navController.navigate(Routes.accountEdit(id)) }
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
        }
    }
}
