package com.example.trainerledger.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.trainerledger.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    dateMillis: Long,
    onDateChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier = modifier.clickable { showPicker = true }) {
        OutlinedTextField(
            value = DateUtils.formatDisplay(dateMillis),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Календарь")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
        )
    }
    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = DateUtils.toUtcPicker(dateMillis))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onDateChange(DateUtils.fromUtcPicker(it)) }
                        showPicker = false
                    },
                ) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    from: Long,
    to: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = DateUtils.toUtcPicker(from),
        initialSelectedEndDateMillis = DateUtils.toUtcPicker(to),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis
                    val end = state.selectedEndDateMillis
                    if (start != null && end != null) {
                        onConfirm(DateUtils.fromUtcPicker(start), DateUtils.fromUtcPicker(end))
                    }
                },
            ) { Text("ОК") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    ) {
        DateRangePicker(state = state)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Удалить",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
