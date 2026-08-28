package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager
import oms.theme.OmsDimensions

@Composable
fun SortableTableHeader(title: String, selected: Boolean, ascending: Boolean, onClick: () -> Unit, modifier: Modifier, numeric: Boolean = false) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(OmsDimensions.TableHeaderHeight).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .semantics { stateDescription = LocalizationManager.t(if (!selected) "not_sorted" else if (ascending) "sort_ascending" else "sort_descending") },
        shape = androidx.compose.ui.graphics.RectangleShape,
        contentPadding = PaddingValues(horizontal = if (numeric) 8.dp else 0.dp, vertical = 4.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 2,
                textAlign = if (numeric) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start)
            if (selected) {
                Spacer(Modifier.width(4.dp))
                Icon(if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, Modifier.size(14.dp))
            }
        }
    }
}
