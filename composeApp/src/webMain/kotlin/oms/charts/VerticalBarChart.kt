package oms.charts

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import oms.components.TableScrollControls

/** Zero-based plot with a fixed baseline, equal label lanes and accessible tooltips. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun VerticalBarChart(
    data: List<BarData>, color: Color, labelWidth: Dp = 88.dp, labelMaxLines: Int = 2,
    maxVisibleItems: Int? = null, initialScrollToEnd: Boolean = false,
    valueLabel: (Float) -> String = { it.toLong().toString() }, onItemClick: ((BarData) -> Unit)? = null,
    showScrollControls: Boolean = true
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.value }.coerceAtLeast(1f)
    val scroll = rememberScrollState()
    LaunchedEffect(data, initialScrollToEnd, scroll.maxValue) { if (initialScrollToEnd) scroll.scrollTo(scroll.maxValue) }
    val laneHeight = if (labelMaxLines == 1) 24.dp else 52.dp
    val plotWidth = labelWidth * data.size + 8.dp * (data.size - 1)
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Row(Modifier.width(plotWidth), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            data.forEach { item ->
                val displayValue = item.formattedValue ?: valueLabel(item.value)
                val tooltip = listOf(item.label + ": " + displayValue, item.tooltip).filterNotNull().joinToString("\n")
                oms.components.OmsTooltipBox(tooltip = { Text(tooltip) }) {
                    Column(Modifier.width(labelWidth).semantics { contentDescription = tooltip }
                        .then(
                            if (onItemClick == null) Modifier.focusable()
                            else Modifier.clickable { onItemClick(item) }
                                .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(154.dp), contentAlignment = Alignment.BottomCenter) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(displayValue, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.width(28.dp).height((118f * (item.value.coerceAtLeast(0f) / maxValue)).dp)
                                    .clip(MaterialTheme.shapes.extraSmall).background(item.color ?: color))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(Modifier.fillMaxWidth().height(laneHeight).padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
                            Text(item.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = labelMaxLines)
                        }
                    }
                }
            }
        }
        if (data.any { it.groupLabel != null }) {
            val groups = mutableListOf<Pair<String?, Int>>()
            data.forEach { item ->
                if (groups.lastOrNull()?.first == item.groupLabel && groups.isNotEmpty()) {
                    val previous = groups.removeAt(groups.lastIndex)
                    groups.add(previous.first to previous.second + 1)
                } else groups.add(item.groupLabel to 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { (year, count) ->
                    Text(year.orEmpty(), Modifier.width(labelWidth * count + 8.dp * (count - 1)),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        }
        // Do not show disabled navigation when every bar already fits in the
        // available width. The navigator becomes visible only after there is
        // actual horizontal content to browse.
        if (showScrollControls && scroll.maxValue > 0) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.width(208.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    TableScrollControls(scroll)
                }
            }
        }
    }
}
