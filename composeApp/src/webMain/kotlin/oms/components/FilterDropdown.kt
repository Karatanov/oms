package oms.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import oms.localization.LocalizationManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun <T> FilterDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    modifier: Modifier? = null,
    compact: Boolean = false,
    itemLabel: (T) -> String = { it.toString() }
) {

    // A full-width control is much easier to read and tap on a phone.  Table
    // headers pass an explicit modifier, preserving their fixed column width.
    val resolvedModifier = modifier ?: if (oms.layout.LocalMobileLayout.current && !compact) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.widthIn(min = 160.dp, max = 220.dp)
    }
    if (compact) {
        val pickerFocus = remember { FocusRequester() }
        Row(resolvedModifier, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            OmsTooltipBox(
                modifier = Modifier.weight(1f),
                enabled = selected != null && LocalOptionOverlay.current.menu == null,
                tooltip = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                        Text(selected?.let(itemLabel).orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            ) {
                InlineOptionPicker(
                    options = options, selected = selected, prompt = label,
                    onSelect = onSelect, itemLabel = itemLabel,
                    modifier = Modifier.fillMaxWidth().focusRequester(pickerFocus).then(
                        if (selected != null) Modifier.background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f), MaterialTheme.shapes.small
                        ) else Modifier
                    ),
                    fillWidth = true, compact = true,
                    clearLabel = LocalizationManager.t("all"), onClear = { onSelect(null) }
                )
            }
            if (selected != null) {
                val description = "${LocalizationManager.t("reset_filters")}: $label"
                OmsTooltipBox(tooltip = { Text(description) }) {
                    Box(
                        Modifier.size(24.dp).chartControlFocus()
                            .clickable(role = Role.Button) {
                                pickerFocus.requestFocus()
                                onSelect(null)
                            }.buttonHandCursor().semantics { contentDescription = description },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null,
                            modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    } else {
        InlineOptionPicker(
            options = options,
            selected = selected,
            prompt = label,
            onSelect = onSelect,
            itemLabel = itemLabel,
            modifier = resolvedModifier,
            fillWidth = false,
            showSelectionContext = true,
            clearLabel = LocalizationManager.t("all"),
            onClear = { onSelect(null) }
        )
    }
}
