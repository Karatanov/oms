package oms.components

import androidx.compose.runtime.Composable
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
    itemLabel: (T) -> String = { it.toString() }
) {

    // A full-width control is much easier to read and tap on a phone.  Table
    // headers pass an explicit modifier, preserving their fixed column width.
    val resolvedModifier = modifier ?: if (oms.layout.LocalMobileLayout.current) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.widthIn(min = 160.dp, max = 220.dp)
    }
    InlineOptionPicker(
        options = options,
        selected = selected,
        prompt = label,
        onSelect = onSelect,
        itemLabel = itemLabel,
        modifier = resolvedModifier,
        fillWidth = false,
        clearLabel = oms.localization.LocalizationManager.t("all"),
        onClear = { onSelect(null) }
    )
}
