package oms.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Visible, touch-friendly add action for repeatable form sections. */
@Composable
fun WorkspaceAddButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    WorkspaceOutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, modifier = Modifier.weight(1f, fill = false))
    }
}
