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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import oms.localization.LocalizationManager

/** One viewport with a sticky header and a draggable horizontal scrollbar. */
@Composable
fun ScrollableTable(
    modifier: Modifier = Modifier,
    showScrollControls: Boolean = true,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    var tableTopInRoot by remember { mutableStateOf(0f) }
    var tableHeight by remember { mutableStateOf(0) }
    var headerHeight by remember { mutableStateOf(0) }
    val stickyOffset = (-tableTopInRoot)
        .coerceAtLeast(0f)
        .coerceAtMost((tableHeight - headerHeight).coerceAtLeast(0).toFloat())
    val surface = MaterialTheme.colorScheme.surface

    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    tableTopInRoot = bounds.top
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
        if (showScrollControls) TableScrollControls(scroll)
    }
}

@Composable
fun TableScrollControls(scroll: ScrollState) {
    if (scroll.maxValue <= 0) return
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
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
