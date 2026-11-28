package et.birrwise.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import et.birrwise.app.R
import et.birrwise.app.data.backup.BackupSummary
import et.birrwise.app.ui.accounts.appContainer
import et.birrwise.app.ui.components.ScreenHeader
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MIN_PASSPHRASE_LENGTH = 8

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer()
    val viewModel: BackupViewModel = viewModel(factory = BackupViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var protect by rememberSaveable { mutableStateOf(true) }
    // Passphrases are kept only in memory, never in saved state.
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<Int?>(null) }
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var restorePassphrase by remember { mutableStateOf("") }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val chosen = exportPassphrase
        exportPassphrase = null
        if (uri != null) scope.launch { viewModel.export(uri.toString(), chosen) }
    }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch { viewModel.openBackup(uri.toString()) }
    }

    LaunchedEffect(state.message) {
        if (state.message == BackupMessage.EXPORT_DONE) {
            passphrase = ""
            confirmation = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.backup_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoCard(state.lastBackupAt)

            state.message?.let { message ->
                MessageCard(message, state.exportedSummary, onDismiss = viewModel::dismissMessage)
            }

            // ---- create a backup ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.backup_create_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.backup_protect),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.backup_protect_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = protect, onCheckedChange = { protect = it; formError = null })
                    }
                    if (protect) {
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text(stringResource(R.string.backup_passphrase)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmation,
                            onValueChange = { confirmation = it },
                            label = { Text(stringResource(R.string.backup_passphrase_again)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(R.string.backup_passphrase_warning, MIN_PASSPHRASE_LENGTH),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.backup_plain_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    formError?.let {
                        Text(
                            text = stringResource(it, MIN_PASSPHRASE_LENGTH),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val fileName = "birrwise-backup-${LocalDate.now()}.bwbackup"
                            if (!protect) {
                                formError = null
                                exportPassphrase = null
                                createLauncher.launch(fileName)
                            } else if (passphrase.length < MIN_PASSPHRASE_LENGTH) {
                                formError = R.string.backup_passphrase_short
                            } else if (passphrase != confirmation) {
                                formError = R.string.backup_passphrase_mismatch
                            } else {
                                formError = null
                                exportPassphrase = passphrase.toCharArray()
                                createLauncher.launch(fileName)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.backup_create_button))
                    }
                }
            }

            // ---- restore ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.backup_restore_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.backup_restore_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openLauncher.launch(arrayOf("*/*")) }
                    ) {
                        Text(stringResource(R.string.backup_restore_button))
                    }
                }
            }

            if (state.busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (state.askingPassphrase) {
        AlertDialog(
            onDismissRequest = {
                restorePassphrase = ""
                viewModel.cancelRestore()
            },
            title = { Text(stringResource(R.string.backup_enter_passphrase_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_enter_passphrase_message))
                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        label = { Text(stringResource(R.string.backup_passphrase)) },
                        singleLine = true,
                        isError = state.wrongPassphrase,
                        supportingText = {
                            if (state.wrongPassphrase) Text(stringResource(R.string.backup_wrong_passphrase))
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = restorePassphrase.isNotEmpty() && !state.busy,
                    onClick = {
                        val chars = restorePassphrase.toCharArray()
                        restorePassphrase = ""
                        scope.launch { viewModel.submitPassphrase(chars) }
                    }
                ) { Text(stringResource(R.string.backup_open)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        restorePassphrase = ""
                        viewModel.cancelRestore()
                    }
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
            title = { Text(stringResource(R.string.backup_preview_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(
                            R.string.backup_preview_from,
                            formatMillis(preview.summary.createdAt),
                            preview.summary.accounts,
                            preview.summary.transactions,
                            preview.summary.categories,
                            preview.summary.budgets
                        )
                    )
                    Text(
                        text = stringResource(
                            R.string.backup_preview_replace,
                            preview.current.accounts,
                            preview.current.transactions
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { viewModel.confirmRestore() } }) {
                    Text(stringResource(R.string.backup_restore_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRestore() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun InfoCard(lastBackupAt: Long?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.backup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.backup_sensitive),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = if (lastBackupAt == null) {
                    stringResource(R.string.backup_last_never)
                } else {
                    stringResource(R.string.backup_last, formatMillis(lastBackupAt))
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MessageCard(message: BackupMessage, exported: BackupSummary?, onDismiss: () -> Unit) {
    val isError = message != BackupMessage.EXPORT_DONE && message != BackupMessage.RESTORE_DONE
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(message.textRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (message == BackupMessage.EXPORT_DONE && exported != null) {
                Text(
                    text = stringResource(
                        R.string.backup_done_summary,
                        exported.accounts,
                        exported.transactions
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    }
}

private fun BackupMessage.textRes(): Int = when (this) {
    BackupMessage.EXPORT_DONE -> R.string.backup_msg_export_done
    BackupMessage.EXPORT_FAILED -> R.string.backup_msg_export_failed
    BackupMessage.RESTORE_DONE -> R.string.backup_msg_restore_done
    BackupMessage.RESTORE_FAILED -> R.string.backup_msg_restore_failed
    BackupMessage.NOT_A_BACKUP -> R.string.backup_msg_not_a_backup
    BackupMessage.UNSUPPORTED_VERSION -> R.string.backup_msg_unsupported
    BackupMessage.CORRUPT -> R.string.backup_msg_corrupt
    BackupMessage.UNREADABLE -> R.string.backup_msg_unreadable
}

private val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatMillis(millis: Long): String =
    dateTimeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
