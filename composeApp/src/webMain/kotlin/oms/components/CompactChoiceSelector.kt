package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager

/** Shared compact single-choice controls for toolbars and forms. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> CompactChoiceSelector(
    options: List<T>, selected: T, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, itemLabel: (T) -> String
) {
    FlowRow(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { option ->
            val active = selected == option
            Box(
                Modifier.heightIn(min = 32.dp).clip(MaterialTheme.shapes.extraSmall)
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                    .chartControlFocus().selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(option) })
                    .buttonHandCursor().padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(itemLabel(option), style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TrancheSelector(selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    CompactChoiceSelector(listOf<Int?>(null, 1, 2), selected, onSelect, modifier) {
        LocalizationManager.t(when (it) { 1 -> "tranche_a"; 2 -> "tranche_b"; else -> "all_tranches" })
    }
}
