package oms.components

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import oms.localization.LocalizationManager

/** Small per-chart control for bar-value labels; it deliberately has no global persistence. */
@Composable
fun ChartDataLabelsToggle(showDataLabels: Boolean, onShowDataLabelsChange: (Boolean) -> Unit) {
    val actionKey = if (showDataLabels) "hide_data_labels" else "show_data_labels"
    OmsTooltipBox(tooltip = { Text(LocalizationManager.t(actionKey)) }) {
        IconButton(
            onClick = { onShowDataLabelsChange(!showDataLabels) },
            modifier = Modifier
                .buttonHandCursor()
                .background(
                    if (showDataLabels) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.small
                )
                .semantics { contentDescription = LocalizationManager.t(actionKey) }
        ) {
            Icon(
                imageVector = if (showDataLabels) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = null,
                tint = if (showDataLabels) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
