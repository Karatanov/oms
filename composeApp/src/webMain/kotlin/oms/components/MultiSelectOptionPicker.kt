package oms.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager
import oms.theme.OmsDimensions

/**
 * A compact checkbox dropdown backed by the shared in-canvas overlay. It keeps
 * the menu open while values are toggled, avoiding page-layout jumps and native
 * browser popup differences on mobile and Safari.
 */
@Composable
fun <T> MultiSelectOptionPicker(
    options: List<T>,
    selected: Set<T>,
    prompt: String,
    onSelectionChange: (Set<T>) -> Unit,
    itemLabel: (T) -> String = { it.toString() },
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    enabled: Boolean = true,
    clearLabel: String = LocalizationManager.t("clear_selection")
) {
    val host = LocalOptionOverlay.current
    val id = remember { Any() }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val expanded = host.menu?.id === id
    val restoreFocus = remember { FocusRequester() }

    fun selectedIndexes(values: Set<T>) = options.mapIndexedNotNull { index, option ->
        (index + 1).takeIf { option in values }
    }.toSet()

    fun updateSelection(values: Set<T>) {
        onSelectionChange(values)
        host.menu?.takeIf { it.id === id }?.let { menu ->
            host.menu = menu.copy(selectedIndexes = selectedIndexes(values))
        }
    }

    fun showMenu() {
        host.menu = OptionMenu(
            id = id,
            anchor = anchor,
            labels = listOf(clearLabel) + options.map(itemLabel),
            selectedIndex = 0,
            selectedIndexes = selectedIndexes(selected),
            multiSelect = true,
            clearIndex = 0,
            onChoose = { index ->
                if (index == 0) {
                    updateSelection(emptySet())
                } else {
                    val option = options[index - 1]
                    updateSelection(selected.toMutableSet().apply {
                        if (!add(option)) remove(option)
                    })
                }
            },
            onDismiss = { host.dismiss(id); restoreFocus.requestFocus() }
        )
    }

    LaunchedEffect(options, selected) {
        host.menu?.takeIf { it.id === id }?.let { menu ->
            host.menu = menu.copy(selectedIndexes = selectedIndexes(selected))
        }
    }
    DisposableEffect(id, enabled) { onDispose { host.dismiss(id) } }

    val displayLabel = when (selected.size) {
        0 -> prompt
        1 -> selected.firstOrNull()?.let(itemLabel) ?: prompt
        else -> "$prompt (${selected.size})"
    }
    OutlinedButton(
        enabled = enabled,
        modifier = modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = OmsDimensions.ControlHeight)
            .onGloballyPositioned { anchor = it.boundsInRoot() }
            .semantics { stateDescription = LocalizationManager.t(if (expanded) "expanded" else "collapsed") }
            .focusRequester(restoreFocus),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        onClick = { if (expanded) host.dismiss(id) else showMenu() }
    ) {
        Text(displayLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = fillWidth), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Icon(
            if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
            null,
            Modifier.width(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
