package et.birrwise.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import et.birrwise.app.R
import et.birrwise.app.navigation.TopLevelDestination

internal fun TopLevelDestination.labelRes(): Int = when (this) {
    TopLevelDestination.HOME -> R.string.nav_home
    TopLevelDestination.TRANSACTIONS -> R.string.nav_transactions
    TopLevelDestination.ACCOUNTS -> R.string.nav_accounts
    TopLevelDestination.BUDGETS -> R.string.nav_budgets
    TopLevelDestination.PEOPLE -> R.string.nav_people
}

internal fun TopLevelDestination.icon(): ImageVector = when (this) {
    TopLevelDestination.HOME -> Icons.Filled.Home
    TopLevelDestination.TRANSACTIONS -> Icons.Filled.SwapHoriz
    TopLevelDestination.ACCOUNTS -> Icons.Filled.AccountBalanceWallet
    TopLevelDestination.BUDGETS -> Icons.Filled.PieChart
    TopLevelDestination.PEOPLE -> Icons.Filled.People
}
