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
    OutlinedTextField(
        value = value.toOmsDate(),
        onValueChange = {},
        label = { Text(if (required) "$label *" else label) },
        modifier = modifier,
        readOnly = true,
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

fun String?.toOmsDate(): String {
    val value = this.orEmpty()
    val parts = value.split('-')
    return if (parts.size == 3 && parts.all { it.all(Char::isDigit) }) "${parts[2]}.${parts[1]}.${parts[0]}" else value
}

/** Displays a UTC API timestamp in the browser's local timezone and active UI locale. */
fun String?.toOmsDateTime(): String =
    this?.takeIf { it.isNotBlank() }?.let { browserFormatOmsDateTime(it) }.orEmpty()
