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
import androidx.compose.ui.platform.LocalDensity
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
    val density = LocalDensity.current.density
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
            onValueChange(sanitized.toIsoDateOrNull() ?: sanitized)
        },
        label = { Text(if (required) "$label *" else label) },
        modifier = modifier,
        readOnly = false,
        singleLine = true,
        isError = displayValue.isNotBlank() && displayValue.toIsoDateOrNull()?.let(::isValidIsoDate) != true,
        supportingText = { if (displayValue.isNotBlank() && displayValue.toIsoDateOrNull()?.let(::isValidIsoDate) != true)
            Text(LocalizationManager.t("date_invalid")) },
        trailingIcon = {
            IconButton(
                onClick = { openNativeDatePicker(value, anchorLeft, anchorTop, onValueChange) },
                modifier = Modifier.buttonHandCursor().onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInWindow()
                    anchorLeft = position.x / density
                    anchorTop = (position.y + coordinates.size.height) / density
                }
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = LocalizationManager.t("choose_date"))
            }
        }
    )
}

fun isValidIsoDate(value: String): Boolean {
    if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) return false
    val (year, month, day) = value.split('-').map { it.toInt() }
    if (year !in 1..9999 || month !in 1..12) return false
    val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val days = listOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    return day in 1..days[month - 1]
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
