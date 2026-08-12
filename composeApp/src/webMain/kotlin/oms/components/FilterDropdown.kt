package oms.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*


@Composable
fun <T> FilterDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    itemLabel: (T) -> String = { it.toString() }
) {

    var expanded by remember { mutableStateOf(false) }

    Box {

        OutlinedButton(onClick = { expanded = true }) {
            Text(selected?.let(itemLabel) ?: label)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {

            DropdownMenuItem(
                text = { Text(oms.localization.LocalizationManager.t("all")) },
                onClick = {
                    expanded = false
                    onSelect(null)
                }
            )

            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(itemLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
