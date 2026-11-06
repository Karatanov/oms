package oms.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import oms.localization.LocalizationManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class TooltipEntry(
    val id: Any,
    val bounds: Rect,
    val content: @Composable () -> Unit,
    val onDismiss: (() -> Unit)? = null
)
private class TooltipOverlayState {
    var entry by mutableStateOf<TooltipEntry?>(null)
    fun dismiss(id: Any) { if (entry?.id === id) entry = null }
}
private val LocalTooltipOverlay = staticCompositionLocalOf<TooltipOverlayState> { error("TooltipOverlayHost is required") }

/** Render tooltips in the same canvas hierarchy; native Wasm Popup positioning can crash navigation. */
@Composable
fun TooltipOverlayHost(content: @Composable () -> Unit) {
    val state = remember { TooltipOverlayState() }
    CompositionLocalProvider(LocalTooltipOverlay provides state) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            content()
            state.entry?.let { tooltip ->
                val density = LocalDensity.current
                val width = 300.dp.coerceAtMost((maxWidth - 16.dp).coerceAtLeast(1.dp))
                val x = with(density) { tooltip.bounds.left.toDp() }
                    .coerceIn(8.dp, (maxWidth - width - 8.dp).coerceAtLeast(8.dp))
                val bottom = with(density) { tooltip.bounds.bottom.toDp() }
                val top = with(density) { tooltip.bounds.top.toDp() }
                // A generic tooltip fits in 164dp, but the project-status
                // explanation deliberately lists every supported state. Give
                // long content the larger side of the viewport and make its
                // body scrollable instead of clipping it at the card edge.
                val maxTooltipHeight = minOf(300.dp, (maxHeight - 16.dp).coerceAtLeast(1.dp))
                val belowSpace = (maxHeight - bottom - 8.dp).coerceAtLeast(1.dp)
                val aboveSpace = (top - 8.dp).coerceAtLeast(1.dp)
                val below = belowSpace >= 172.dp || belowSpace >= aboveSpace
                val availableHeight = (if (below) belowSpace else aboveSpace).coerceAtMost(maxTooltipHeight)
                val y = if (below) bottom + 6.dp else (top - availableHeight - 6.dp).coerceAtLeast(8.dp)
                Surface(
                    Modifier.offset(x, y).widthIn(max = width).heightIn(max = availableHeight)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && tooltip.onDismiss != null) {
                                tooltip.onDismiss.invoke()
                                true
                            } else false
                        },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 4.dp
                ) {
                    Column {
                        tooltip.onDismiss?.let { dismiss ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = dismiss) {
                                    Icon(Icons.Default.Close, LocalizationManager.t("close"), Modifier.size(18.dp))
                                }
                            }
                        }
                        Box(
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                .verticalScroll(rememberScrollState())
                        ) { tooltip.content() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OmsTooltipBox(
    modifier: Modifier = Modifier,
    tooltip: @Composable () -> Unit,
    openOnPress: Boolean = false,
    forceOpen: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val host = LocalTooltipOverlay.current
    val id = remember { Any() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var hovered by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var pinned by remember { mutableStateOf(false) }
    val currentTooltip by rememberUpdatedState(tooltip)
    fun dismiss() {
        pinned = false
        hovered = false
        focused = false
        host.dismiss(id)
    }
    LaunchedEffect(enabled, hovered, focused, pinned, forceOpen, bounds) {
        host.dismiss(id)
        if (enabled && (hovered || focused || pinned || forceOpen)) {
            if (!pinned && !forceOpen) delay(450)
            host.entry = TooltipEntry(
                id = id,
                bounds = bounds,
                content = { currentTooltip() },
                onDismiss = if (pinned) { { dismiss() } } else null
            )
        }
    }
    DisposableEffect(id) { onDispose { host.dismiss(id) } }
    Box(modifier
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || !enabled) false
            else when {
                event.key == Key.Escape -> {
                    dismiss()
                    true
                }
                openOnPress && (event.key == Key.Enter || event.key == Key.Spacebar) -> {
                    pinned = !pinned
                    true
                }
                else -> false
            }
        }
        .then(if (openOnPress && enabled) Modifier.semantics {
            role = Role.Button
            onClick(label = LocalizationManager.t("show_full_text")) {
                pinned = !pinned
                true
            }
        } else Modifier)
        .onGloballyPositioned { if (it.isAttached) bounds = it.boundsInRoot() }
        .onPointerEvent(PointerEventType.Enter) { hovered = true }
        .onPointerEvent(PointerEventType.Exit) { hovered = false }
        .onPointerEvent(PointerEventType.Press) {
            if (openOnPress) pinned = !pinned
            else { hovered = false; host.dismiss(id) }
        }
        .onFocusChanged { focused = it.hasFocus }
        .focusable(enabled = openOnPress && enabled)
    ) { content() }
}
