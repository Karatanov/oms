package oms.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import oms.localization.LocalizationManager

/** Consistent search with a keyboard-accessible, labelled clear action. */
@Composable
fun WorkspaceSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Default.Close, contentDescription = LocalizationManager.t("clear_search"))
            } }
        } else null,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    )
}
