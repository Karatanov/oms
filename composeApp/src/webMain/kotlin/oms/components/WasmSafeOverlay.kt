package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import oms.localization.LocalizationManager

/** Page-level dialog replacement that does not use the unstable Wasm browser popup layer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WasmSafeOverlay(onDismiss: (() -> Unit)? = null, errorMessage: String? = null, content: @Composable () -> Unit) {
    val requester = remember { FocusRequester() }
    val errorVisibility = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            withFrameNanos { }
            errorVisibility.bringIntoView()
        }
    }
    BoxWithConstraints(
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
        // Forms opened in this layer can be taller than a phone viewport.  A
        // scrolling dialog keeps its actions and all fields reachable instead
        // of letting the lower part be clipped by the browser viewport.
        Column(
            Modifier.align(if (maxHeight < 480.dp) Alignment.TopCenter else Alignment.Center)
                .padding(if (maxWidth < 600.dp) 8.dp else 12.dp)
                .widthIn(max = 960.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState())
            .semantics { paneTitle = LocalizationManager.t("edit_dialog") },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth()) { content() }
            errorMessage?.let { message ->
                Box(Modifier.fillMaxWidth().bringIntoViewRequester(errorVisibility)) {
                    ContentState(message, error = true)
                }
            }
        }
    }
}
