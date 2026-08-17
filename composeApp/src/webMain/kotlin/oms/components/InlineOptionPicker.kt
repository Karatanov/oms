package oms.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * WASM-safe selector. Unlike Material DropdownMenu it is composed in the page
 * layout rather than a popup layer, which avoids browser focus/positioning hangs.
 */
@Composable
fun <T> InlineOptionPicker(
    options: List<T>,
    selected: T?,
    prompt: String,
    onSelect: (T) -> Unit,
    itemLabel: (T) -> String = { it.toString() },
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        OutlinedButton(
            enabled = enabled,
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selected?.let(itemLabel) ?: prompt, maxLines = 1)
        }
        if (expanded) {
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                LazyColumn {
                    if (clearLabel != null && onClear != null) {
                        item(key = "clear") {
                            TextButton(
                                onClick = { expanded = false; onClear() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(clearLabel) }
                        }
                    }
                    items(options, key = { "option-${itemLabel(it)}" }) { option ->
                        TextButton(
                            onClick = { expanded = false; onSelect(option) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(itemLabel(option)) }
                    }
                }
            }
        }
    }
}
