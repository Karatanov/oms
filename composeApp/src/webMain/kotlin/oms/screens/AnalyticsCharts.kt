package oms.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import oms.charts.BarData
import oms.charts.VerticalBarChart
import oms.charts.HorizontalBarChart
import oms.charts.BarChartOrientation
import oms.charts.ChartSortCriterion
import oms.charts.sortBarData
import oms.charts.sortChartCategories
import oms.data.ApiDashboardMetric
import oms.data.ApiMonthlyActPayment
import oms.data.ApiSubprojectFunding
import oms.data.ApiSubprojectProgress
import oms.localization.LocalizationManager
import oms.screens.dashboard.toMonthName
import oms.components.FilterDropdown
import oms.components.OmsTooltipBox
import oms.components.ChartSortControls
import oms.components.ChartPngExportButton
import oms.components.OmsChartHeader
import oms.components.localizedUkraineRegion
import oms.components.canonicalUkraineRegion
import oms.components.buttonHandCursor
import kotlin.math.roundToLong

@Composable
fun FundingByOblastChart(items: List<ApiSubprojectFunding>, onOpenFinancialRegion: (String) -> Unit = {}, orientation: BarChartOrientation = BarChartOrientation.Vertical) {
    var currency by remember { mutableStateOf("EUR") }
    val fundingByRegion = items
        .groupBy { canonicalUkraineRegion(it.region) }
        .mapValues { (_, rows) -> rows.sumOf { if (currency == "EUR") it.amountEur else it.amountUah } }
    // The map is a comparison of approved funding, not an exchange-rate
    // heatmap.  Keep its intensity on the canonical UAH amount so changing
    // the displayed currency never changes the relative colour of regions.
    val intensityByRegion = items
        .groupBy { canonicalUkraineRegion(it.region) }
        .mapValues { (_, rows) -> rows.sumOf { it.amountUah } }
    UkraineFundingMapChart(
        amounts = fundingByRegion,
        intensityAmounts = intensityByRegion,
        currency = currency,
        onCurrencyChange = { currency = it },
        onOpenFinancialRegion = onOpenFinancialRegion
    )
}

@Composable
private fun UkraineFundingMapChart(
    amounts: Map<String, Double>,
    intensityAmounts: Map<String, Double>,
    currency: String,
    onCurrencyChange: (String) -> Unit,
    onOpenFinancialRegion: (String) -> Unit
) {
    val maxAmount = intensityAmounts.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    Card(
        Modifier.fillMaxWidth().heightIn(min = 462.dp),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                Text(LocalizationManager.t("approved_funding_by_oblast"), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            Text(
                LocalizationManager.t("approved_funding_by_oblast_hint"),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                oms.components.CurrencySelector(currency, onCurrencyChange)
            }
            Spacer(Modifier.height(14.dp))
            UkraineFundingMap(
                amounts = amounts,
                intensityAmounts = intensityAmounts,
                currency = currency,
                maxAmount = maxAmount,
                onOpenFinancialRegion = onOpenFinancialRegion
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun UkraineFundingMap(
    amounts: Map<String, Double>,
    intensityAmounts: Map<String, Double>,
    currency: String,
    maxAmount: Double,
    onOpenFinancialRegion: (String) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val emptyRegion = MaterialTheme.colorScheme.surfaceVariant
    val border = MaterialTheme.colorScheme.outlineVariant
    var hoveredRegion by remember { mutableStateOf<UkraineMapRegion?>(null) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    OmsTooltipBox(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ukraineMapWidth / ukraineMapHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(8.dp),
        enabled = hoveredRegion != null,
        tooltip = {
            hoveredRegion?.let { region ->
                Column {
                    Text(localizedUkraineRegion(region.name), fontWeight = FontWeight.SemiBold)
                    Text("${formatFundingMapAmount(amounts[region.name] ?: 0.0)} $currency", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { mapSize = it }
                .onPointerEvent(PointerEventType.Move) { event ->
                    val position = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                    if (mapSize.width == 0 || mapSize.height == 0) return@onPointerEvent
                    val point = UkraineMapPoint(
                        position.x / mapSize.width * ukraineMapWidth,
                        position.y / mapSize.height * ukraineMapHeight
                    )
                    hoveredRegion = ukraineFundingMapRegions.firstOrNull { it.contains(point) }
                }
                .onPointerEvent(PointerEventType.Exit) { hoveredRegion = null }
                .pointerInput(onOpenFinancialRegion, amounts) {
                detectTapGestures { position ->
                    val point = UkraineMapPoint(
                        position.x / size.width * ukraineMapWidth,
                        position.y / size.height * ukraineMapHeight
                    )
                    ukraineFundingMapRegions.firstOrNull { it.contains(point) }
                        ?.let { onOpenFinancialRegion(it.name) }
                }
                }
        ) {
            val scaleX = size.width / ukraineMapWidth
            val scaleY = size.height / ukraineMapHeight
            ukraineFundingMapRegions.forEach { region ->
                val intensityAmount = intensityAmounts[region.name] ?: 0.0
                val intensity = (intensityAmount / maxAmount).toFloat().coerceIn(0f, 1f)
                val isHovered = region == hoveredRegion
                val fill = when {
                    isHovered && intensityAmount <= 0.0 -> primary.copy(alpha = .30f)
                    isHovered -> primary.copy(alpha = (.32f + intensity * .68f).coerceAtMost(1f))
                    intensityAmount <= 0.0 -> emptyRegion
                    else -> primary.copy(alpha = .18f + intensity * .72f)
                }
                val path = Path()
                region.rings.forEach { ring ->
                    if (ring.size < 3) return@forEach
                    path.moveTo(ring.first().x * scaleX, ring.first().y * scaleY)
                    ring.drop(1).forEach { path.lineTo(it.x * scaleX, it.y * scaleY) }
                    path.close()
                }
                drawPath(path, fill)
                drawPath(
                    path,
                    if (isHovered) primary else border,
                    style = Stroke(width = if (isHovered) 2.5f else 1.25f)
                )
            }
        }
    }
}

/** Compact locale-neutral money formatter suitable for Canvas tooltips in JS/Wasm. */
private fun formatFundingMapAmount(value: Double): String {
    val cents = (value * 100).roundToLong()
    val sign = if (cents < 0) "-" else ""
    val absolute = if (cents < 0) -cents else cents
    val whole = absolute / 100
    val fraction = absolute % 100
    val grouped = whole.toString().reversed().chunked(3).joinToString(" ").reversed()
    return if (fraction == 0L) "$sign$grouped" else "$sign$grouped,${fraction.toString().padStart(2, '0').trimEnd('0')}"
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
        orientation = orientation,
        sortEnabled = true
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
    compact: Boolean = false,
    sortControls: (@Composable () -> Unit)? = null
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
            OmsChartHeader(LocalizationManager.t(titleKey)) {
                sortControls?.invoke()
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
        } else if (titleKey.contains("signed_contract")) {
            LocalizationManager.t(metric.label)
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
        onExpandedChange = onExpandedChange,
        // Month-keyed metrics are chronological and must never be reordered.
        sortEnabled = metrics.any() && metrics.none { it.label.matches(Regex("\\d{4}-\\d{2}")) }
    )
}

@Composable
fun MetricsListChart(titleKey: String, metrics: List<ApiDashboardMetric>, compact: Boolean = false) {
    var criterion by remember { mutableStateOf<ChartSortCriterion?>(null) }
    var ascending by remember { mutableStateOf(true) }
    val displayMetrics = criterion?.let { activeCriterion ->
        sortChartCategories(
            metrics,
            activeCriterion,
            ascending,
            labelOf = { LocalizationManager.procurementStatus(it.label) },
            valueOf = { it.value.toDouble() }
        )
    } ?: metrics
    AnalyticsListCard(
        titleKey,
        displayMetrics.map { AnalyticsListRow(LocalizationManager.procurementStatus(it.label), it.value.toString()) },
        compact = compact,
        sortControls = {
            ChartSortControls(criterion, ascending, { criterion = it }, { ascending = !ascending })
        }
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
    var currency by remember { mutableStateOf("EUR") }
    MonthlyMoneyChart(
        titleKey = titleKey,
        hintKey = hintKey,
        payments = payments.map { payment ->
            MonthlyMoneyAmount(payment.month, if (currency == "EUR") payment.amountEurCents else payment.amountUahCents ?: 0L)
        },
        currency = currency,
        tooltipByMonth = tooltipByMonth,
        currencySelector = { oms.components.CurrencySelector(currency) { currency = it } },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        orientation = orientation
    )
}

data class MonthlyMoneyAmount(val month: String, val amountCents: Long)

@Composable
fun MonthlyMoneyChart(
    titleKey: String,
    hintKey: String?,
    payments: List<MonthlyMoneyAmount>,
    currency: String,
    titleSuffix: String? = null,
    tooltipByMonth: Map<String, String> = emptyMap(),
    currencySelector: (@Composable () -> Unit)? = null,
    expanded: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onMonthClick: (String) -> Unit = {},
    orientation: BarChartOrientation = BarChartOrientation.Vertical
) {
    val data = payments.sortedBy { it.month }.map {
        BarData(
            label = it.month.toMonthName(),
            value = it.amountCents.toFloat(),
            id = it.month,
            groupLabel = it.month.take(4),
            tooltip = tooltipByMonth[it.month],
            formattedValue = formatChartAmount(it.amountCents / 100.0, currency)
        )
    }
    AnalyticsCard(
        titleKey, hintKey, data, { formatChartAmount(it / 100.0, currency) }, labelMaxLines = 1,
        filterContent = currencySelector, expanded = expanded, onExpandedChange = onExpandedChange,
        currency = currency,
        titleSuffix = titleSuffix,
        onItemClick = { bar -> bar.id?.let(onMonthClick) }, orientation = orientation
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
    orientation: BarChartOrientation = BarChartOrientation.Vertical,
    sortEnabled: Boolean = false,
    currency: String? = null,
    titleSuffix: String? = null
) {
    var sortCriterion by remember { mutableStateOf<ChartSortCriterion?>(null) }
    var sortAscending by remember { mutableStateOf(true) }
    val displayData = sortCriterion?.let { sortBarData(data, it, sortAscending) } ?: data
    val cardModifier = Modifier.fillMaxWidth().then(
        if (!expanded) Modifier
        else if (compact) Modifier.heightIn(min = 144.dp)
        else Modifier.heightIn(min = if (filterContent == null) 348.dp else 392.dp)
    )
    Card(cardModifier, shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            val title = listOfNotNull(LocalizationManager.t(titleKey), titleSuffix).joinToString(" — ")
            OmsChartHeader(title) {
                if (sortEnabled) ChartSortControls(
                    criterion = sortCriterion,
                    ascending = sortAscending,
                    onCriterionChange = { sortCriterion = it },
                    onDirectionToggle = { sortAscending = !sortAscending }
                )
                if (displayData.isNotEmpty()) ChartPngExportButton(
                    title = title,
                    filenameStem = titleKey,
                    data = displayData,
                    orientation = orientation,
                    showDataLabels = true,
                    valueLabel = valueLabel,
                    subtitle = hintKey?.let(LocalizationManager::t),
                    currency = currency
                )
                onExpandedChange?.let { onToggle ->
                    IconButton(onClick = { onToggle(!expanded) }, modifier = Modifier.size(36.dp).buttonHandCursor()) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = LocalizationManager.t(if (expanded) "collapse" else "expand"),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (expanded) {
                Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
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
                Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
                if (displayData.isEmpty()) Text(LocalizationManager.t("no_chart_data"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (orientation == BarChartOrientation.Vertical) VerticalBarChart(displayData, MaterialTheme.colorScheme.primary, labelMaxLines = labelMaxLines, maxVisibleItems = 6, valueLabel = valueLabel, onItemClick = onItemClick, showScrollControls = showScrollControls)
                else HorizontalBarChart(displayData, MaterialTheme.colorScheme.primary, valueLabel = valueLabel, onItemClick = onItemClick)
            }
        }
    }
}

private fun String.toChartMonth(): String = if (matches(Regex("\\d{4}-\\d{2}"))) "${toMonthName()} ${take(4)}" else this
// Inline (?i) flags are not supported by the JavaScript RegExp engine.
internal fun String.toRegionChartLabel(): String =
    replace(Regex("\\s+(область|oblast|region)$", RegexOption.IGNORE_CASE), "").trim()
private fun formatChartAmount(value: Double, currency: String): String {
    // Charts communicate a trend; whole units are clearer. Exact cents stay
    // available in the register and in tooltips sourced from that register.
    return groupedNumber(value.roundToLong())
}

private fun groupedNumber(value: Long): String = value.toString().reversed().chunked(3).joinToString(" ").reversed()
private fun euro(cents: Long): String = "€ " + (cents / 100).toString() + "." + (cents % 100).toString().padStart(2, '0')
