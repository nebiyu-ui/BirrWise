package et.birrwise.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import et.birrwise.app.R
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.MoneyFormatter
import et.birrwise.app.ui.components.ScreenHeader
import et.birrwise.app.ui.theme.LocalFinanceColors
import kotlinx.coroutines.launch
import java.time.ZoneId

@Composable
fun AccountDetailScreen(
    accountId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: AccountDetailViewModel = viewModel(
        key = "account-detail-$accountId",
        factory = AccountDetailViewModel.factory(container, accountId)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmArchive by rememberSaveable { mutableStateOf(false) }
    var actionFailed by rememberSaveable { mutableStateOf(false) }

    val title = (state as? AccountDetailUiState.Loaded)?.summary?.account?.name.orEmpty()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = title, onBack = onBack)

        when (val current = state) {
            is AccountDetailUiState.Loading -> {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
            is AccountDetailUiState.NotFound -> MessageText(stringResource(R.string.detail_not_found))
            is AccountDetailUiState.Error -> MessageText(stringResource(R.string.detail_load_failed))
            is AccountDetailUiState.Loaded -> {
                val account = current.summary.account
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BalanceCard(current)
                    DetailsCard(account)

                    Text(
                        text = stringResource(R.string.detail_transactions_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.detail_transactions_soon),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (actionFailed) {
                        Text(
                            text = stringResource(R.string.detail_action_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.detail_edit))
                    }
                    OutlinedButton(
                        onClick = {
                            if (account.archived) {
                                scope.launch {
                                    actionFailed = !viewModel.setArchived(false)
                                }
                            } else {
                                confirmArchive = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (account.archived) R.string.detail_restore else R.string.detail_archive
                            )
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(R.string.detail_archive_title)) },
            text = { Text(stringResource(R.string.detail_archive_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmArchive = false
                        scope.launch { actionFailed = !viewModel.setArchived(true) }
                    }
                ) { Text(stringResource(R.string.detail_archive)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun MessageText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun BalanceCard(state: AccountDetailUiState.Loaded) {
    val balance = state.summary.balance
    val color =
        if (balance.isNegative) LocalFinanceColors.current.expense
        else MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.accounts_tracked_balance),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = MoneyFormatter.format(balance),
                style = MaterialTheme.typography.headlineMedium,
                color = color
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.accounts_total_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (state.unreadableTransactions > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.accounts_unreadable_warning, state.unreadableTransactions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DetailsCard(account: Account) {
    val openingDateText = account.openingBalanceDate
        ?.atZone(ZoneId.systemDefault())
        ?.toLocalDate()
        ?.let { stringResource(R.string.detail_opening_from, it.toString()) }
        ?: stringResource(R.string.detail_opening_all_dates)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailRow(
                stringResource(R.string.detail_status),
                stringResource(
                    if (account.archived) R.string.detail_status_archived else R.string.detail_status_active
                )
            )
            DetailRow(stringResource(R.string.detail_type), stringResource(account.type.labelRes()))
            account.provider?.let { DetailRow(stringResource(R.string.detail_provider), it) }
            DetailRow(stringResource(R.string.detail_currency), account.currencyCode)
            DetailRow(
                stringResource(R.string.detail_opening_balance),
                MoneyFormatter.format(account.openingBalance) + "\n" + openingDateText
            )
            account.notes?.let { DetailRow(stringResource(R.string.detail_notes), it) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
