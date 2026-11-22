package et.birrwise.app.ui.budgets

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import et.birrwise.app.R
import et.birrwise.app.domain.BudgetLine
import et.birrwise.app.domain.BudgetOverview
import et.birrwise.app.domain.BudgetProgress
import et.birrwise.app.domain.MoneyFormatter
import et.birrwise.app.navigation.TopLevelDestination
import et.birrwise.app.ui.accounts.appContainer
import et.birrwise.app.ui.components.EmptyState
import et.birrwise.app.ui.components.MonthSelector
import et.birrwise.app.ui.components.ScreenHeader
import et.birrwise.app.ui.icon
import et.birrwise.app.ui.theme.LocalFinanceColors
import java.time.YearMonth

@Composable
fun BudgetsScreen(
    onAddBudget: () -> Unit,
    onOpenBudget: (String) -> Unit,
    onReviewCategory: (String, YearMonth) -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: BudgetsViewModel = viewModel(factory = BudgetsViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            is BudgetsUiState.Loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            is BudgetsUiState.Error -> {
                EmptyState(
                    icon = TopLevelDestination.BUDGETS.icon(),
                    title = stringResource(R.string.nav_budgets),
                    message = stringResource(R.string.budgets_error)
                )
            }
            is BudgetsUiState.Loaded -> {
                val overview = current.overview
                if (!overview.hasAnyBudget) {
                    EmptyState(
                        icon = TopLevelDestination.BUDGETS.icon(),
                        title = stringResource(R.string.budgets_empty_title),
                        message = stringResource(R.string.budgets_empty_message),
                        actionLabel = stringResource(R.string.budgets_add),
                        onAction = onAddBudget
                    )
                } else {
                    BudgetsContent(
                        overview = overview,
                        canGoNext = current.canGoNext,
                        onPreviousMonth = viewModel::previousMonth,
                        onNextMonth = viewModel::nextMonth,
                        onOpenBudget = onOpenBudget,
                        onReviewCategory = onReviewCategory
                    )
                    ExtendedFloatingActionButton(
                        onClick = onAddBudget,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.budgets_add)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetsContent(
    overview: BudgetOverview,
    canGoNext: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenBudget: (String) -> Unit,
    onReviewCategory: (String, YearMonth) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader(title = stringResource(R.string.nav_budgets)) }
        item {
            MonthSelector(
                month = overview.month,
                canGoNext = canGoNext,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth
            )
        }

        val total = overview.total
        if (total == null) {
            item {
                Text(
                    text = stringResource(R.string.budgets_none_this_month),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item { TotalCard(overview, total) }
        }

        items(overview.lines, key = { it.budget.id }) { line ->
            BudgetLineCard(
                line = line,
                onClick = { onOpenBudget(line.budget.categoryId) },
                onReview = { onReviewCategory(line.budget.categoryId, overview.month) }
            )
        }

        if (overview.paused.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.budgets_paused_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(overview.paused, key = { "paused-" + it.budget.id }) { paused ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBudget(paused.budget.categoryId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = paused.categoryName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.budgets_paused_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalCard(overview: BudgetOverview, total: BudgetProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.budgets_total_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(
                    R.string.budgets_spent_of,
                    MoneyFormatter.format(total.spent),
                    MoneyFormatter.format(total.limit)
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            ProgressBar(total)
            Spacer(Modifier.height(8.dp))
            StatusLine(total)

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.budgets_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (overview.pendingExcluded > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.budgets_pending_note, overview.pendingExcluded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (overview.unbudgetedSpending.isPositive) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.budgets_unbudgeted_note,
                        MoneyFormatter.format(overview.unbudgetedSpending)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (overview.unreadableTransactions > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.accounts_unreadable_warning, overview.unreadableTransactions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun BudgetLineCard(line: BudgetLine, onClick: () -> Unit, onReview: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = line.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.budgets_percent_used, line.progress.percentUsed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(
                    R.string.budgets_spent_of,
                    MoneyFormatter.format(line.progress.spent),
                    MoneyFormatter.format(line.progress.limit)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            ProgressBar(line.progress)
            Spacer(Modifier.height(8.dp))
            StatusLine(line.progress)
            TextButton(onClick = onReview) {
                Text(stringResource(R.string.budgets_review_entries))
            }
        }
    }
}

@Composable
internal fun ProgressBar(progress: BudgetProgress, modifier: Modifier = Modifier) {
    val fraction = progress.percentUsed.coerceIn(0, 100) / 100f
    val barColor: Color =
        if (progress.isOverBudget) LocalFinanceColors.current.expense
        else MaterialTheme.colorScheme.primary
    LinearProgressIndicator(
        progress = { fraction },
        modifier = modifier.fillMaxWidth(),
        color = barColor,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
}

/** Says in words how a budget stands, so it never relies on color alone. */
@Composable
internal fun StatusLine(progress: BudgetProgress) {
    val text: String
    val tint: Color
    when {
        progress.isOverBudget -> {
            text = stringResource(R.string.budgets_over_by, MoneyFormatter.format(progress.overspent))
            tint = LocalFinanceColors.current.expense
        }
        progress.thresholdReached -> {
            text = stringResource(R.string.budgets_near_limit, MoneyFormatter.format(progress.remaining))
            tint = MaterialTheme.colorScheme.tertiary
        }
        else -> {
            text = stringResource(R.string.budgets_left, MoneyFormatter.format(progress.remaining))
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (progress.isOverBudget || progress.thresholdReached) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = tint
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = tint
        )
    }
}
