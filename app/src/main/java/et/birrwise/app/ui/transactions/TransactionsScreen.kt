package et.birrwise.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import et.birrwise.app.R
import et.birrwise.app.navigation.TopLevelDestination
import et.birrwise.app.ui.accounts.appContainer
import et.birrwise.app.ui.components.EmptyState
import et.birrwise.app.ui.components.ScreenHeader
import et.birrwise.app.ui.icon

@Composable
fun TransactionsScreen(
    onOpenTransaction: (String) -> Unit,
    onAddTransaction: () -> Unit,
    onAddAccount: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: TransactionsViewModel = viewModel(factory = TransactionsViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            is TransactionsUiState.Loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            is TransactionsUiState.Error -> {
                EmptyState(
                    icon = TopLevelDestination.TRANSACTIONS.icon(),
                    title = stringResource(R.string.nav_transactions),
                    message = stringResource(R.string.transactions_error)
                )
            }
            is TransactionsUiState.Loaded -> {
                if (!current.hasActiveAccounts) {
                    EmptyState(
                        icon = TopLevelDestination.TRANSACTIONS.icon(),
                        title = stringResource(R.string.transactions_need_account_title),
                        message = stringResource(R.string.transactions_need_account_message),
                        actionLabel = stringResource(R.string.accounts_add),
                        onAction = onAddAccount
                    )
                } else if (current.items.isEmpty()) {
                    EmptyState(
                        icon = TopLevelDestination.TRANSACTIONS.icon(),
                        title = stringResource(R.string.transactions_empty_title),
                        message = stringResource(R.string.transactions_empty_message_manual),
                        actionLabel = stringResource(R.string.transactions_add),
                        onAction = onAddTransaction
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { ScreenHeader(title = stringResource(R.string.nav_transactions)) }
                        if (current.unreadableCount > 0) {
                            item {
                                Text(
                                    text = stringResource(
                                        R.string.accounts_unreadable_warning,
                                        current.unreadableCount
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        items(current.items, key = { it.transaction.id }) { item ->
                            TransactionRow(
                                item = item,
                                showAccount = true,
                                onClick = { onOpenTransaction(item.transaction.id) }
                            )
                        }
                    }
                    ExtendedFloatingActionButton(
                        onClick = onAddTransaction,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.transactions_add)) }
                    )
                }
            }
        }
    }
}
