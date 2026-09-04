package oms.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch

/** Text search selector for large option sets. The canonical value changes only after selection. */
@Composable
fun <T> SearchableOptionPicker(
    options: List<T>,
    selected: T?,
    label: String,
    onSelect: (T) -> Unit,
    itemLabel: (T) -> String = { it.toString() },
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxSuggestions: Int = 50,
    loadOptionsOnInput: (suspend () -> List<T>)? = null
) {
    val host = LocalOptionOverlay.current
    val id = remember { Any() }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var query by remember { mutableStateOf(selected?.let(itemLabel).orEmpty()) }
    var availableOptions by remember { mutableStateOf(options) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selected) { query = selected?.let(itemLabel).orEmpty() }
    LaunchedEffect(options) { availableOptions = options }
    DisposableEffect(id) { onDispose { host.dismiss(id) } }

    fun showMatches(entered: String, source: List<T>) {
        val matches = source.asSequence()
            .filter { entered.isNotBlank() && itemLabel(it).contains(entered, ignoreCase = true) }
            .take(maxSuggestions)
            .toList()
        if (matches.isEmpty()) {
            host.dismiss(id)
        } else {
            host.menu = OptionMenu(
                id = id,
                anchor = anchor,
                labels = matches.map(itemLabel),
                selectedIndex = 0,
                onChoose = { index ->
                    val choice = matches[index]
                    query = itemLabel(choice)
                    host.dismiss(id)
                    onSelect(choice)
                },
                onDismiss = { host.dismiss(id) },
                focusOnOpen = false
            )
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = { entered ->
            query = entered
            if (loadOptionsOnInput == null) {
                showMatches(entered, availableOptions)
            } else {
                scope.launch {
                    availableOptions = loadOptionsOnInput()
                    showMatches(entered, availableOptions)
                }
            }
        },
        label = { Text(label) },
        placeholder = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = modifier.fillMaxWidth()
            .onGloballyPositioned { anchor = it.boundsInRoot() }
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && host.menu?.id === id -> {
                        host.menu = host.menu?.copy(focusOnOpen = true)
                        true
                    }
                    event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                        host.dismiss(id)
                        true
                    }
                    else -> false
                }
            }
    )
}
