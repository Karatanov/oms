package oms.components

import androidx.compose.runtime.Composable


@Composable
fun <T> FilterDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    itemLabel: (T) -> String = { it.toString() }
) {

    InlineOptionPicker(
        options = options,
        selected = selected,
        prompt = label,
        onSelect = onSelect,
        itemLabel = itemLabel,
        clearLabel = oms.localization.LocalizationManager.t("all"),
        onClear = { onSelect(null) }
    )
}
