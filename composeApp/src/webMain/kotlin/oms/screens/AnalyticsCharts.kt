package oms.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
    val data = items
        .groupBy { it.region.toOblastChartLabel() }
        .entries
        .sortedBy { it.key }
        .map { entry ->
            BarData(
                label = entry.key,
                value = entry.value.sumOf { it.amount }.toFloat(),
                formattedValue = money(entry.value.sumOf { it.amount }),
                tooltip = entry.value
                    .map { it.name }
                    .filter(String::isNotBlank)
                    .distinct()
                    .sorted()
                    .joinToString("\n")
            )
        }
    AnalyticsCard(
        titleKey = "approved_funding_by_oblast",
        hintKey = "approved_funding_by_oblast_hint",
        data = data,
        valueLabel = { money(it.toLong()) },
        labelMaxLines = 2
    )
}

@Composable
fun SubprojectProgressChart(items: List<ApiSubprojectProgress>, onOpenProject: (String) -> Unit) {
    val data = items
        .sortedBy { it.name }
        .map {
            BarData(
                label = it.name,
                value = it.completionPct.toFloat(),
                id = it.projectUuid,
                formattedValue = "${it.completionPct.toInt()}%"
            )
        }
    AnalyticsCard(
        titleKey = "subproject_completion",
        hintKey = "subproject_completion_hint",
        data = data,
        valueLabel = { "${it.toInt()}%" },
        onItemClick = { bar -> bar.id?.let(onOpenProject) },
        labelMaxLines = 2
    )
}

private data class AnalyticsListRow(
    val label: String,
    val value: String,
    val supporting: String? = null,
    val progress: Float? = null,
    val onClick: (() -> Unit)? = null
)

@Composable
private fun AnalyticsListCard(titleKey: String, rows: List<AnalyticsListRow>, hintKey: String? = null) {
    var page by remember(rows) { mutableStateOf(0) }
    val pageSize = 6
    val pageCount = ((rows.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    if (page >= pageCount) page = pageCount - 1
    Card(
        Modifier.fillMaxWidth().height(420.dp),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                Text(LocalizationManager.t(titleKey), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            hintKey?.let { Text(LocalizationManager.t(it), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(8.dp))
            if (rows.isEmpty()) Text(LocalizationManager.t("no_chart_data"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.drop(page * pageSize).take(pageSize).forEach { row ->
                val clickModifier = row.onClick?.let { Modifier.clickable(onClick = it).pointerHoverIcon(PointerIcon.Hand) } ?: Modifier
                Column(clickModifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(row.label, Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                        Text(row.value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    row.supporting?.takeIf(String::isNotBlank)?.let { Text(it, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    row.progress?.let { LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 4.dp)) }
                }
                HorizontalDivider()
            }
            if (pageCount > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Text("‹") }
                Text("${page + 1} / $pageCount", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { page = (page + 1).coerceAtMost(pageCount - 1) }, enabled = page < pageCount - 1) { Text("›") }
            }
        }
    }
}

@Composable
fun MetricsChart(
    titleKey: String,
    hintKey: String? = null,
    metrics: List<ApiDashboardMetric>,
    centerYearLabels: Boolean = false,
    compact: Boolean = false
) {
    val sortedMetrics = metrics.sortedBy { it.label }
    val data = sortedMetrics.mapIndexed { index, metric ->
        val isMonth = metric.label.matches(Regex("\\d{4}-\\d{2}"))
        val displayLabel = if (titleKey.contains("procurement_status")) {
            LocalizationManager.procurementStatus(metric.label)
        } else metric.label
        BarData(
            label = if (centerYearLabels && isMonth) displayLabel.toMonthName() else displayLabel.toChartMonth(),
            value = metric.value.toFloat(),
            groupLabel = if (centerYearLabels && isMonth) metric.label.take(4) else null
        )
    }
    AnalyticsCard(titleKey, hintKey, data, compact = compact)
}

@Composable
fun MetricsListChart(titleKey: String, metrics: List<ApiDashboardMetric>) {
    AnalyticsListCard(
        titleKey,
        metrics.map { AnalyticsListRow(LocalizationManager.procurementStatus(it.label), it.value.toString()) }
    )
}

@Composable
fun MonthlyAmountsChart(
    titleKey: String,
    hintKey: String? = null,
    payments: List<ApiMonthlyActPayment>,
    tooltipByMonth: Map<String, String> = emptyMap()
) {
    val data = payments.sortedBy { it.month }.map {
        BarData(
            label = it.month.toMonthName(),
            value = it.amountEurCents.toFloat(),
            groupLabel = it.month.take(4),
            tooltip = tooltipByMonth[it.month],
            formattedValue = oms.components.formatEuroCents(it.amountEurCents)
        )
    }
    AnalyticsCard(titleKey, hintKey, data, { euro(it.toLong()) }, labelMaxLines = 1)
}

@Composable
private fun AnalyticsCard(
    titleKey: String,
    hintKey: String?,
    data: List<BarData>,
    valueLabel: (Float) -> String = { it.toInt().toString() },
    onItemClick: ((BarData) -> Unit)? = null,
    compact: Boolean = false,
    labelMaxLines: Int = 2
) {
    val cardModifier = Modifier.fillMaxWidth().then(
        if (compact) Modifier.heightIn(min = 144.dp) else Modifier.height(420.dp)
    )
    Card(cardModifier, shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
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
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            if (data.isEmpty()) Text(LocalizationManager.t("no_chart_data"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else VerticalBarChart(data, MaterialTheme.colorScheme.primary, labelWidth = 116.dp, labelMaxLines = labelMaxLines, maxVisibleItems = 6, valueLabel = valueLabel, onItemClick = onItemClick)
        }
    }
}

private fun String.toChartMonth(): String = if (matches(Regex("\\d{4}-\\d{2}"))) "${toMonthName()} ${take(4)}" else this
private fun String.toOblastChartLabel(): String = replace(Regex("(?i)\\s+(область|oblast)$"), "").trim()
private fun money(value: Long): String = "${value.toString().reversed().chunked(3).joinToString(" ").reversed()} грн"
private fun euro(cents: Long): String = "€ " + (cents / 100).toString() + "." + (cents % 100).toString().padStart(2, '0')
