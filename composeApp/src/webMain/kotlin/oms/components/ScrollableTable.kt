package oms.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import oms.localization.LocalizationManager

/** One viewport with a sticky header and a permanently visible horizontal scrollbar. */
@Composable
fun ScrollableTable(
    modifier: Modifier = Modifier,
    showScrollControls: Boolean = true,
    pageScrollState: ScrollState? = null,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    val navigatorOverlay = LocalTableScrollOverlay.current
    val navigatorId = remember { Any() }
    var tableTopInRoot by remember { mutableStateOf(0f) }
    var tableTopInPage by remember { mutableStateOf<Float?>(null) }
    var tableHeight by remember { mutableStateOf(0) }
    var headerHeight by remember { mutableStateOf(0) }
    // Browser/Wasm scrolling is applied as a layer transform, so layout
    // coordinates alone can remain stale while the page moves.  Keep the
    // table's stable page position and derive its live position from the
    // actual ScrollState instead.
    val liveTableTop = pageScrollState?.let { scrollState ->
        (tableTopInPage ?: tableTopInRoot + scrollState.value) - scrollState.value
    } ?: tableTopInRoot
    val stickyOffset = (-liveTableTop)
        .coerceAtLeast(0f)
        .coerceAtMost((tableHeight - headerHeight).coerceAtLeast(0).toFloat())
    val surface = MaterialTheme.colorScheme.surface

    DisposableEffect(navigatorId, showScrollControls) {
        if (showScrollControls) navigatorOverlay.show(navigatorId, scroll)
        onDispose { navigatorOverlay.dismiss(navigatorId) }
    }

    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    tableTopInRoot = bounds.top
                    // Capture the document position once. Replacing it on
                    // every browser-scroll frame cancels the sticky offset.
                    if (tableTopInPage == null || (pageScrollState?.value ?: 0) == 0) {
                        tableTopInPage = bounds.top + (pageScrollState?.value ?: 0)
                    }
                    tableHeight = coordinates.size.height
                }
        ) {
            // The header itself must live outside the clipped horizontal-scroll
            // content.  Translating a child of horizontalScroll works on JVM,
            // but Web/Wasm clips it as soon as the page moves.  Keeping this
            // outer layer separate makes it a real sticky header on the page.
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(2f)
                    .graphicsLayer { translationY = stickyOffset }
                    .clipToBounds()
                    .background(surface)
                    .then(if (stickyOffset > 0f) Modifier.shadow(3.dp) else Modifier)
                    .onGloballyPositioned { headerHeight = it.size.height }
            ) {
                // Use the very same scroll state as the body. A translated
                // layer works for narrow headers, but Web Compose can clip its
                // far-right cells (notably Procurement columns after the ID).
                // A true horizontal viewport keeps every header cell aligned
                // with, and reachable alongside, its data column.
                // Header is rendered from the body state but is not an
                // additional gesture target; this avoids competing scroll
                // ranges on wide Web/Wasm tables.
                Column(Modifier.horizontalScroll(scroll, enabled = false), content = header)
            }
            Column(Modifier.fillMaxWidth().horizontalScroll(scroll), content = content)
        }
    }
}

@Composable
fun TableScrollControls(scroll: ScrollState, showWhenStationary: Boolean = false) {
    val maxValue = scroll.maxValue
    // Tables keep their navigator at the bottom of the screen even when a
    // wide viewport temporarily fits every column. Charts retain the compact
    // behaviour by using the default false value.
    if (!showWhenStationary && maxValue <= 0) return
    val canScroll = maxValue > 0
    val percentage = if (canScroll) {
        ((scroll.value.toFloat() / maxValue.toFloat()) * 100).toInt()
    } else 0
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HoldToScrollButton(
            tooltip = LocalizationManager.t("scroll_table_left"),
            icon = Icons.Default.KeyboardArrowLeft,
            scrollState = scroll,
            direction = -1,
            clickDistance = 500f,
            continuousPixelsPerFrame = 10f,
            enabled = canScroll
        )
        Text(
            "$percentage%",
            modifier = Modifier.width(64.dp).semantics { contentDescription = LocalizationManager.t("table_scroll") },
            style = MaterialTheme.typography.labelMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        HoldToScrollButton(
            tooltip = LocalizationManager.t("scroll_table_right"),
            icon = Icons.Default.KeyboardArrowRight,
            scrollState = scroll,
            direction = 1,
            clickDistance = 500f,
            continuousPixelsPerFrame = 10f,
            enabled = canScroll
        )
    }
}
