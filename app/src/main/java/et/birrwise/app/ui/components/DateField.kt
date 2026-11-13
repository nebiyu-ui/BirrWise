package et.birrwise.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import et.birrwise.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A button that shows a date as year-month-day and opens a date picker when tapped. */
@Composable
fun DateField(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var open by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { open = true },
        enabled = enabled,
        modifier = modifier
    ) {
        Text("$label: $date")
    }

    if (open) {
        DatePickerPopup(
            initial = date,
            onPicked = onDateChange,
            onDismiss = { open = false }
        )
    }
}

/** Like [DateField], but the date may be unset. A Clear button appears once a date is chosen. */
@Composable
fun OptionalDateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val shown = date?.toString() ?: stringResource(R.string.filter_any_date)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.weight(1f)
        ) {
            Text("$label: $shown")
        }
        if (date != null) {
            TextButton(onClick = { onDateChange(null) }) {
                Text(stringResource(R.string.filter_clear_date))
            }
        }
    }

    if (open) {
        DatePickerPopup(
            initial = date ?: LocalDate.now(),
            onPicked = { onDateChange(it) },
            onDismiss = { open = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerPopup(
    initial: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    // The picker works in UTC midnight, so the day is converted in UTC both ways.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = pickerState.selectedDateMillis
                    if (picked != null) {
                        onPicked(Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) {
        DatePicker(state = pickerState)
    }
}
