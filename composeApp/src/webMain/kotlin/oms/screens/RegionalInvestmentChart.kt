package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.VerticalAlignBottom
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import oms.charts.chartItemHighlight
import oms.components.*
import oms.localization.LocalizationManager
import oms.theme.InvestmentColors

/** Three adjacent bars share a scale. The total is not stacked with its components. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RegionalInvestmentChart(items: List<RegionalInvestment>, loading: Boolean, failed: Boolean, onRetry: () -> Unit, titleSuffix: String? = null) {
    var expanded by remember { mutableStateOf(true) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val orderedItems = items.sortedBy { localizedUkraineRegion(it.region) }
    LaunchedEffect(orderedItems.map { it.region }) { scroll.scrollTo(0) }
    val colors = listOf(InvestmentColors.Total, InvestmentColors.Eib, InvestmentColors.Local)
    val labels = listOf("payment_monitoring_total_cost", "payment_monitoring_eib_cost", "payment_monitoring_local_cost").map(LocalizationManager::t)
    val values = items.flatMap { listOfNotNull(it.total, it.eib, it.local) }
    val maximum = values.maxOrNull()?.coerceAtLeast(0) ?: 0L
    val minimum = values.minOrNull()?.coerceAtMost(0) ?: 0L
    val span = (maximum.toDouble() - minimum).coerceAtLeast(1.0)
    fun money(value: Long?) = value?.let { (if (it < 0) "−" else "") + formatGroupedNumber(kotlin.math.abs(it / 100)) + (if (LocalizationManager.currentLanguage == oms.localization.Language.EN) "." else ",") + (kotlin.math.abs(it % 100)).toString().padStart(2, '0') } ?: "—"
    Card(Modifier.fillMaxWidth().semantics { testTag = "regional-investment-chart" }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OmsChartHeader(listOfNotNull(LocalizationManager.t("regional_investment_title"), titleSuffix).joinToString(" — ")) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    if (expanded && scroll.maxValue > 0) {
                        listOf(false, true).forEach { end ->
                            val label = LocalizationManager.t(if (end) "chart_scroll_end" else "chart_scroll_start")
                            val enabled = if (end) scroll.value < scroll.maxValue else scroll.value > 0
                            OmsTooltipBox(tooltip = { Text(label) }, enabled = enabled) {
                                WorkspaceIconButton(
                                    onClick = { scope.launch { scroll.animateScrollTo(if (end) scroll.maxValue else 0) } },
                                    enabled = enabled, modifier = Modifier.size(24.dp).chartControlFocus()
                                ) {
                                    Icon(if (end) Icons.Default.VerticalAlignBottom else Icons.Default.VerticalAlignTop,
                                        label, Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    val collapseLabel = LocalizationManager.t(if (expanded) "collapse" else "expand")
                    OmsTooltipBox(tooltip = { Text(collapseLabel) }) {
                        WorkspaceIconButton(onClick = { expanded = !expanded },
                            modifier = Modifier.size(24.dp).chartControlFocus()) {
                            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                collapseLabel, Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (expanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    labels.forEachIndexed { index, label -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(colors[index])); Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    } }
                }
                when {
                    loading -> ChartContentState(LocalizationManager.t("financial_progress_loading"), loading = true)
                    failed -> ChartContentState(LocalizationManager.t("financial_progress_load_error"), onRetry = onRetry)
                    items.isEmpty() -> Text(LocalizationManager.t("no_chart_data"))
                    else -> {
                        Column(Modifier.fillMaxWidth().heightIn(max = 216.dp).verticalScroll(scroll),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            orderedItems.forEach { item ->
                                val region = localizedUkraineRegion(item.region).ifBlank { LocalizationManager.t("construction_unspecified") }
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ExpandableTableText(region, Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.labelSmall)
                                    listOf(item.total, item.eib, item.local).forEachIndexed { index, amount ->
                                        val hint = "$region\n${labels[index]}: ${money(amount)}" +
                                            if (!item.reconciled && item.total != null && item.eib != null && item.local != null)
                                                "\n${LocalizationManager.t("investment_source_difference")}" else ""
                                        OmsTooltipBox(tooltip = { Text(hint) }, openOnPress = true) {
                                            Row(Modifier.fillMaxWidth().chartItemHighlight()
                                                .semantics { contentDescription = hint }.pointerHoverIcon(PointerIcon.Hand),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                BoxWithConstraints(Modifier.weight(1f).height(12.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                                                    val zero = (-minimum.toDouble() / span).toFloat()
                                                    val start = ((minOf(0L, amount ?: 0L).toDouble() - minimum) / span).toFloat()
                                                    val fraction = (kotlin.math.abs((amount ?: 0L).toDouble()) / span).toFloat()
                                                    if (amount != null && amount != 0L) {
                                                        Box(Modifier.offset(x = maxWidth * start)
                                                            .width(maxWidth * fraction).fillMaxHeight().background(colors[index]))
                                                    }
                                                    Box(Modifier.offset(x = maxWidth * zero).width(1.dp).fillMaxHeight()
                                                        .background(MaterialTheme.colorScheme.outline))
                                                }
                                                Text(money(amount), Modifier.width(132.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                    maxLines = 1, softWrap = false)
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
