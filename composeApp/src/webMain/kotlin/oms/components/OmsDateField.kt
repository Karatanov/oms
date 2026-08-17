package oms.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import oms.localization.LocalizationManager
import kotlin.js.JsName

@JsName("currentIsoDate")
private external fun browserCurrentIsoDate(): String

@JsName("openNativeDatePicker")
private external fun openNativeDatePicker(value: String, onSelected: (String) -> Unit)

/** The user's local calendar date in the API's YYYY-MM-DD format. */
fun currentIsoDate(): String = browserCurrentIsoDate()

/** Displays dates as DD.MM.YYYY while preserving API-safe ISO YYYY-MM-DD values internally. */
@Composable
fun OmsDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false
) {
    OutlinedTextField(
        value = value.toOmsDate(),
        onValueChange = {},
        label = { Text(if (required) "$label *" else label) },
        modifier = modifier,
        readOnly = true,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { openNativeDatePicker(value, onValueChange) }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = LocalizationManager.t("choose_date"))
            }
        }
    )
}

fun String?.toOmsDate(): String {
    val value = this.orEmpty()
    val parts = value.split('-')
    return if (parts.size == 3 && parts.all { it.all(Char::isDigit) }) "${parts[2]}.${parts[1]}.${parts[0]}" else value
}
