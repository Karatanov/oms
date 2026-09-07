package oms.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import oms.localization.LocalizationManager
import kotlin.js.JsName

@JsName("currentIsoDate")
private external fun browserCurrentIsoDate(): String

@JsName("openNativeDatePicker")
private external fun openNativeDatePicker(value: String, anchorLeft: Float, anchorTop: Float, onSelected: (String) -> Unit)

@JsName("formatOmsDateTime")
private external fun browserFormatOmsDateTime(value: String): String

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
    var anchorLeft by remember { mutableStateOf(16f) }
    var anchorTop by remember { mutableStateOf(16f) }
    // Native pickers are convenient, but dates also need to be usable with a
    // keyboard and pasted from a spreadsheet.  Keep a local display value so
    // an incomplete DD.MM.YYYY value does not overwrite the API-safe ISO one.
    var displayValue by remember { mutableStateOf(value.toOmsDate()) }
    androidx.compose.runtime.LaunchedEffect(value) {
        val formatted = value.toOmsDate()
        if (displayValue != formatted) displayValue = formatted
    }
    OutlinedTextField(
        value = displayValue,
        onValueChange = { rawValue ->
            val sanitized = rawValue.filter { it.isDigit() || it == '.' || it == '-' }.take(10)
            displayValue = sanitized
            sanitized.toIsoDateOrNull()?.let(onValueChange)
            if (sanitized.isBlank()) onValueChange("")
        },
        label = { Text(if (required) "$label *" else label) },
        modifier = modifier,
        readOnly = false,
        singleLine = true,
        trailingIcon = {
            IconButton(
                onClick = { openNativeDatePicker(value, anchorLeft, anchorTop, onValueChange) },
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInWindow()
                    anchorLeft = position.x
                    anchorTop = position.y + coordinates.size.height
                }
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = LocalizationManager.t("choose_date"))
            }
        }
    )
}

private fun String.toIsoDateOrNull(): String? {
    val normalized = trim()
    val ddMmYyyy = Regex("(\\d{2})\\.(\\d{2})\\.(\\d{4})").matchEntire(normalized)
    if (ddMmYyyy != null) {
        val (day, month, year) = ddMmYyyy.destructured
        return "$year-$month-$day"
    }
    return normalized.takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) }
}

fun String?.toOmsDate(): String {
    val value = this.orEmpty()
    val parts = value.split('-')
    return if (parts.size == 3 && parts.all { it.all(Char::isDigit) }) "${parts[2]}.${parts[1]}.${parts[0]}" else value
}

/** Displays a UTC API timestamp in the browser's local timezone and active UI locale. */
fun String?.toOmsDateTime(): String =
    this?.takeIf { it.isNotBlank() }?.let { browserFormatOmsDateTime(it) }.orEmpty()
