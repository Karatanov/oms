package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Shared compact actions for registry rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableActionIconButton(
    tooltip: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val destructive = icon == Icons.Default.Delete
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        OmsTooltipBox(tooltip = { Text(tooltip) }) {
            WorkspaceIconButton(
                onClick = onClick,
                modifier = Modifier.size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.extraSmall)
                    .chartControlFocus(),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(icon, contentDescription = tooltip, modifier = Modifier.size(18.dp))
            }
        }
    }
}
