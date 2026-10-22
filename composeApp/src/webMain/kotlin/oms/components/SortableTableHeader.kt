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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager
import oms.theme.OmsDimensions

@Composable
fun SortableTableHeader(
    title: String,
    selected: Boolean,
    ascending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    numeric: Boolean = false,
    height: Dp = OmsDimensions.TableHeaderHeight,
    maxLines: Int = 2
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(height).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .semantics { stateDescription = LocalizationManager.t(if (!selected) "not_sorted" else if (ascending) "sort_ascending" else "sort_descending") },
        shape = androidx.compose.ui.graphics.RectangleShape,
        contentPadding = PaddingValues(horizontal = if (numeric) 8.dp else 0.dp, vertical = 4.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(title, Modifier.fillMaxWidth().padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge, maxLines = maxLines,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (selected) {
                Icon(
                    if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    null,
                    Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(14.dp)
                )
            }
        }
    }
}
