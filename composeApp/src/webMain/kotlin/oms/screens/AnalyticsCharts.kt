package oms.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import oms.charts.BarData
import oms.charts.VerticalBarChart
import oms.data.ApiDashboardMetric
import oms.data.ApiMonthlyActPayment
import oms.data.ApiSubprojectFunding
import oms.data.ApiSubprojectProgress
import oms.localization.LocalizationManager
import oms.screens.dashboard.toMonthName

@Composable
fun FundingByOblastChart(items: List<ApiSubprojectFunding>) {
    val colors = listOf(Color(0xFF278DAD), Color(0xFF4D9F76), Color(0xFFC68642), Color(0xFF7666A5), Color(0xFFB85E75))
    val data = items
        .groupBy { it.region.toOblastChartLabel() }
        .entries
        .sortedBy { it.key }
        .mapIndexed { index, entry ->
            BarData(entry.key, entry.value.sumOf { it.amount }.toFloat(), color = colors[index % colors.size])
        }
    AnalyticsCard("approved_funding_by_oblast", "approved_funding_by_oblast_hint", data, { money(it.toInt().toLong()) })
}

@Composable
fun SubprojectProgressChart(items: List<ApiSubprojectProgress>, onOpenProject: (String) -> Unit) {
    val data = items.map { BarData(it.name, it.completionPct.toFloat().coerceIn(0f, 100f)) }
    AnalyticsCard("subproject_completion", "subproject_completion_hint", data, { "${it.toInt()}%" }, onItemClick = { bar ->
        items.firstOrNull { it.name == bar.label }?.let { onOpenProject(it.projectUuid) }
    })
}

@Composable
fun MetricsChart(
    titleKey: String,
    hintKey: String? = null,
    metrics: List<ApiDashboardMetric>,
    centerYearLabels: Boolean = false
) {
    val sortedMetrics = metrics.sortedBy { it.label }
    val yearCenterIndexes = if (centerYearLabels) {
        sortedMetrics.withIndex()
            .filter { it.value.label.matches(Regex("\\d{4}-\\d{2}")) }
            .groupBy { it.value.label.take(4) }
            .values
            .associate { group -> group[group.size / 2].index to group.first().value.label.take(4) }
    } else emptyMap()
    val data = sortedMetrics.mapIndexed { index, metric ->
        val isMonth = metric.label.matches(Regex("\\d{4}-\\d{2}"))
        BarData(
            label = if (centerYearLabels && isMonth) metric.label.toMonthName() else metric.label.toChartMonth(),
            value = metric.value.toFloat(),
            groupLabel = yearCenterIndexes[index]
        )
    }
    AnalyticsCard(titleKey, hintKey, data)
}

@Composable
fun MonthlyAmountsChart(titleKey: String, hintKey: String, payments: List<ApiMonthlyActPayment>) {
    val data = payments.sortedBy { it.month }.map { BarData(it.month.toChartMonth(), it.amountEurCents.toFloat()) }
    AnalyticsCard(titleKey, hintKey, data, { euro(it.toInt().toLong()) })
}

@Composable
private fun AnalyticsCard(
    titleKey: String,
    hintKey: String?,
    data: List<BarData>,
    valueLabel: (Float) -> String = { it.toInt().toString() },
    onItemClick: ((BarData) -> Unit)? = null
) {
    Card(Modifier.fillMaxWidth().height(320.dp), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    LocalizationManager.t(titleKey),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
            Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                hintKey?.let {
                    Text(
                        LocalizationManager.t(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (data.isEmpty()) Text(LocalizationManager.t("no_chart_data"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else VerticalBarChart(data, MaterialTheme.colorScheme.primary, labelWidth = 116.dp, labelMaxLines = 2, maxVisibleItems = 6, valueLabel = valueLabel, onItemClick = onItemClick)
        }
    }
}

private fun String.toChartMonth(): String = if (matches(Regex("\\d{4}-\\d{2}"))) "${toMonthName()} ${take(4)}" else this
private fun String.toOblastChartLabel(): String = replace(Regex("(?i)\\s+(область|oblast)$"), "").trim()
private fun money(value: Long): String = "${value.toString().reversed().chunked(3).joinToString(" ").reversed()} грн"
private fun euro(cents: Long): String = "€ " + (cents / 100).toString() + "." + (cents % 100).toString().padStart(2, '0')
