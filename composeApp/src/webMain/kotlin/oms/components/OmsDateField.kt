package oms.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import oms.localization.LocalizationManager
import kotlin.js.JsName

@JsName("formatDateForInput")
private external fun formatDateForInput(epochMillis: Double): String

/** Displays dates as DD.MM.YYYY while preserving API-safe ISO YYYY-MM-DD values internally. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmsDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState()
    OutlinedTextField(
        value = value.toOmsDate(),
        onValueChange = {},
        label = { Text(if (required) "$label *" else label) },
        modifier = modifier,
        readOnly = true,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = LocalizationManager.t("choose_date"))
            }
        }
    )
    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onValueChange(formatDateForInput(it.toDouble())) }
                    showPicker = false
                }) { Text(LocalizationManager.t("save")) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(LocalizationManager.t("cancel")) } }
        ) { DatePicker(state = pickerState) }
    }
}

fun String?.toOmsDate(): String {
    val value = this.orEmpty()
    val parts = value.split('-')
    return if (parts.size == 3 && parts.all { it.all(Char::isDigit) }) "${parts[2]}.${parts[1]}.${parts[0]}" else value
}
