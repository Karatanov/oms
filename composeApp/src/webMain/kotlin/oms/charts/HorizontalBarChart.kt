package oms.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import oms.components.OmsTooltipBox
import oms.components.formatGroupedNumber

/** Horizontal counterpart to [VerticalBarChart], with vertical navigation. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HorizontalBarChart(
    data: List<BarData>, color: Color, valueLabel: (Float) -> String = { formatGroupedNumber(it.toLong()) },
    onItemClick: ((BarData) -> Unit)? = null,
    showDataLabels: Boolean = true
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.value }.coerceAtLeast(1f)
    val scroll = rememberScrollState()
    val categoryOrder = data.map { (it.id ?: it.label) to it.groupLabel }
    LaunchedEffect(categoryOrder) {
        // A new filter or order should start with its first result. Amount-only
        // updates retain the current position so reading is not interrupted.
        scroll.scrollTo(0)
    }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().heightIn(max = 144.dp).verticalScroll(scroll)) {
            data.forEach { item ->
                val displayValue = item.formattedValue ?: valueLabel(item.value)
                val fullLabel = listOfNotNull(item.label, item.groupLabel?.takeIf { it.isNotBlank() }).joinToString(" ")
                val tooltip = listOf(fullLabel + ": " + displayValue, item.tooltip).filterNotNull().joinToString("\n")
                OmsTooltipBox(tooltip = { Text(tooltip) }) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 32.dp)
                            .semantics(mergeDescendants = true) { contentDescription = tooltip }
                            .then(
                                if (onItemClick == null) Modifier.focusable()
                                else Modifier.clickable { onItemClick(item) }
                                    .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
                            ).padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(fullLabel, Modifier.weight(1f), maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall)
                            if (showDataLabels) {
                                Text(displayValue, maxLines = 1, softWrap = false,
                                    textAlign = TextAlign.End,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(Modifier.fillMaxWidth((item.value.coerceAtLeast(0f) / maxValue).coerceIn(0f, 1f))
                                .fillMaxHeight().background(item.color ?: color))
                        }
                    }
                }
            }
        }
    }
}
