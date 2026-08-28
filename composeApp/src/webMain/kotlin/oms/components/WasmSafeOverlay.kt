package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import oms.localization.LocalizationManager

/** Page-level dialog replacement that does not use the unstable Wasm browser popup layer. */
@Composable
fun WasmSafeOverlay(onDismiss: (() -> Unit)? = null, errorMessage: String? = null, content: @Composable () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .focusRequester(requester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && onDismiss != null) {
                    onDismiss(); true
                } else false
            }.focusable(),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null, onClick = { onDismiss?.invoke() }))
        Column(Modifier.padding(20.dp).widthIn(max = 960.dp)
            .semantics { paneTitle = LocalizationManager.t("edit_dialog") },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f, fill = false)) { content() }
            errorMessage?.let { ContentState(it, error = true) }
        }
    }
}
