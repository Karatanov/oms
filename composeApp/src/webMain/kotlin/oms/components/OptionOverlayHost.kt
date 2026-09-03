package oms.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager

internal data class OptionMenu(
    val id: Any, val anchor: Rect, val labels: List<String>, val selectedIndex: Int,
    val onChoose: (Int) -> Unit, val onDismiss: () -> Unit, val focusOnOpen: Boolean = true
)
internal class OptionOverlayState {
    var menu by mutableStateOf<OptionMenu?>(null)
    fun dismiss(id: Any) { if (menu?.id === id) menu = null }
}
internal val LocalOptionOverlay = staticCompositionLocalOf<OptionOverlayState> { error("OptionOverlayHost is required") }

/** One in-canvas overlay above pages and dialogs, without native popup focus loops. */
@Composable
fun OptionOverlayHost(content: @Composable () -> Unit) {
    val state = remember { OptionOverlayState() }
    DisposableEffect(state.menu != null) {
        setOmsCanvasOverlay(state.menu != null)
        onDispose { setOmsCanvasOverlay(false) }
    }
    CompositionLocalProvider(LocalOptionOverlay provides state) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            content()
            state.menu?.let { menu ->
                val density = LocalDensity.current
                val margin = 8.dp
                val width = with(density) { menu.anchor.width.toDp() }.coerceAtLeast(240.dp).coerceAtMost((maxWidth - margin * 2).coerceAtLeast(1.dp))
                val left = with(density) { menu.anchor.left.toDp() }.coerceIn(margin, (maxWidth - width - margin).coerceAtLeast(margin))
                val top = with(density) { menu.anchor.top.toDp() }
                val bottom = with(density) { menu.anchor.bottom.toDp() }
                val below = (maxHeight - bottom - margin).coerceAtLeast(0.dp)
                val above = (top - margin).coerceAtLeast(0.dp)
                val desired = (menu.labels.size * 44 + 8).dp.coerceAtMost(320.dp)
                val openBelow = below >= desired || below >= above
                val height = desired.coerceAtMost(if (openBelow) below else above).coerceAtLeast(1.dp)
                val y = if (openBelow) bottom else (top - height).coerceAtLeast(margin)
                val requester = remember(menu.id) { FocusRequester() }
                var active by remember(menu.id) { mutableStateOf(menu.selectedIndex.coerceIn(0, (menu.labels.size - 1).coerceAtLeast(0))) }
                val listState = rememberLazyListState()
                LaunchedEffect(menu.id, menu.focusOnOpen) { if (menu.focusOnOpen) requester.requestFocus() }
                LaunchedEffect(active) { if (menu.labels.isNotEmpty()) listState.animateScrollToItem(active) }
                Box(Modifier.fillMaxSize().clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null, onClickLabel = LocalizationManager.t("close"), onClick = menu.onDismiss))
                Surface(
                    modifier = Modifier.offset(left, y).width(width).heightIn(max = height)
                        .focusRequester(requester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                                Key.Escape -> { menu.onDismiss(); true }
                                Key.DirectionDown -> { active = (active + 1).coerceAtMost((menu.labels.size - 1).coerceAtLeast(0)); true }
                                Key.DirectionUp -> { active = (active - 1).coerceAtLeast(0); true }
                                Key.Enter, Key.NumPadEnter -> { if (menu.labels.isNotEmpty()) menu.onChoose(active); true }
                                Key.Tab -> { menu.onDismiss(); true }
                                else -> false
                            }
                        }.focusable(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
                        if (menu.labels.isEmpty()) item { Text(LocalizationManager.t("no_options"), Modifier.padding(12.dp)) }
                        itemsIndexed(menu.labels, key = { index, _ -> index }) { index, label ->
                            Row(Modifier.fillMaxWidth()
                                .background(if (index == active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .clickable(role = Role.RadioButton) { menu.onChoose(index) }
                                .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
                                .semantics { selected = index == menu.selectedIndex }
                                .heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                if (index == menu.selectedIndex) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
