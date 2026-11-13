package et.birrwise.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import et.birrwise.app.R
import et.birrwise.app.domain.AccountSummary
import et.birrwise.app.domain.HomeOverview
import et.birrwise.app.domain.Money
import et.birrwise.app.domain.MoneyFormatter
import et.birrwise.app.domain.TransactionType
import et.birrwise.app.navigation.TopLevelDestination
import et.birrwise.app.ui.accounts.appContainer
import et.birrwise.app.ui.accounts.labelRes
import et.birrwise.app.ui.components.EmptyState
import et.birrwise.app.ui.components.ScreenHeader
import et.birrwise.app.ui.icon
import et.birrwise.app.ui.theme.LocalFinanceColors
import et.birrwise.app.ui.transactions.TransactionRow
import et.birrwise.app.ui.transactions.icon
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit,
    onAddTransfer: () -> Unit,
    onAddAccount: () -> Unit,
    onViewTransactions: () -> Unit,
    onReviewPending: () -> Unit,
    onReviewTransfers: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenTransaction: (String) -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            is HomeUiState.Loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            is HomeUiState.Error -> {
                EmptyState(
                    icon = TopLevelDestination.HOME.icon(),
                    title = stringResource(R.string.nav_home),
                    message = stringResource(R.string.home_error)
                )
            }
            is HomeUiState.Loaded -> {
                if (!current.overview.hasAccounts) {
                    EmptyState(
                        icon = TopLevelDestination.HOME.icon(),
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_message),
                        actionLabel = stringResource(R.string.accounts_add),
                        onAction = onAddAccount
                    )
                } else {
                    HomeContent(
                        overview = current.overview,
                        canGoNext = current.canGoNext,
                        onPreviousMonth = viewModel::previousMonth,
                        onNextMonth = viewModel::nextMonth,
                        onAddIncome = onAddIncome,
                        onAddExpense = onAddExpense,
                        onAddTransfer = onAddTransfer,
                        onAddAccount = onAddAccount,
                        onViewTransactions = onViewTransactions,
                        onReviewPending = onReviewPending,
                        onReviewTransfers = onReviewTransfers,
                        onOpenAccount = onOpenAccount,
                        onOpenTransaction = onOpenTransaction
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    overview: HomeOverview,
    canGoNext: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit,
    onAddTransfer: () -> Unit,
    onAddAccount: () -> Unit,
    onViewTransactions: () -> Unit,
    onReviewPending: () -> Unit,
    onReviewTransfers: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenTransaction: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader(title = stringResource(R.string.nav_home)) }

        item {
            MonthSelector(
                month = overview.month,
                canGoNext = canGoNext,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth
            )
        }

        item { BalanceCard(overview.totalBalance) }
        item { MonthCard(overview) }

        if (overview.pendingReviewCount > 0) {
            item {
                WarningCard(
                    text = stringResource(R.string.home_pending_warning, overview.pendingReviewCount),
                    actionLabel = stringResource(R.string.home_review_action),
                    onAction = onReviewPending
                )
            }
        }
        if (overview.unpairedTransferLegs > 0) {
            item {
                WarningCard(
                    text = stringResource(R.string.home_unpaired_warning, overview.unpairedTransferLegs),
                    actionLabel = stringResource(R.string.home_review_action),
                    onAction = onReviewTransfers
                )
            }
        }
        if (overview.unreadableTransactions > 0) {
            item {
                WarningCard(
                    text = stringResource(R.string.accounts_unreadable_warning, overview.unreadableTransactions),
                    actionLabel = null,
                    onAction = {}
                )
            }
        }

        item { QuickActions(overview, onAddIncome, onAddExpense, onAddTransfer, onAddAccount) }

        item { SectionTitle(stringResource(R.string.home_accounts_title)) }
        items(overview.accounts, key = { it.account.id }) { summary ->
            AccountLine(summary, onClick = { onOpenAccount(summary.account.id) })
        }

        item { SectionTitle(stringResource(R.string.home_recent_title)) }
        if (overview.recent.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.home_no_recent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(overview.recent, key = { it.transaction.id }) { item ->
                TransactionRow(
                    item = item,
                    showAccount = true,
                    onClick = { onOpenTransaction(item.transaction.id) }
                )
            }
        }
        item {
            TextButton(onClick = onViewTransactions, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_view_all))
            }
        }
    }
}

@Composable
private fun MonthSelector(
    month: YearMonth,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val label = remember(month) {
        month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.home_previous_month)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.home_next_month)
            )
        }
    }
}

@Composable
private fun BalanceCard(total: Money?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.accounts_total_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (total != null) {
                Text(
                    text = MoneyFormatter.format(total),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (total.isNegative) {
                        LocalFinanceColors.current.expense
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.accounts_total_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MonthCard(overview: HomeOverview) {
    val summary = overview.summary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryLine(
                icon = TransactionType.INCOME.icon(),
                label = stringResource(R.string.home_income),
                amount = "+" + MoneyFormatter.format(summary.income),
                color = LocalFinanceColors.current.income
            )
            SummaryLine(
                icon = TransactionType.EXPENSE.icon(),
                label = stringResource(R.string.home_expenses),
                amount = "\u2212" + MoneyFormatter.format(summary.expenses),
                color = LocalFinanceColors.current.expense
            )
            SummaryLine(
                icon = null,
                label = stringResource(R.string.home_net),
                amount = signedNet(summary.netCashFlow),
                color = if (summary.netCashFlow.isNegative) {
                    LocalFinanceColors.current.expense
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = stringResource(R.string.home_summary_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun signedNet(net: Money): String =
    if (net.isPositive) "+" + MoneyFormatter.format(net) else MoneyFormatter.format(net)

@Composable
private fun SummaryLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    amount: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
    }
}

@Composable
private fun WarningCard(text: String, actionLabel: String?, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun QuickActions(
    overview: HomeOverview,
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit,
    onAddTransfer: () -> Unit,
    onAddAccount: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.home_quick_actions))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddIncome, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_add_income), textAlign = TextAlign.Center)
            }
            Button(onClick = onAddExpense, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_add_expense), textAlign = TextAlign.Center)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onAddTransfer,
                enabled = overview.accounts.size >= 2,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.home_transfer), textAlign = TextAlign.Center)
            }
            OutlinedButton(onClick = onAddAccount, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.accounts_add), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun AccountLine(summary: AccountSummary, onClick: () -> Unit) {
    val account = summary.account
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(account.type.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = MoneyFormatter.format(summary.balance),
                style = MaterialTheme.typography.titleSmall,
                color = if (summary.balance.isNegative) {
                    LocalFinanceColors.current.expense
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
