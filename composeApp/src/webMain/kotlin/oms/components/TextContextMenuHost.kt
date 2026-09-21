@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package oms.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItemWithComposableLeadingIcon
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred

private class TextMenuSession(val data: TextContextMenuDataProvider) : TextContextMenuSession {
    val closed = CompletableDeferred<Unit>()
    override fun close() { closed.complete(Unit) }
}

private class TextMenuProvider : TextContextMenuProvider {
    var session by mutableStateOf<TextMenuSession?>(null)
        private set

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        session?.close()
        val current = TextMenuSession(dataProvider)
        session = current
        try { current.closed.await() }
        finally { if (session === current) session = null }
    }
}

/**
 * One in-canvas clipboard menu for every field and selectable text block.
 * Keep the field's own copy/cut/paste callbacks and selection semantics; only
 * replace the platform Popup which owns a separate browser focus layer.
 * Compose 1.10 exposes the provider API but its actionable item is internal.
 * Keep that version-specific dependency isolated here until it becomes public.
 */
@Composable
fun TextContextMenuHost(content: @Composable () -> Unit) {
    val provider = remember { TextMenuProvider() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(provider) { onDispose { provider.session?.close() } }
    CompositionLocalProvider(
        LocalTextContextMenuDropdownProvider provides provider,
        LocalTextContextMenuToolbarProvider provides provider
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()
            .onPreviewKeyEvent {
                if (provider.session != null && it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                    provider.session?.close(); true
                } else false
            }
            .onGloballyPositioned { coordinates = it }) {
            content()
            val session = provider.session
            val anchor = coordinates
            if (session != null && anchor != null && anchor.isAttached) {
                val items = session.data.data().components
                    .filterIsInstance<TextContextMenuItemWithComposableLeadingIcon>()
                val density = LocalDensity.current
                val position = session.data.position(anchor)
                val width = 220.dp.coerceAtMost(maxWidth)
                val height = (items.size * 44 + 8).dp.coerceAtMost(maxHeight)
                val x = with(density) { position.x.toDp() }.coerceIn(0.dp, maxWidth - width)
                val y = with(density) { position.y.toDp() }.coerceIn(0.dp, maxHeight - height)
                Box(Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { session.close() }
                ))
                Surface(
                    modifier = Modifier.offset(x, y).width(width),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                    shadowElevation = 8.dp
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        items.forEach { item ->
                            TextButton(
                                enabled = item.enabled,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                    .pointerHoverIcon(PointerIcon.Hand),
                                onClick = {
                                    // Release the menu before a clipboard permission prompt
                                    // or asynchronous paste can move focus to the browser.
                                    session.close()
                                    item.onClick(session)
                                }
                            ) { Text(item.label) }
                        }
                    }
                }
            }
        }
    }
}
