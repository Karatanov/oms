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
import kotlinx.coroutines.launch
import oms.localization.LocalizationManager

/** One viewport with a sticky header and a draggable horizontal scrollbar. */
@Composable
fun ScrollableTable(
    modifier: Modifier = Modifier,
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
        TableScrollControls(scroll)
    }
}

@Composable
fun TableScrollControls(scroll: ScrollState) {
    if (scroll.maxValue <= 0) return
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TableActionIconButton(LocalizationManager.t("scroll_table_left"), Icons.Default.KeyboardArrowLeft) {
            scope.launch { scroll.animateScrollTo((scroll.value - 500).coerceAtLeast(0)) }
        }
        Slider(value = scroll.value.toFloat(), onValueChange = { value -> scope.launch { scroll.scrollTo(value.toInt()) } },
            valueRange = 0f..scroll.maxValue.toFloat(),
            modifier = Modifier.weight(1f).semantics { contentDescription = LocalizationManager.t("table_scroll") })
        TableActionIconButton(LocalizationManager.t("scroll_table_right"), Icons.Default.KeyboardArrowRight) {
            scope.launch { scroll.animateScrollTo((scroll.value + 500).coerceAtMost(scroll.maxValue)) }
        }
    }
}
