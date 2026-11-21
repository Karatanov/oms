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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
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
    var viewportWidth by remember { mutableStateOf(0) }
    val categoryOrder = data.map { (it.id ?: it.label) to it.groupLabel }
    LaunchedEffect(categoryOrder, initialScrollToEnd) {
        if (!initialScrollToEnd) scroll.scrollTo(0)
    }
    LaunchedEffect(data, initialScrollToEnd, scroll.maxValue) { if (initialScrollToEnd) scroll.scrollTo(scroll.maxValue) }
    val laneHeight = if (labelMaxLines == 1) 18.dp else 32.dp
    // A compact lane and wider bar make dense month/subproject charts easier
    // to scan. The label still owns its lane, so tooltips and click targets
    // remain reliable on touch screens.
    val columnGap = 1.dp
    // Hiding values is also a density control.  Without the value lane each
    // bar can use a narrower column, so more categories fit before the
    // horizontal navigator is needed.
    val valueStyle = MaterialTheme.typography.labelMedium
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val displayValues = data.map { it.formattedValue ?: valueLabel(it.value) }
    // Grouped amounts must fit in a single line without losing their final digits.
    // Only widen columns when needed; short values retain the compact layout.
    val widestValue = if (showDataLabels) displayValues.maxOf {
        textMeasurer.measure(it, valueStyle, softWrap = false, maxLines = 1).size.width
    } else 0
    val effectiveLabelWidth = if (showDataLabels) {
        maxOf(labelWidth, with(density) { widestValue.toDp() } + 8.dp)
    } else labelWidth * .74f
    val plotWidth = effectiveLabelWidth * data.size + columnGap * (data.size - 1)
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()
            .onSizeChanged { viewportWidth = it.width }
            .horizontalScroll(scroll)) {
        Row(Modifier.width(plotWidth), horizontalArrangement = Arrangement.spacedBy(columnGap)) {
            data.forEachIndexed { index, item ->
                val displayValue = displayValues[index]
                val fullLabel = listOfNotNull(item.label, item.groupLabel?.takeIf { it.isNotBlank() }).joinToString(" ")
                val tooltip = listOf(fullLabel + ": " + displayValue, item.tooltip).filterNotNull().joinToString("\n")
                oms.components.OmsTooltipBox(tooltip = { Text(tooltip) }) {
                    Column(Modifier.width(effectiveLabelWidth).chartItemHighlight().semantics { contentDescription = tooltip }
                        .then(
                            if (onItemClick == null) Modifier.focusable()
                            else Modifier.clickable { onItemClick(item) }
                                .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(if (showDataLabels) 96.dp else 76.dp), contentAlignment = Alignment.BottomCenter) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (showDataLabels) {
                                    Text(displayValue, style = valueStyle, maxLines = 1, softWrap = false)
                                    Spacer(Modifier.height(4.dp))
                                }
                                Box(Modifier.width(36.dp).height((66f * (item.value.coerceAtLeast(0f) / maxValue)).dp)
                                    .clip(MaterialTheme.shapes.extraSmall).background(item.color ?: color))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(Modifier.fillMaxWidth().height(laneHeight).padding(top = 2.dp), contentAlignment = Alignment.TopCenter) {
                            Text(item.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = labelMaxLines, overflow = TextOverflow.Ellipsis)
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
            Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.width(120.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    TableScrollControls(
                        scroll, compact = true,
                        stepDistance = (viewportWidth * .8f).coerceAtLeast(1f)
                    )
                }
            }
        }
    }
}
