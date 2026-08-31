package oms.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import oms.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableActionIconButton(
    tooltip: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    oms.components.OmsTooltipBox(
        tooltip = { Text(tooltip) }
    ) {
        IconButton(onClick = onClick, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
            Icon(icon, contentDescription = tooltip, modifier = Modifier.size(20.dp),
                tint = if (icon == Icons.Default.Delete) MaterialTheme.colorScheme.error else Primary)
        }
    }
}
