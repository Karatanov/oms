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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import oms.charts.BarData
import oms.charts.VerticalBarChart
import oms.charts.HorizontalBarChart
import oms.charts.BarChartOrientation
import oms.data.ApiDashboardMetric
import oms.data.ApiMonthlyActPayment
import oms.data.ApiSubprojectFunding
import oms.data.ApiSubprojectProgress
import oms.localization.LocalizationManager
import oms.screens.dashboard.toMonthName
import oms.components.FilterDropdown
import oms.components.localizedUkraineRegion
import oms.components.canonicalUkraineRegion
import kotlin.math.roundToLong

@Composable
fun FundingByOblastChart(items: List<ApiSubprojectFunding>, onOpenRegion: (String) -> Unit = {}, orientation: BarChartOrientation = BarChartOrientation.Vertical) {
    var currency by remember { mutableStateOf("EUR") }
    val data = items
        .groupBy { localizedUkraineRegion(it.region).toRegionChartLabel() }
        .entries
        .sortedBy { it.key }
        .map { entry ->
            val amount = entry.value.sumOf { if (currency == "EUR") it.amountEur else it.amountUah }
            BarData(
                label = entry.key,
                value = amount.toFloat(),
                id = entry.value.firstOrNull()?.region,
                formattedValue = formatChartAmount(amount, currency),
            )
        }
    AnalyticsCard(
        titleKey = "approved_funding_by_oblast",
        hintKey = "approved_funding_by_oblast_hint",
        data = data,
        valueLabel = { formatChartAmount(it.toDouble(), currency) },
        onItemClick = { bar -> bar.id?.let(onOpenRegion) },
        labelMaxLines = 2,
        filterContent = {
            oms.components.CurrencySelector(currency) { currency = it }
        }, orientation = orientation
    )
}

@Composable
fun SubprojectProgressChart(items: List<ApiSubprojectProgress>, onOpenFinancial: (String) -> Unit, orientation: BarChartOrientation = BarChartOrientation.Vertical) {
    var regionFilter by remember(items) { mutableStateOf<String?>(null) }
    val regions = items.map { it.region }.filter(String::isNotBlank).map(::canonicalUkraineRegion).distinct().sorted()
    val data = items
        .filter { regionFilter == null || canonicalUkraineRegion(it.region) == regionFilter }
        .sortedBy { it.name }
        .map {
            BarData(
                label = it.code.ifBlank { it.name },
                value = it.completionPct.toFloat(),
                id = it.projectUuid,
                tooltip = if (LocalizationManager.currentLanguage == oms.localization.Language.EN) it.nameEn?.takeIf(String::isNotBlank) ?: it.name else it.name,
                formattedValue = completionPercentLabel(it.completionPct)
            )
        }
    AnalyticsCard(
        titleKey = "subproject_completion",
        hintKey = "subproject_completion_hint",
        data = data,
        valueLabel = { completionPercentLabel(it.toDouble()) },
        onItemClick = { bar -> bar.id?.let(onOpenFinancial) },
        labelMaxLines = 2,
        filterContent = {
            FilterDropdown(
                label = LocalizationManager.t("region"),
                options = regions,
                selected = regionFilter,
                onSelect = { regionFilter = it },
                itemLabel = ::localizedUkraineRegion
            )
        },
        orientation = orientation
    )
}

/** Retain small non-zero completion values instead of truncating 0.8% to 0%. */
private fun completionPercentLabel(value: Double): String {
    val tenths = (value.coerceAtLeast(0.0) * 10).roundToLong()
    return if (tenths % 10L == 0L) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%"
}

private data class AnalyticsListRow(
    val label: String,
    val value: String,
    val supporting: String? = null,
    val progress: Float? = null,
    val onClick: (() -> Unit)? = null
)

@Composable
private fun AnalyticsListCard(
    titleKey: String,
    rows: List<AnalyticsListRow>,
    hintKey: String? = null,
    compact: Boolean = false
) {
    var page by remember(rows) { mutableStateOf(0) }
    val pageSize = 6
    val pageCount = ((rows.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    if (page >= pageCount) page = pageCount - 1
    Card(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 300.dp else 420.dp),
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
                val clickModifier = row.onClick?.let {
                    Modifier.clickable(onClick = it).pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
                } ?: Modifier
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
    compact: Boolean = false,
    onItemClick: ((String) -> Unit)? = null,
    showScrollControls: Boolean = true,
    orientation: BarChartOrientation = BarChartOrientation.Vertical,
    expanded: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null
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
            id = metric.label,
            groupLabel = if (centerYearLabels && isMonth) metric.label.take(4) else null
        )
    }
    AnalyticsCard(
        titleKey,
        hintKey,
        data,
        compact = compact,
        showScrollControls = showScrollControls,
        onItemClick = onItemClick?.let { handler -> { bar -> bar.id?.let(handler) } },
        orientation = orientation,
        expanded = expanded,
        onExpandedChange = onExpandedChange
    )
}

@Composable
fun MetricsListChart(titleKey: String, metrics: List<ApiDashboardMetric>, compact: Boolean = false) {
    AnalyticsListCard(
        titleKey,
        metrics.map { AnalyticsListRow(LocalizationManager.procurementStatus(it.label), it.value.toString()) },
        compact = compact
    )
}

@Composable
fun MonthlyAmountsChart(
    titleKey: String,
    hintKey: String? = null,
    payments: List<ApiMonthlyActPayment>,
    tooltipByMonth: Map<String, String> = emptyMap(),
    expanded: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    orientation: BarChartOrientation = BarChartOrientation.Vertical
) {
    MonthlyMoneyChart(titleKey, hintKey, payments.map { MonthlyMoneyAmount(it.month, it.amountEurCents) },
        "EUR", tooltipByMonth, expanded, onExpandedChange, orientation)
}

data class MonthlyMoneyAmount(val month: String, val amountCents: Long)

@Composable
fun MonthlyMoneyChart(
    titleKey: String,
    hintKey: String?,
    payments: List<MonthlyMoneyAmount>,
    currency: String,
    tooltipByMonth: Map<String, String> = emptyMap(),
    expanded: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    orientation: BarChartOrientation = BarChartOrientation.Vertical
) {
    val data = payments.sortedBy { it.month }.map {
        BarData(
            label = it.month.toMonthName(),
            value = it.amountCents.toFloat(),
            groupLabel = it.month.take(4),
            tooltip = tooltipByMonth[it.month],
            formattedValue = "${formatChartAmount(it.amountCents / 100.0, "EUR")} $currency"
        )
    }
    AnalyticsCard(
        titleKey, hintKey, data, { "${formatChartAmount(it / 100.0, "EUR")} $currency" }, labelMaxLines = 1,
        expanded = expanded, onExpandedChange = onExpandedChange, orientation = orientation
    )
}

@Composable
private fun AnalyticsCard(
    titleKey: String,
    hintKey: String?,
    data: List<BarData>,
    valueLabel: (Float) -> String = { it.toInt().toString() },
    onItemClick: ((BarData) -> Unit)? = null,
    compact: Boolean = false,
    labelMaxLines: Int = 2,
    filterContent: (@Composable () -> Unit)? = null,
    showScrollControls: Boolean = true,
    expanded: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    orientation: BarChartOrientation = BarChartOrientation.Vertical
) {
    val cardModifier = Modifier.fillMaxWidth().then(
        if (!expanded) Modifier
        else if (compact) Modifier.heightIn(min = 144.dp)
        else Modifier.heightIn(min = if (filterContent == null) 420.dp else 476.dp)
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
                onExpandedChange?.let { onToggle ->
                    IconButton(
                        onClick = { onToggle(!expanded) },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = LocalizationManager.t(if (expanded) "collapse" else "expand")
                        )
                    }
                }
            }
            if (expanded) {
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
                filterContent?.let { content ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
                }
                Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                if (data.isEmpty()) Text(LocalizationManager.t("no_chart_data"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (orientation == BarChartOrientation.Vertical) VerticalBarChart(data, MaterialTheme.colorScheme.primary, labelMaxLines = labelMaxLines, maxVisibleItems = 6, valueLabel = valueLabel, onItemClick = onItemClick, showScrollControls = showScrollControls)
                else HorizontalBarChart(data, MaterialTheme.colorScheme.primary, valueLabel = valueLabel, onItemClick = onItemClick)
            }
        }
    }
}

private fun String.toChartMonth(): String = if (matches(Regex("\\d{4}-\\d{2}"))) "${toMonthName()} ${take(4)}" else this
// Inline (?i) flags are not supported by the JavaScript RegExp engine.
internal fun String.toRegionChartLabel(): String =
    replace(Regex("\\s+(область|oblast|region)$", RegexOption.IGNORE_CASE), "").trim()
private fun formatChartAmount(value: Double, currency: String): String {
    if (currency == "EUR") {
        val cents = (value * 100).roundToLong()
        return "${groupedNumber(cents / 100)}.${(cents % 100).toString().padStart(2, '0')}"
    }
    return groupedNumber(value.roundToLong())
}

private fun groupedNumber(value: Long): String = value.toString().reversed().chunked(3).joinToString(" ").reversed()
private fun euro(cents: Long): String = "€ " + (cents / 100).toString() + "." + (cents % 100).toString().padStart(2, '0')
