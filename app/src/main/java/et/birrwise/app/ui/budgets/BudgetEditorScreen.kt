package et.birrwise.app.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import et.birrwise.app.R
import et.birrwise.app.domain.BudgetFormError
import et.birrwise.app.domain.BudgetFormInput
import et.birrwise.app.domain.BudgetRules
import et.birrwise.app.domain.CategoryType
import et.birrwise.app.ui.accounts.appContainer
import et.birrwise.app.ui.accounts.editableAmount
import et.birrwise.app.ui.components.DropdownField
import et.birrwise.app.ui.components.ScreenHeader
import kotlinx.coroutines.launch

@Composable
fun BudgetEditorScreen(
    categoryId: String?,
    onDone: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: BudgetEditorViewModel = viewModel(
        key = "budget-editor-${categoryId ?: "new"}",
        factory = BudgetEditorViewModel.factory(container, categoryId)
    )
    val load by viewModel.load.collectAsStateWithLifecycle()
    val saveState by viewModel.save.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(saveState) {
        if (saveState is BudgetEditorSaveState.Saved) onDone()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(
                if (categoryId == null) R.string.budget_title_new else R.string.budget_title_edit
            ),
            onBack = onDone
        )
        when (val current = load) {
            is BudgetEditorLoadState.Loading -> {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
            is BudgetEditorLoadState.NotFound -> Message(stringResource(R.string.budget_not_found))
            is BudgetEditorLoadState.Failed -> Message(stringResource(R.string.budgets_error))
            is BudgetEditorLoadState.Ready -> {
                BudgetForm(
                    ready = current,
                    saveState = saveState,
                    onSave = { input -> scope.launch { viewModel.save(input) } },
                    onCancel = onDone,
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
private fun BudgetForm(
    ready: BudgetEditorLoadState.Ready,
    saveState: BudgetEditorSaveState,
    onSave: (BudgetFormInput) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val existing = ready.existing
    val expenseCategories = ready.allCategories.filter { it.type == CategoryType.EXPENSE }
    val selectable = if (existing == null) {
        expenseCategories.filter { it.id !in ready.categoriesWithBudget }
    } else {
        expenseCategories.filter { it.id == existing.categoryId }
    }

    var categoryId by rememberSaveable {
        mutableStateOf(existing?.categoryId ?: selectable.firstOrNull()?.id ?: "")
    }
    var limitText by rememberSaveable {
        mutableStateOf(if (existing != null) editableAmount(existing.limit) else "")
    }
    var threshold by rememberSaveable {
        mutableStateOf(existing?.alertThresholdPercent ?: BudgetRules.DEFAULT_THRESHOLD_PERCENT)
    }

    val errors: Set<BudgetFormError> = (saveState as? BudgetEditorSaveState.Invalid)?.errors ?: emptySet()
    val categoryError = listOf(
        BudgetFormError.CATEGORY_REQUIRED,
        BudgetFormError.CATEGORY_NOT_EXPENSE,
        BudgetFormError.CATEGORY_HAS_BUDGET
    ).firstOrNull { it in errors }
    val limitError = listOf(
        BudgetFormError.LIMIT_REQUIRED,
        BudgetFormError.LIMIT_INVALID,
        BudgetFormError.LIMIT_ZERO
    ).firstOrNull { it in errors }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (existing == null && selectable.isEmpty()) {
            Text(
                text = stringResource(R.string.budget_no_categories_left),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            DropdownField(
                label = stringResource(R.string.budget_category),
                selectedText = selectable.firstOrNull { it.id == categoryId }?.name ?: "",
                options = selectable.map { it.id to it.name },
                onSelect = { if (existing == null) categoryId = it },
                errorText = categoryError?.let { stringResource(it.messageRes()) }
            )

            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it },
                label = { Text(stringResource(R.string.budget_limit_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = limitError != null,
                supportingText = { limitError?.let { Text(stringResource(it.messageRes())) } },
                modifier = Modifier.fillMaxWidth()
            )

            DropdownField(
                label = stringResource(R.string.budget_alert_label),
                selectedText = stringResource(R.string.budgets_percent_used, threshold),
                options = BudgetRules.THRESHOLD_CHOICES.map {
                    it.toString() to stringResource(R.string.budgets_percent_used, it)
                },
                onSelect = { threshold = it.toIntOrNull() ?: threshold },
                errorText = if (BudgetFormError.THRESHOLD_INVALID in errors) {
                    stringResource(BudgetFormError.THRESHOLD_INVALID.messageRes())
                } else null
            )
            Text(
                text = stringResource(R.string.budget_alert_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    if (existing == null) R.string.budget_starts_note else R.string.budget_changes_note
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (saveState is BudgetEditorSaveState.Failed) {
                Text(
                    text = stringResource(R.string.budget_save_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    onSave(BudgetFormInput(categoryId.ifEmpty { null }, limitText, threshold))
                },
                enabled = saveState !is BudgetEditorSaveState.Saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save))
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
        Spacer(Modifier.height(16.dp))
    }
}
