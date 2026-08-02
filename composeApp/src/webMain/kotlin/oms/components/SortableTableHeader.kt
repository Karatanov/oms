package oms.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SortableTableHeader(
    title: String,
    selected: Boolean,
    ascending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Row {
            Text(title)
            if (selected) {
                Spacer(Modifier.width(4.dp))
                Icon(if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null)
            }
        }
    }
}
