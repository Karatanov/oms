package oms.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager

/** One compact search style across all workspace pages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSearchField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier.fillMaxWidth()) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val clearSearch: () -> Unit = { onValueChange(""); focusRequester.requestFocus() }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            interactionSource = interactions,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = modifier.heightIn(min = 40.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                .border(if (focused) 2.dp else 1.dp,
                    if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    MaterialTheme.shapes.small)
                .focusRequester(focusRequester).semantics { contentDescription = label }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && value.isNotEmpty()) {
                        clearSearch(); true
                    } else false
                },
            decorationBox = { input ->
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(label, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        input()
                    }
                    if (value.isNotEmpty()) OmsTooltipBox(tooltip = { Text(LocalizationManager.t("clear_search")) }) {
                        IconButton(onClick = clearSearch, modifier = Modifier.size(28.dp).buttonHandCursor()) {
                            Icon(Icons.Default.Close, LocalizationManager.t("clear_search"), Modifier.size(16.dp))
                        }
                    } else Spacer(Modifier.size(28.dp))
                }
            }
        )
    }
}
