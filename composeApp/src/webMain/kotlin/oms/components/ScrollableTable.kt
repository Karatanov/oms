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
    val surface = MaterialTheme.colorScheme.surface

    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    tableTopInRoot = bounds.top
                    tableTopInPage = bounds.top + (pageScrollState?.value ?: 0)
                    tableHeight = coordinates.size.height
                }
                .horizontalScroll(scroll)
        ) {
            Column(
                Modifier
                    .zIndex(2f)
                    .graphicsLayer { translationY = stickyOffset }
                    .background(surface)
                    .then(if (stickyOffset > 0f) Modifier.shadow(3.dp) else Modifier)
                    .onGloballyPositioned { headerHeight = it.size.height },
                content = header
            )
            Column(content = content)
        }
        if (showScrollControls) {
            Box(
                Modifier.fillMaxWidth()
                    .background(surface)
                    .zIndex(5f)
                    .graphicsLayer { translationY = fixedControlsOffset }
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        controlsTopInRoot = bounds.top
                        controlsTopInPage = bounds.top + (pageScrollState?.value ?: 0)
                        controlsHeight = coordinates.size.height
                        rootHeight = coordinates.findRootCoordinates().size.height
                    }
            ) { TableScrollControls(scroll) }
        }
    }
}

@Composable
fun TableScrollControls(scroll: ScrollState) {
    if (scroll.maxValue <= 0) return
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
            "${((scroll.value.toFloat() / scroll.maxValue.toFloat()) * 100).toInt()}%",
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
