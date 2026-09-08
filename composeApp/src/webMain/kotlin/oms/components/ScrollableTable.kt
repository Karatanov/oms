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
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
    var tableTopInRoot by remember { mutableStateOf(0f) }
    var tableTopInPage by remember { mutableStateOf<Float?>(null) }
    var tableHeight by remember { mutableStateOf(0) }
    var headerHeight by remember { mutableStateOf(0) }
    var controlsTopInRoot by remember { mutableStateOf(0f) }
    var controlsTopInPage by remember { mutableStateOf<Float?>(null) }
    var controlsHeight by remember { mutableStateOf(0) }
    var rootHeight by remember { mutableStateOf(0) }
    val bottomInsetPx = with(LocalDensity.current) { 12.dp.toPx() }
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
    // Keep the horizontal scrollbar in the lower part of the visible page,
    // regardless of where the table itself is in the vertical scroll.
    val liveControlsTop = pageScrollState?.let { scrollState ->
        (controlsTopInPage ?: controlsTopInRoot + scrollState.value) - scrollState.value
    } ?: controlsTopInRoot
    val fixedControlsOffset = if (pageScrollState != null && rootHeight > 0 && controlsHeight > 0) {
        rootHeight - controlsHeight - bottomInsetPx - liveControlsTop
    } else 0f
    // A fixed table navigator must not overlap chart navigators while the
    // user is still above the table. It becomes visible only for the table
    // currently being read.
    val tableIsInViewport = rootHeight == 0 ||
        (liveTableTop < rootHeight && liveTableTop + tableHeight > 0)
    val surface = MaterialTheme.colorScheme.surface

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
                // Content owns the actual horizontal ScrollState.  The header
                // follows that state as a layer so it cannot steal or reset
                // the Projects table's horizontal scrolling range.
                Column(Modifier.graphicsLayer { translationX = -scroll.value.toFloat() }, content = header)
            }
            Column(Modifier.fillMaxWidth().horizontalScroll(scroll), content = content)
        }
        if (showScrollControls && tableIsInViewport) {
            Box(
                Modifier.fillMaxWidth()
                    .background(surface)
                    .zIndex(5f)
                    .graphicsLayer { translationY = fixedControlsOffset }
                    .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    controlsTopInRoot = bounds.top
                    // boundsInRoot includes graphicsLayer translation. Updating
                    // the stored base position after the navigator was moved
                    // feeds that translated value back into fixedControlsOffset
                    // and makes the control oscillate on Web/Wasm.
                    if (controlsTopInPage == null) {
                        controlsTopInPage = bounds.top + (pageScrollState?.value ?: 0)
                    }
                        controlsHeight = coordinates.size.height
                        rootHeight = coordinates.findRootCoordinates().size.height
                    }
            ) { TableScrollControls(scroll) }
        }
    }
}

@Composable
fun TableScrollControls(scroll: ScrollState) {
    // Charts and tables which fit on screen do not need a dead 0% navigator.
    // ScrollState.maxValue is observable, so this recomposes once Web/Wasm
    // finishes measuring the actual content width.
    if (scroll.maxValue <= 0) return
    val percentage = if (scroll.maxValue > 0) {
        ((scroll.value.toFloat() / scroll.maxValue.toFloat()) * 100).toInt()
    } else 0
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HoldToScrollButton(
            tooltip = LocalizationManager.t("scroll_table_left"),
            icon = Icons.Default.KeyboardArrowLeft,
            scrollState = scroll,
            direction = -1,
            clickDistance = 500f,
            continuousPixelsPerFrame = 10f
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
            continuousPixelsPerFrame = 10f
        )
    }
}
