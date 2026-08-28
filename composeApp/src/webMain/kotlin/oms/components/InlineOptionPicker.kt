package oms.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp

/**
 * WASM-safe selector. The option card is drawn over the page rather than added
 * to the layout flow, so opening it never moves neighbouring controls. It avoids
 * Material DropdownMenu's browser popup layer, which was unstable in Compose/Wasm.
 */
@Composable
fun <T> InlineOptionPicker(
    options: List<T>,
    selected: T?,
    prompt: String,
    onSelect: (T) -> Unit,
    itemLabel: (T) -> String = { it.toString() },
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    enabled: Boolean = true,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val contentWidth = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    Box(modifier) {
        OutlinedButton(
            enabled = enabled,
            onClick = { expanded = !expanded },
            modifier = contentWidth
        ) {
            Text(selected?.let(itemLabel) ?: prompt, maxLines = 1, modifier = Modifier)
            Icon(
                imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = null
            )
        }
        if (expanded) {
            Card(
                modifier = contentWidth
                    .align(Alignment.TopStart)
                    .offset(y = 48.dp)
                    .zIndex(10f)
                    .heightIn(max = 240.dp),
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
                    // Labels are not unique in the project hierarchy (for example a
                    // subproject code can equal its display name).  A label-based key
                    // made LazyColumn unstable and could freeze Compose/Wasm menus.
                    itemsIndexed(options, key = { index, _ -> "option-$index" }) { _, option ->
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
