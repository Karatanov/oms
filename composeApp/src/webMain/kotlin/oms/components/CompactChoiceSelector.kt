package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
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
    val focusTargets = remember(options) { options.map { FocusRequester() } }
    FlowRow(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { index, option ->
            val active = selected == option
            Box(
                Modifier.heightIn(min = 32.dp).clip(MaterialTheme.shapes.extraSmall)
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                    .focusRequester(focusTargets[index])
                    .choiceKeyboardNavigation(index, focusTargets) { onSelect(options[it]) }
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

/** Move focus and selection together within a short, exclusive-choice group. */
internal fun Modifier.choiceKeyboardNavigation(
    index: Int,
    focusTargets: List<FocusRequester>,
    onSelect: (Int) -> Unit
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isCtrlPressed || event.isMetaPressed || focusTargets.isEmpty()) {
        false
    } else {
        val target = when (event.key) {
            Key.DirectionRight, Key.DirectionDown -> (index + 1) % focusTargets.size
            Key.DirectionLeft, Key.DirectionUp -> (index + focusTargets.size - 1) % focusTargets.size
            Key.MoveHome -> 0
            Key.MoveEnd -> focusTargets.lastIndex
            else -> null
        }
        if (target == null) false else {
            focusTargets[target].requestFocus()
            if (target != index) onSelect(target)
            true
        }
    }
}
