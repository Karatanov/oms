package oms.charts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Compact vertical bar chart for time-series counts and monetary totals. */
@Composable
fun VerticalBarChart(
    data: List<BarData>,
    color: Color,
    labelWidth: Dp = 76.dp,
    labelMaxLines: Int = 2,
    maxVisibleItems: Int? = null,
    initialScrollToEnd: Boolean = false,
    valueLabel: (Float) -> String = { it.toInt().toString() }
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.value }.coerceAtLeast(1f)
    val scrollState = rememberScrollState()
    LaunchedEffect(data, initialScrollToEnd) {
        if (initialScrollToEnd) scrollState.scrollTo(scrollState.maxValue)
    }
    val viewportWidth = maxVisibleItems?.let { minOf(data.size, it) * (labelWidth.value + 12f) }
    Row(
        modifier = (if (viewportWidth == null) Modifier else Modifier.width(viewportWidth.dp))
            .horizontalScroll(scrollState)
            .height(230.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { item ->
            val barHeight = (156f * (item.value / maxValue)).coerceAtLeast(6f).dp
            Column(
                modifier = Modifier.width(labelWidth).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(valueLabel(item.value), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(barHeight)
                        .clip(MaterialTheme.shapes.small)
                        .background(color.copy(alpha = 0.86f))
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = labelMaxLines,
                    softWrap = labelMaxLines > 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (data.any { it.groupLabel != null }) {
                    Text(
                        item.groupLabel.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
