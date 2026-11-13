package et.birrwise.app.ui.transactions

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import et.birrwise.app.domain.EntryKind
import et.birrwise.app.domain.TransactionFilter
import et.birrwise.app.domain.TransactionListRow
import et.birrwise.app.domain.TransactionSort
import et.birrwise.app.navigation.TopLevelDestination
import et.birrwise.app.ui.accounts.appContainer
import et.birrwise.app.ui.components.DropdownField
import et.birrwise.app.ui.components.EmptyState
import et.birrwise.app.ui.components.OptionalDateField
import et.birrwise.app.ui.components.ScreenHeader
import et.birrwise.app.ui.icon
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TransactionsScreen(
    onOpenTransaction: (String) -> Unit,
    onAddTransaction: () -> Unit,
    onAddAccount: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: TransactionsViewModel = viewModel(factory = TransactionsViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The search box keeps its own text while it is on screen. It starts from the saved search.
    var queryText by remember { mutableStateOf(viewModel.currentQuery()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

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
                } else if (current.totalCount == 0) {
                    EmptyState(
                        icon = TopLevelDestination.TRANSACTIONS.icon(),
                        title = stringResource(R.string.transactions_empty_title),
                        message = stringResource(R.string.transactions_empty_message_manual),
                        actionLabel = stringResource(R.string.transactions_add),
                        onAction = onAddTransaction
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        ScreenHeader(title = stringResource(R.string.nav_transactions))

                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = queryText,
                                onValueChange = {
                                    queryText = it
                                    viewModel.updateFilter { filter -> filter.copy(query = it) }
                                },
                                label = { Text(stringResource(R.string.transactions_search_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { showFilters = true }) {
                                    Text(
                                        if (current.filter.activeCount > 0) {
                                            stringResource(
                                                R.string.transactions_filters_count,
                                                current.filter.activeCount
                                            )
                                        } else {
                                            stringResource(R.string.transactions_filters)
                                        }
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                if (current.filter.isActive) {
                                    TextButton(
                                        onClick = {
                                            queryText = ""
                                            viewModel.clearFilters()
                                        }
                                    ) {
                                        Text(stringResource(R.string.transactions_clear_filters))
                                    }
                                }
                            }
                            if (current.filter.isActive) {
                                Text(
                                    text = stringResource(
                                        R.string.transactions_showing,
                                        current.matchCount,
                                        current.totalCount
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (current.rows.isEmpty()) {
                            Text(
                                text = stringResource(R.string.transactions_no_matches),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
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
                                items(
                                    current.rows,
                                    key = { row ->
                                        when (row) {
                                            is TransactionListRow.DateHeader -> "header-" + row.date
                                            is TransactionListRow.Entry -> row.item.transaction.id
                                        }
                                    }
                                ) { row ->
                                    when (row) {
                                        is TransactionListRow.DateHeader -> DayHeading(row)
                                        is TransactionListRow.Entry -> TransactionRow(
                                            item = row.item,
                                            showAccount = true,
                                            onClick = { onOpenTransaction(row.item.transaction.id) }
                                        )
                                    }
                                }
                            }
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

                    if (showFilters) {
                        FilterDialog(
                            state = current,
                            onChange = { change -> viewModel.updateFilter(change) },
                            onClear = {
                                queryText = ""
                                viewModel.clearFilters()
                            },
                            onDismiss = { showFilters = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeading(row: TransactionListRow.DateHeader) {
    val weekday = row.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    Text(
        text = "$weekday, ${row.date}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun FilterDialog(
    state: TransactionsUiState.Loaded,
    onChange: ((TransactionFilter) -> TransactionFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val filter = state.filter

    val allAccounts = stringResource(R.string.filter_all_accounts)
    val accountOptions = listOf("" to allAccounts) + state.accountOptions
    val accountText = accountOptions.firstOrNull { it.first == (filter.accountId ?: "") }?.second ?: allAccounts

    val allCategories = stringResource(R.string.filter_all_categories)
    val noCategory = stringResource(R.string.filter_no_category)
    val categoryOptions =
        listOf("" to allCategories, TransactionFilter.UNCATEGORIZED to noCategory) + state.categoryOptions
    val categoryText = categoryOptions.firstOrNull { it.first == (filter.categoryId ?: "") }?.second ?: allCategories

    val allTypes = stringResource(R.string.filter_all_types)
    val typeOptions = listOf("" to allTypes) +
        EntryKind.entries.map { it.name to stringResource(it.labelRes()) }
    val typeText = typeOptions.firstOrNull { it.first == (filter.kind?.name ?: "") }?.second ?: allTypes

    val sortOptions = TransactionSort.entries.map { it.name to stringResource(it.labelRes()) }
    val sortText = sortOptions.firstOrNull { it.first == filter.sort.name }?.second ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DropdownField(
                    label = stringResource(R.string.filter_account),
                    selectedText = accountText,
                    options = accountOptions,
                    onSelect = { id -> onChange { it.copy(accountId = id.ifEmpty { null }) } }
                )
                DropdownField(
                    label = stringResource(R.string.filter_category),
                    selectedText = categoryText,
                    options = categoryOptions,
                    onSelect = { id -> onChange { it.copy(categoryId = id.ifEmpty { null }) } }
                )
                DropdownField(
                    label = stringResource(R.string.filter_type),
                    selectedText = typeText,
                    options = typeOptions,
                    onSelect = { name ->
                        onChange { it.copy(kind = EntryKind.entries.firstOrNull { kind -> kind.name == name }) }
                    }
                )
                OptionalDateField(
                    label = stringResource(R.string.filter_from_date),
                    date = filter.fromDate,
                    onDateChange = { picked ->
                        onChange {
                            val currentTo = it.toDate
                            it.copy(
                                fromDate = picked,
                                toDate = if (picked != null && currentTo != null && currentTo < picked) picked else currentTo
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OptionalDateField(
                    label = stringResource(R.string.filter_to_date),
                    date = filter.toDate,
                    onDateChange = { picked ->
                        onChange {
                            val currentFrom = it.fromDate
                            it.copy(
                                toDate = picked,
                                fromDate = if (picked != null && currentFrom != null && currentFrom > picked) picked else currentFrom
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.filter_review_only),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = filter.reviewOnly,
                        onCheckedChange = { checked -> onChange { it.copy(reviewOnly = checked) } }
                    )
                }
                DropdownField(
                    label = stringResource(R.string.filter_sort),
                    selectedText = sortText,
                    options = sortOptions,
                    onSelect = { name ->
                        onChange {
                            it.copy(sort = TransactionSort.entries.firstOrNull { sort -> sort.name == name } ?: it.sort)
                        }
                    }
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.filter_done)) }
        },
        dismissButton = {
            TextButton(onClick = onClear) { Text(stringResource(R.string.transactions_clear_filters)) }
        }
    )
}
