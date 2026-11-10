package et.birrwise.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import et.birrwise.app.R
import et.birrwise.app.domain.Account
import et.birrwise.app.domain.CategoryType
import et.birrwise.app.domain.EntryKind
import et.birrwise.app.domain.MoneyFormatter
import et.birrwise.app.domain.Transaction
import et.birrwise.app.domain.TransactionFormError
import et.birrwise.app.domain.TransactionFormInput
import et.birrwise.app.domain.TransactionFormValidator
import et.birrwise.app.domain.TransactionType
import et.birrwise.app.ui.accounts.appContainer
import et.birrwise.app.ui.accounts.editableAmount
import et.birrwise.app.ui.components.DateField
import et.birrwise.app.ui.components.DropdownField
import et.birrwise.app.ui.components.ScreenHeader
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun TransactionEditorScreen(
    transactionId: String?,
    presetAccountId: String?,
    onDone: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: TransactionEditorViewModel = viewModel(
        key = "transaction-editor-${transactionId ?: "new"}-${presetAccountId ?: "none"}",
        factory = TransactionEditorViewModel.factory(container, transactionId, presetAccountId)
    )
    val load by viewModel.load.collectAsStateWithLifecycle()
    val saveState by viewModel.save.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var deleteFailed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(saveState) {
        if (saveState is TransactionEditorSaveState.Saved) onDone()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(
                if (transactionId == null) R.string.transaction_title_new else R.string.transaction_title_edit
            ),
            onBack = onDone
        )
        when (val current = load) {
            is TransactionEditorLoadState.Loading -> {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
            is TransactionEditorLoadState.NotFound -> Message(stringResource(R.string.transaction_not_found))
            is TransactionEditorLoadState.Failed -> Message(stringResource(R.string.transaction_load_failed))
            is TransactionEditorLoadState.NoAccounts -> Message(stringResource(R.string.transaction_no_accounts))
            is TransactionEditorLoadState.Ready -> {
                val existing = current.existing
                val editable = existing == null ||
                    (existing.transferGroupId == null && EntryKind.forType(existing.type) != null)
                if (editable) {
                    TransactionForm(
                        ready = current,
                        saveState = saveState,
                        deleteFailed = deleteFailed,
                        onSave = { input -> scope.launch { viewModel.save(input) } },
                        onDelete = {
                            scope.launch {
                                if (viewModel.delete()) onDone() else deleteFailed = true
                            }
                        },
                        onCancel = onDone,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ReadOnlyTransaction(
                        ready = current,
                        deleteFailed = deleteFailed,
                        onDelete = {
                            scope.launch {
                                if (viewModel.delete()) onDone() else deleteFailed = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
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
private fun TransactionForm(
    ready: TransactionEditorLoadState.Ready,
    saveState: TransactionEditorSaveState,
    deleteFailed: Boolean,
    onSave: (TransactionFormInput) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val existing = ready.existing
    val accounts = ready.accounts
    val zone = remember { java.time.ZoneId.systemDefault() }
    val now = remember { java.time.Instant.now() }

    val availableKinds = remember(accounts.size, existing) {
        EntryKind.entries.filter { kind ->
            kind != EntryKind.TRANSFER || (existing == null && accounts.size >= 2)
        }
    }
    val firstAccountId = existing?.accountId
        ?: accounts.firstOrNull { it.id == ready.presetAccountId }?.id
        ?: accounts.first().id

    var kindName by rememberSaveable {
        mutableStateOf((existing?.let { EntryKind.forType(it.type) } ?: EntryKind.EXPENSE).name)
    }
    var accountId by rememberSaveable { mutableStateOf(firstAccountId) }
    var toAccountId by rememberSaveable {
        mutableStateOf(accounts.firstOrNull { it.id != firstAccountId }?.id ?: "")
    }
    var amountText by rememberSaveable {
        mutableStateOf(if (existing != null) editableAmount(existing.amount) else "")
    }
    var feeText by rememberSaveable { mutableStateOf("") }
    var dateEpochDay by rememberSaveable {
        mutableStateOf(
            (existing?.occurredAt?.atZone(zone)?.toLocalDate() ?: LocalDate.now(zone)).toEpochDay()
        )
    }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var counterparty by rememberSaveable { mutableStateOf(existing?.counterparty ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var needsReview by rememberSaveable {
        mutableStateOf(existing?.status == et.birrwise.app.domain.TransactionStatus.PENDING_REVIEW)
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val kind = EntryKind.valueOf(kindName)
    val isTransfer = kind == EntryKind.TRANSFER
    val date = LocalDate.ofEpochDay(dateEpochDay)

    val errors: Set<TransactionFormError> =
        (saveState as? TransactionEditorSaveState.Invalid)?.errors ?: emptySet()

    val wantedCategoryType = if (kind == EntryKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    val categoryOptions = remember(ready.categories, kind) {
        ready.categories.filter { it.type == wantedCategoryType }
    }
    val noneLabel = stringResource(R.string.category_none)
    val categoryText = categoryOptions.firstOrNull { it.id == categoryId }?.name ?: noneLabel

    val selectedAccount: Account? = accounts.firstOrNull { it.id == accountId }
    val resolvedAt = TransactionFormValidator.resolveOccurredAt(date, existing?.occurredAt, now, zone)
    val beforeOpening = selectedAccount != null &&
        TransactionFormValidator.isBeforeOpeningBalance(selectedAccount, resolvedAt)

    val accountOptions = accounts.map { it.id to it.name }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (availableKinds.size > 1) {
            Text(
                text = stringResource(R.string.field_kind),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column(Modifier.selectableGroup()) {
                for (option in availableKinds) {
                    KindRow(
                        label = stringResource(option.labelRes()),
                        selected = option == kind,
                        onSelect = {
                            kindName = option.name
                            categoryId = ""
                        }
                    )
                }
            }
        }

        DropdownField(
            label = stringResource(if (isTransfer) R.string.field_from_account else R.string.field_account),
            selectedText = selectedAccount?.name ?: "",
            options = accountOptions,
            onSelect = { accountId = it },
            errorText = if (TransactionFormError.ACCOUNT_REQUIRED in errors) {
                stringResource(TransactionFormError.ACCOUNT_REQUIRED.messageRes())
            } else null
        )

        if (isTransfer) {
            val toAccount = accounts.firstOrNull { it.id == toAccountId }
            val toError = listOf(
                TransactionFormError.TO_ACCOUNT_REQUIRED,
                TransactionFormError.SAME_ACCOUNT,
                TransactionFormError.CURRENCY_MISMATCH
            ).firstOrNull { it in errors }
            DropdownField(
                label = stringResource(R.string.field_to_account),
                selectedText = toAccount?.name ?: "",
                options = accountOptions,
                onSelect = { toAccountId = it },
                errorText = toError?.let { stringResource(it.messageRes()) }
            )
        }

        val amountError = listOf(
            TransactionFormError.AMOUNT_REQUIRED,
            TransactionFormError.AMOUNT_INVALID,
            TransactionFormError.AMOUNT_ZERO
        ).firstOrNull { it in errors }
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text(stringResource(R.string.field_amount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = amountError != null,
            supportingText = { amountError?.let { Text(stringResource(it.messageRes())) } },
            modifier = Modifier.fillMaxWidth()
        )

        if (isTransfer) {
            OutlinedTextField(
                value = feeText,
                onValueChange = { feeText = it },
                label = { Text(stringResource(R.string.field_transfer_fee)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = TransactionFormError.FEE_INVALID in errors,
                supportingText = {
                    Text(
                        if (TransactionFormError.FEE_INVALID in errors) {
                            stringResource(TransactionFormError.FEE_INVALID.messageRes())
                        } else {
                            stringResource(R.string.field_transfer_fee_hint)
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        DateField(
            label = stringResource(R.string.field_date),
            date = date,
            onDateChange = { dateEpochDay = it.toEpochDay() },
            modifier = Modifier.fillMaxWidth()
        )
        if (beforeOpening) {
            Text(
                text = stringResource(R.string.warning_before_opening),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        if (!isTransfer) {
            DropdownField(
                label = stringResource(R.string.field_category),
                selectedText = categoryText,
                options = listOf("" to noneLabel) + categoryOptions.map { it.id to it.name },
                onSelect = { categoryId = it },
                errorText = if (TransactionFormError.CATEGORY_MISMATCH in errors) {
                    stringResource(TransactionFormError.CATEGORY_MISMATCH.messageRes())
                } else null
            )
        }

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.field_description)) },
            singleLine = true,
            isError = TransactionFormError.DESCRIPTION_TOO_LONG in errors,
            supportingText = {
                if (TransactionFormError.DESCRIPTION_TOO_LONG in errors) {
                    Text(
                        stringResource(
                            R.string.error_description_too_long,
                            TransactionFormValidator.MAX_DESCRIPTION_LENGTH
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = counterparty,
            onValueChange = { counterparty = it },
            label = { Text(stringResource(R.string.field_counterparty)) },
            singleLine = true,
            isError = TransactionFormError.COUNTERPARTY_TOO_LONG in errors,
            supportingText = {
                if (TransactionFormError.COUNTERPARTY_TOO_LONG in errors) {
                    Text(
                        stringResource(
                            R.string.error_counterparty_too_long,
                            TransactionFormValidator.MAX_COUNTERPARTY_LENGTH
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.field_notes)) },
            minLines = 2,
            maxLines = 4,
            isError = TransactionFormError.NOTES_TOO_LONG in errors,
            supportingText = {
                if (TransactionFormError.NOTES_TOO_LONG in errors) {
                    Text(
                        stringResource(
                            R.string.error_notes_too_long,
                            TransactionFormValidator.MAX_NOTES_LENGTH
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.field_needs_review),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.field_needs_review_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = needsReview, onCheckedChange = { needsReview = it })
        }

        if (saveState is TransactionEditorSaveState.Failed) {
            ErrorLine(stringResource(R.string.transaction_save_failed))
        }
        if (deleteFailed) {
            ErrorLine(stringResource(R.string.transaction_delete_failed))
        }

        Button(
            onClick = {
                onSave(
                    TransactionFormInput(
                        kind = kind,
                        accountId = accountId,
                        toAccountId = toAccountId.ifEmpty { null },
                        amountText = amountText,
                        feeText = feeText,
                        date = date,
                        categoryId = categoryId.ifEmpty { null },
                        description = description,
                        counterparty = counterparty,
                        notes = notes,
                        needsReview = needsReview
                    )
                )
            },
            enabled = saveState !is TransactionEditorSaveState.Saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.save))
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
        if (existing != null) {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.transaction_delete))
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirmDelete) {
        DeleteDialog(
            message = stringResource(R.string.transaction_delete_message),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun ReadOnlyTransaction(
    ready: TransactionEditorLoadState.Ready,
    deleteFailed: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val existing: Transaction = ready.existing ?: return
    val accountNames = ready.accounts.associate { it.id to it.name }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val date = existing.occurredAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    val out = ready.transferLegs.firstOrNull { it.type == TransactionType.TRANSFER_OUT }
    val into = ready.transferLegs.firstOrNull { it.type == TransactionType.TRANSFER_IN }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoLine(
            stringResource(R.string.field_kind),
            stringResource(
                if (ready.transferLegs.isNotEmpty()) R.string.type_transfer else existing.type.labelRes()
            )
        )
        InfoLine(
            stringResource(R.string.field_amount),
            MoneyFormatter.format(existing.amount)
        )
        if (out != null && into != null) {
            InfoLine(
                stringResource(R.string.field_from_account),
                accountNames[out.accountId].orEmpty()
            )
            InfoLine(
                stringResource(R.string.field_to_account),
                accountNames[into.accountId].orEmpty()
            )
        } else {
            InfoLine(
                stringResource(R.string.field_account),
                accountNames[existing.accountId].orEmpty()
            )
        }
        InfoLine(stringResource(R.string.field_date), date.toString())
        if (existing.description.isNotEmpty()) {
            InfoLine(stringResource(R.string.field_description), existing.description)
        }
        existing.counterparty?.let { InfoLine(stringResource(R.string.field_counterparty), it) }
        existing.notes?.let { InfoLine(stringResource(R.string.field_notes), it) }

        Text(
            text = stringResource(
                if (ready.transferLegs.isNotEmpty()) R.string.transfer_readonly_note else R.string.transaction_readonly_note
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (deleteFailed) ErrorLine(stringResource(R.string.transaction_delete_failed))

        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    if (ready.transferLegs.isNotEmpty()) R.string.transfer_delete else R.string.transaction_delete
                )
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirmDelete) {
        DeleteDialog(
            message = stringResource(
                if (ready.transferLegs.isNotEmpty()) R.string.transfer_delete_message else R.string.transaction_delete_message
            ),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun DeleteDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transaction_delete_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
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

@Composable
private fun KindRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ErrorLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}
