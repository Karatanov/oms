package oms.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun <T> FilterDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier.widthIn(min = 160.dp, max = 220.dp),
    itemLabel: (T) -> String = { it.toString() }
) {

    InlineOptionPicker(
        options = options,
        selected = selected,
        prompt = label,
        onSelect = onSelect,
        itemLabel = itemLabel,
        modifier = modifier,
        fillWidth = false,
        clearLabel = oms.localization.LocalizationManager.t("all"),
        onClear = { onSelect(null) }
    )
}
