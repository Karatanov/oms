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
import oms.components.formatGroupedNumber

/** Zero-based plot with a fixed baseline, equal label lanes and accessible tooltips. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun VerticalBarChart(
    data: List<BarData>, color: Color, labelWidth: Dp = 76.dp, labelMaxLines: Int = 2,
    maxVisibleItems: Int? = null, initialScrollToEnd: Boolean = false,
    valueLabel: (Float) -> String = { formatGroupedNumber(it.toLong()) }, onItemClick: ((BarData) -> Unit)? = null,
    showScrollControls: Boolean = true,
    showDataLabels: Boolean = true
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.value }.coerceAtLeast(1f)
    val scroll = rememberScrollState()
    LaunchedEffect(data, initialScrollToEnd, scroll.maxValue) { if (initialScrollToEnd) scroll.scrollTo(scroll.maxValue) }
    val laneHeight = if (labelMaxLines == 1) 24.dp else 52.dp
    // A compact lane and wider bar make dense month/subproject charts easier
    // to scan. The label still owns its lane, so tooltips and click targets
    // remain reliable on touch screens.
    val columnGap = 1.dp
    // Hiding values is also a density control.  Without the value lane each
    // bar can use a narrower column, so more categories fit before the
    // horizontal navigator is needed.
    val effectiveLabelWidth = if (showDataLabels) labelWidth else (labelWidth * .74f)
    val plotWidth = effectiveLabelWidth * data.size + columnGap * (data.size - 1)
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Row(Modifier.width(plotWidth), horizontalArrangement = Arrangement.spacedBy(columnGap)) {
            data.forEach { item ->
                val displayValue = item.formattedValue ?: valueLabel(item.value)
                val tooltip = listOf(item.label + ": " + displayValue, item.tooltip).filterNotNull().joinToString("\n")
                oms.components.OmsTooltipBox(tooltip = { Text(tooltip) }) {
                    Column(Modifier.width(effectiveLabelWidth).semantics { contentDescription = tooltip }
                        .then(
                            if (onItemClick == null) Modifier.focusable()
                            else Modifier.clickable { onItemClick(item) }
                                .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(if (showDataLabels) 154.dp else 128.dp), contentAlignment = Alignment.BottomCenter) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (showDataLabels) {
                                    Text(displayValue, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    Spacer(Modifier.height(6.dp))
                                }
                                Box(Modifier.width(36.dp).height((118f * (item.value.coerceAtLeast(0f) / maxValue)).dp)
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
            Row(horizontalArrangement = Arrangement.spacedBy(columnGap)) {
                groups.forEach { (year, count) ->
                    Text(year.orEmpty(), Modifier.width(effectiveLabelWidth * count + columnGap * (count - 1)),
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
                    modifier = Modifier.width(176.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    TableScrollControls(scroll)
                }
            }
        }
    }
}
