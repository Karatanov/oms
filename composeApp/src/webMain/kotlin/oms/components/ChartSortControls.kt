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
        OmsTooltipBox(tooltip = { Text(LocalizationManager.t("chart_sort_alphabetically")) }) {
            SingleChoiceSegmentedButtonRow {
                listOf(ChartSortCriterion.Label, ChartSortCriterion.Value).forEachIndexed { index, option ->
                    val label = if (option == ChartSortCriterion.Label) "A–Z" else "123"
                    val tooltipKey = if (option == ChartSortCriterion.Label) "chart_sort_alphabetically" else "chart_sort_by_value"
                    SegmentedButton(
                        selected = criterion == option,
                        onClick = { onCriterionChange(option) },
                        modifier = Modifier.height(28.dp).buttonHandCursor().semantics { contentDescription = LocalizationManager.t(tooltipKey) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        icon = {},
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
        Spacer(Modifier.width(2.dp))
        val directionKey = if (ascending) "sort_ascending" else "sort_descending"
        OmsTooltipBox(tooltip = { Text(LocalizationManager.t(directionKey)) }) {
            IconButton(
                onClick = onDirectionToggle,
                modifier = Modifier.size(30.dp).buttonHandCursor().semantics { contentDescription = LocalizationManager.t(directionKey) }
            ) {
                Icon(
                    if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
