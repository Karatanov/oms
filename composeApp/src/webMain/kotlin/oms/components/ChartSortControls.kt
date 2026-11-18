package oms.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import oms.charts.ChartSortCriterion
import oms.localization.LocalizationManager

/** Compact, reusable category-sort controls for non-chronological charts. */
@Composable
fun ChartSortControls(
    criterion: ChartSortCriterion?,
    ascending: Boolean,
    onCriterionChange: (ChartSortCriterion) -> Unit,
    onDirectionToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(ChartSortCriterion.Label, ChartSortCriterion.Value).forEach { option ->
            val label = if (option == ChartSortCriterion.Label) "A–Z" else "123"
            val description = LocalizationManager.t(if (option == ChartSortCriterion.Label) "chart_sort_alphabetically" else "chart_sort_by_value")
            OmsTooltipBox(tooltip = { Text(description) }) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(width = 32.dp, height = 24.dp)
                        .background(if (criterion == option) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.extraSmall)
                        .selectable(selected = criterion == option, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { onCriterionChange(option) })
                        .buttonHandCursor().semantics { contentDescription = description },
                    contentAlignment = Alignment.Center
                ) { Text(label, style = MaterialTheme.typography.labelSmall) }
            }
        }
        val directionKey = if (ascending) "sort_ascending" else "sort_descending"
        OmsTooltipBox(tooltip = { Text(LocalizationManager.t(directionKey)) }) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(24.dp).clickable(onClick = onDirectionToggle).buttonHandCursor()
                    .semantics { contentDescription = LocalizationManager.t(directionKey) },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
