package et.birrwise.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import et.birrwise.app.R
import et.birrwise.app.domain.AccountSummary
import et.birrwise.app.domain.MoneyFormatter
import et.birrwise.app.navigation.TopLevelDestination
import et.birrwise.app.ui.components.EmptyState
import et.birrwise.app.ui.components.ScreenHeader
import et.birrwise.app.ui.icon
import et.birrwise.app.ui.theme.LocalFinanceColors

@Composable
fun AccountsScreen(
    onOpenAccount: (String) -> Unit,
    onAddAccount: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: AccountsViewModel = viewModel(factory = AccountsViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            is AccountsUiState.Loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            is AccountsUiState.Error -> {
                EmptyState(
                    icon = TopLevelDestination.ACCOUNTS.icon(),
                    title = stringResource(R.string.nav_accounts),
                    message = stringResource(R.string.accounts_error)
                )
            }
            is AccountsUiState.Loaded -> {
                if (current.active.isEmpty() && current.archived.isEmpty()) {
                    EmptyState(
                        icon = TopLevelDestination.ACCOUNTS.icon(),
                        title = stringResource(R.string.accounts_empty_title),
                        message = stringResource(R.string.accounts_empty_message),
                        actionLabel = stringResource(R.string.accounts_add),
                        onAction = onAddAccount
                    )
                } else {
                    AccountsList(current, onOpenAccount)
                    ExtendedFloatingActionButton(
                        onClick = onAddAccount,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.accounts_add)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountsList(
    state: AccountsUiState.Loaded,
    onOpenAccount: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.nav_accounts),
                modifier = Modifier.padding(horizontal = 0.dp)
            )
        }
        if (state.totals.isNotEmpty()) {
            item { TotalsCard(state) }
        }
        if (state.unreadableTransactions > 0) {
            item {
                Text(
                    text = stringResource(R.string.accounts_unreadable_warning, state.unreadableTransactions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        items(state.active, key = { it.account.id }) { summary ->
            AccountCard(summary, onClick = { onOpenAccount(summary.account.id) })
        }
        if (state.archived.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.accounts_archived_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(state.archived, key = { it.account.id }) { summary ->
                AccountCard(summary, onClick = { onOpenAccount(summary.account.id) })
            }
        }
    }
}

@Composable
private fun TotalsCard(state: AccountsUiState.Loaded) {
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
            Spacer(Modifier.height(4.dp))
            for (total in state.totals) {
                Text(
                    text = MoneyFormatter.format(total),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
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
private fun AccountCard(summary: AccountSummary, onClick: () -> Unit) {
    val account = summary.account
    val subtitle = listOfNotNull(
        account.provider,
        stringResource(account.type.labelRes())
    ).joinToString(" · ")
    val balanceColor =
        if (summary.balance.isNegative) LocalFinanceColors.current.expense
        else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (account.archived) {
                    subtitle + " · " + stringResource(R.string.detail_status_archived)
                } else {
                    subtitle
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.accounts_tracked_balance),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = MoneyFormatter.format(summary.balance),
                style = MaterialTheme.typography.titleLarge,
                color = balanceColor
            )
        }
    }
}
