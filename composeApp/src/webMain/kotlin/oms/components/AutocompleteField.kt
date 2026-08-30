package oms.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

/** Canonical values are preserved; matching can use either localized name. */
@Composable
fun AutocompleteField(value: String, onValueChange: (String) -> Unit, label: String,
    options: List<Pair<String, String>>, modifier: Modifier = Modifier, placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default) {
    val host = LocalOptionOverlay.current
    val id = remember { Any() }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var query by remember(value, options) { mutableStateOf(options.firstOrNull { it.first == value }?.second ?: value) }
    DisposableEffect(id) { onDispose { host.dismiss(id) } }
    OutlinedTextField(query, { entered ->
        query = entered
        onValueChange(entered)
        val matches = options.filter { entered.isNotBlank() && (it.first.contains(entered, true) || it.second.contains(entered, true)) }
        if (matches.isEmpty()) host.dismiss(id) else host.menu = OptionMenu(id, anchor, matches.map { it.second }, 0,
            onChoose = { index -> host.dismiss(id); query = matches[index].second; onValueChange(matches[index].first) },
            onDismiss = { host.dismiss(id) }, focusOnOpen = false)
    }, label = { Text(label) }, placeholder = placeholder?.let { { Text(it) } }, singleLine = true,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth().onGloballyPositioned { anchor = it.boundsInRoot() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && host.menu?.id === id) {
                    host.menu = host.menu?.copy(focusOnOpen = true); true
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    host.dismiss(id); true
                } else false
            })
}
