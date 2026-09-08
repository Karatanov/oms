package oms.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import oms.components.HoldToScrollButton
import oms.components.OmsTooltipBox

/** Horizontal counterpart to [VerticalBarChart], with vertical navigation. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HorizontalBarChart(
    data: List<BarData>, color: Color, valueLabel: (Float) -> String = { it.toLong().toString() },
    onItemClick: ((BarData) -> Unit)? = null
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.value }.coerceAtLeast(1f)
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().heightIn(max = 238.dp).verticalScroll(scroll)) {
            data.forEach { item ->
                val displayValue = item.formattedValue ?: valueLabel(item.value)
                val tooltip = listOf(item.label + ": " + displayValue, item.tooltip).filterNotNull().joinToString("\n")
                OmsTooltipBox(tooltip = { Text(tooltip) }) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 42.dp).then(
                            if (onItemClick == null) Modifier.focusable()
                            else Modifier.clickable { onItemClick(item) }.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
                        ), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(item.label, Modifier.width(108.dp), maxLines = 2, style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.weight(1f).height(20.dp).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(Modifier.fillMaxWidth((item.value.coerceAtLeast(0f) / maxValue).coerceIn(0f, 1f)).fillMaxHeight().background(item.color ?: color))
                        }
                        Text(displayValue, Modifier.width(72.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (scroll.maxValue > 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            HoldToScrollButton("Scroll up", Icons.Default.KeyboardArrowUp, scroll, -1, clickDistance = 300f, continuousPixelsPerFrame = 8f)
            Text("${((scroll.value.toFloat() / scroll.maxValue) * 100).toInt()}%", Modifier.width(56.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            HoldToScrollButton("Scroll down", Icons.Default.KeyboardArrowDown, scroll, 1, clickDistance = 300f, continuousPixelsPerFrame = 8f)
        }
    }
}
