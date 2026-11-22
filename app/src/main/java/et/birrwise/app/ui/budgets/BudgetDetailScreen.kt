package et.birrwise.app.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import et.birrwise.app.domain.Budget
import et.birrwise.app.domain.BudgetMonth
import et.birrwise.app.domain.BudgetStatus
import et.birrwise.app.domain.MoneyFormatter
import et.birrwise.app.ui.accounts.appContainer
import et.birrwise.app.ui.components.ScreenHeader
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun BudgetDetailScreen(
    categoryId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onReviewCategory: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: BudgetDetailViewModel = viewModel(
        key = "budget-detail-$categoryId",
        factory = BudgetDetailViewModel.factory(container, categoryId)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var actionFailed by rememberSaveable { mutableStateOf(false) }

    val title = (state as? BudgetDetailUiState.Loaded)?.categoryName.orEmpty()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = title, onBack = onBack)

        when (val current = state) {
            is BudgetDetailUiState.Loading -> {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
            is BudgetDetailUiState.NotFound -> Message(stringResource(R.string.budget_not_found))
            is BudgetDetailUiState.Error -> Message(stringResource(R.string.budgets_error))
            is BudgetDetailUiState.Loaded -> {
                val budget = current.budget
                val ongoing = budget.status == BudgetStatus.ONGOING
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TermsCard(budget)

                    if (actionFailed) {
                        Text(
                            text = stringResource(R.string.budget_action_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(onClick = onReviewCategory, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.budgets_review_entries))
                    }
                    if (ongoing) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.budget_edit))
                        }
                        OutlinedButton(
                            onClick = { scope.launch { actionFailed = !viewModel.pause() } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.budget_pause))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { scope.launch { actionFailed = !viewModel.resume() } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.budget_resume))
                        }
                    }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.budget_delete))
                    }

                    Text(
                        text = stringResource(R.string.budget_history_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (current.history.isEmpty()) {
                        Text(
                            text = stringResource(R.string.budget_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (month in current.history) HistoryRow(month)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.budget_delete_title)) },
            text = { Text(stringResource(R.string.budget_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            if (viewModel.delete()) onBack() else actionFailed = true
                        }
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun TermsCard(budget: Budget) {
    val startMonth = YearMonth.from(budget.periodStart.atZone(ZoneId.systemDefault()))
    val startLabel = startMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + startMonth.year
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.budget_monthly_limit),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = MoneyFormatter.format(budget.limit),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.budget_alert_at, budget.alertThresholdPercent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(
                    if (budget.status == BudgetStatus.PAUSED) R.string.budget_status_paused
                    else R.string.budget_status_ongoing
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.budget_since, startLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.budget_changes_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun HistoryRow(item: BudgetMonth) {
    val month = item.month
    val label = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year
    val progress = item.progress
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.budgets_percent_used, progress.percentUsed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(
                    R.string.budgets_spent_of,
                    MoneyFormatter.format(progress.spent),
                    MoneyFormatter.format(progress.limit)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            StatusLine(progress)
        }
    }
}
