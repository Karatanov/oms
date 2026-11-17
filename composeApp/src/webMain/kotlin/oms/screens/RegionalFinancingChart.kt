package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import oms.charts.ChartSortCriterion
import oms.charts.sortChartCategories
import oms.components.*
import oms.localization.LocalizationManager

/** Paired series share one scale, while exact amounts remain in labels and tooltips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RegionalFinancingChart(items: List<RegionalFinancingAmount>, loading: Boolean, failed: Boolean, onRetry: () -> Unit, titleSuffix: String? = null, itemsEur: List<RegionalFinancingAmount> = emptyList()) {
    var currency by remember { mutableStateOf("UAH") }
    val displayedItems = if (currency == "EUR") itemsEur else items
    var expanded by remember { mutableStateOf(true) }
    var criterion by remember { mutableStateOf(ChartSortCriterion.Value) }
    var ascending by remember { mutableStateOf(false) }
    val planColor = MaterialTheme.colorScheme.primary
    val paidColor = Color(0xFFB65B13)
    val planLabel = LocalizationManager.t("regional_financing_plan")
    val paidLabel = LocalizationManager.t("regional_financing_paid")
    val valueStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    fun region(value: String) = if (value.isBlank()) LocalizationManager.t("construction_unspecified") else localizedUkraineRegion(value)
    fun money(value: Long?) = value?.let {
        val sign = if (it < 0) "−" else ""
        val whole = it / 100
        val fraction = kotlin.math.abs(it % 100).toString().padStart(2, '0')
        val separator = if (LocalizationManager.currentLanguage == oms.localization.Language.EN) "." else ","
        "$sign${formatGroupedNumber(kotlin.math.abs(whole))}$separator$fraction"
    } ?: "—"
    val sorted = sortChartCategories(displayedItems, criterion, ascending, { region(it.region) }, { (it.plannedCents ?: -1L).toDouble() })
    val known = displayedItems.flatMap { listOfNotNull(it.plannedCents, it.paidCents) }
    val minimum = (known.minOrNull() ?: 0L).coerceAtMost(0L).toDouble()
    val maximum = (known.maxOrNull() ?: 0L).coerceAtLeast(0L).toDouble()
    val span = (maximum - minimum).coerceAtLeast(1.0)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OmsChartHeader(listOfNotNull("${LocalizationManager.t("regional_financing_title")}, ${LocalizationManager.currencyLabel(currency)}", titleSuffix).joinToString(" — ")) {
                CurrencySelector(currency) { currency = it }
                ChartSortControls(criterion, ascending, { criterion = it }, { ascending = !ascending })
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(30.dp).buttonHandCursor()) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        LocalizationManager.t(if (expanded) "collapse" else "expand"), modifier = Modifier.size(18.dp))
                }
            }
            if (expanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(planLabel to planColor, paidLabel to paidColor).forEach { (label, color) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(color))
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                when {
                    loading -> ContentState(LocalizationManager.t("financial_progress_loading"), loading = true)
                    failed -> ContentState(LocalizationManager.t("financial_progress_load_error"), error = true, onRetry = onRetry)
                    displayedItems.isEmpty() -> Text(LocalizationManager.t("no_chart_data"))
                    else -> {
                        if (displayedItems.any { it.plannedCents == null || it.paidCents == null }) Text(
                            LocalizationManager.t("regional_financing_missing"), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                        Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sorted.forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(region(item.region), style = MaterialTheme.typography.labelLarge)
                                    listOf(Triple(planLabel, item.plannedCents, planColor), Triple(paidLabel, item.paidCents, paidColor)).forEach { (label, amount, color) ->
                                        OmsTooltipBox(tooltip = { Text("${region(item.region)}\n$label: ${money(amount)} ${LocalizationManager.currencyLabel(currency)}") }) {
                                            BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                                val value = money(amount)
                                                val labelWidth = with(density) { textMeasurer.measure(value, valueStyle).size.width.toDp() } + 12.dp
                                                val start = ((minOf(0.0, amount?.toDouble() ?: 0.0) - minimum) / span).toFloat().coerceIn(0f, 1f)
                                                val width = (kotlin.math.abs(amount?.toDouble() ?: 0.0) / span).toFloat().coerceIn(0f, 1f)
                                                val barWidth = maxWidth * width
                                                val fitsInside = amount != null && amount != 0L && barWidth >= labelWidth
                                                if (amount != null && amount != 0L) {
                                                    Box(Modifier.offset(x = maxWidth * start).width(barWidth)
                                                        .fillMaxHeight().background(color))
                                                }
                                                if (minimum < 0) Box(Modifier.offset(x = maxWidth * (-minimum / span).toFloat())
                                                    .width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurfaceVariant))
                                                // Short/zero bars keep their true scale. Their values stay
                                                // inside the plotting track on a contrasting background.
                                                Box(if (fitsInside) Modifier.offset(x = maxWidth * start).width(barWidth).fillMaxHeight()
                                                    else Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                                                    Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.background(if (fitsInside) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
                                                            .padding(horizontal = 6.dp),
                                                        color = if (!fitsInside) MaterialTheme.colorScheme.onSurfaceVariant
                                                            else if (color == planColor) MaterialTheme.colorScheme.onPrimary else Color.White,
                                                        style = valueStyle)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
