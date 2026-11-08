package et.birrwise.app.ui.accounts

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import et.birrwise.app.domain.AccountFormError
import et.birrwise.app.domain.AccountFormInput
import et.birrwise.app.domain.AccountFormValidator
import et.birrwise.app.domain.AccountType
import et.birrwise.app.ui.components.DateField
import et.birrwise.app.ui.components.ScreenHeader
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun AccountEditorScreen(
    accountId: String?,
    onDone: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val viewModel: AccountEditorViewModel = viewModel(
        key = "account-editor-${accountId ?: "new"}",
        factory = AccountEditorViewModel.factory(container, accountId)
    )
    val load by viewModel.load.collectAsStateWithLifecycle()
    val saveState by viewModel.save.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(saveState) {
        if (saveState is EditorSaveState.Saved) onDone()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(
                if (accountId == null) R.string.editor_title_new else R.string.editor_title_edit
            ),
            onBack = onDone
        )
        when (val current = load) {
            is EditorLoadState.Loading -> {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
            is EditorLoadState.NotFound -> {
                Text(
                    text = stringResource(R.string.detail_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            is EditorLoadState.Failed -> {
                Text(
                    text = stringResource(R.string.detail_load_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            is EditorLoadState.Ready -> {
                AccountForm(
                    existing = current.existing,
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
private fun AccountForm(
    existing: Account?,
    saveState: EditorSaveState,
    onSave: (AccountFormInput) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zone = remember { ZoneId.systemDefault() }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var provider by rememberSaveable { mutableStateOf(existing?.provider ?: "") }
    var typeName by rememberSaveable { mutableStateOf((existing?.type ?: AccountType.BANK).name) }
    var currency by rememberSaveable { mutableStateOf(existing?.currencyCode ?: "ETB") }
    var amountText by rememberSaveable {
        mutableStateOf(if (existing != null) editableAmount(existing.openingBalance) else "")
    }
    var anyDate by rememberSaveable {
        mutableStateOf(existing != null && existing.openingBalanceDate == null)
    }
    var dateEpochDay by rememberSaveable {
        mutableStateOf(
            (existing?.openingBalanceDate?.atZone(zone)?.toLocalDate() ?: LocalDate.now(zone))
                .toEpochDay()
        )
    }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }

    val errors: Set<AccountFormError> = (saveState as? EditorSaveState.Invalid)?.errors ?: emptySet()

    val nameError: String? = when {
        AccountFormError.NAME_REQUIRED in errors -> stringResource(R.string.error_name_required)
        AccountFormError.NAME_TOO_LONG in errors ->
            stringResource(R.string.error_name_too_long, AccountFormValidator.MAX_NAME_LENGTH)
        AccountFormError.NAME_TAKEN in errors -> stringResource(R.string.error_name_taken)
        else -> null
    }
    val providerError: String? =
        if (AccountFormError.PROVIDER_TOO_LONG in errors) {
            stringResource(R.string.error_provider_too_long, AccountFormValidator.MAX_PROVIDER_LENGTH)
        } else null
    val amountError: String? =
        if (AccountFormError.AMOUNT_INVALID in errors) stringResource(R.string.error_amount_invalid) else null
    val notesError: String? =
        if (AccountFormError.NOTES_TOO_LONG in errors) {
            stringResource(R.string.error_notes_too_long, AccountFormValidator.MAX_NOTES_LENGTH)
        } else null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            isError = nameError != null,
            supportingText = { nameError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = provider,
            onValueChange = { provider = it },
            label = { Text(stringResource(R.string.field_provider)) },
            singleLine = true,
            isError = providerError != null,
            supportingText = { providerError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(R.string.field_type),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(Modifier.selectableGroup()) {
            for (type in AccountType.entries) {
                RadioRow(
                    label = stringResource(type.labelRes()),
                    selected = typeName == type.name,
                    onSelect = { typeName = type.name }
                )
            }
        }

        Text(
            text = stringResource(R.string.field_currency),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(Modifier.selectableGroup()) {
            for (code in SupportedCurrencies) {
                RadioRow(
                    label = code,
                    selected = currency == code,
                    onSelect = { currency = code }
                )
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text(stringResource(R.string.field_opening_balance)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = amountError != null,
            supportingText = {
                Text(amountError ?: stringResource(R.string.field_opening_balance_hint))
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = anyDate,
                    onValueChange = { anyDate = it },
                    role = Role.Checkbox
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = anyDate, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.field_opening_no_date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        DateField(
            label = stringResource(R.string.field_opening_date),
            date = LocalDate.ofEpochDay(dateEpochDay),
            onDateChange = { dateEpochDay = it.toEpochDay() },
            enabled = !anyDate,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.field_notes)) },
            minLines = 2,
            maxLines = 4,
            isError = notesError != null,
            supportingText = { notesError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        when (saveState) {
            is EditorSaveState.Failed -> ErrorText(stringResource(R.string.error_save_failed))
            is EditorSaveState.CurrencyLocked -> ErrorText(stringResource(R.string.error_currency_locked))
            else -> Unit
        }

        Button(
            onClick = {
                onSave(
                    AccountFormInput(
                        name = name,
                        provider = provider,
                        type = AccountType.valueOf(typeName),
                        currencyCode = currency,
                        openingBalanceText = amountText,
                        openingDate = if (anyDate) null else LocalDate.ofEpochDay(dateEpochDay),
                        notes = notes
                    )
                )
            },
            enabled = saveState !is EditorSaveState.Saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.save))
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
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
private fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}
