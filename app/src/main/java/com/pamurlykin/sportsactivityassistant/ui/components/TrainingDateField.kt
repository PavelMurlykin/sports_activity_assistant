package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneOffset

/** A date is selected only through the calendar, with no text editor or keyboard mode. */
@Composable
fun TrainingDateField(
    date: LocalDate?, label: String, onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    calendarTag: String = "date-calendar", onClear: (() -> Unit)? = null,
) {
    Surface(modifier.fillMaxWidth().semantics(mergeDescendants = true) { if (!enabled) disabled() }
        .clickable(enabled = enabled, role = Role.Button, onClick = onOpenCalendar), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(date?.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")) ?: stringResource(if (onClear != null) R.string.date_unlimited else R.string.add_completed_training_dialog_vybrat_datu),
                    style = MaterialTheme.typography.bodyLarge)
            }
            if (date != null && onClear != null) IconButton(onClick = onClear, enabled = enabled) {
                Icon(Icons.Rounded.Close, stringResource(R.string.date_clear))
            }
            IconButton(onClick = onOpenCalendar, enabled = enabled, modifier = Modifier.testTag(calendarTag)) {
                Icon(Icons.Rounded.CalendarMonth, stringResource(R.string.add_completed_training_dialog_vybrat_datu),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSelected: (LocalDate) -> Unit,
    earliestDate: LocalDate? = null,
) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = 1..9999,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = earliestDate == null ||
                Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() >= earliestDate
        },
    )
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
            onSelected(Instant.ofEpochMilli(requireNotNull(picker.selectedDateMillis)).atZone(ZoneOffset.UTC).toLocalDate())
        }) { Text(stringResource(R.string.add_completed_training_dialog_vybrat)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_completed_training_dialog_otmena)) } }) {
        DatePicker(picker, showModeToggle = false)
    }
}
