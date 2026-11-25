package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RegionalInvestmentChart(items: List<RegionalInvestment>, loading: Boolean, failed: Boolean, onRetry: () -> Unit, titleSuffix: String? = null) {
    var expanded by remember { mutableStateOf(true) }
    val scroll = rememberScrollState()
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
                WorkspaceTextButton(onClick = { expanded = !expanded }) { Text(LocalizationManager.t(if (expanded) "collapse" else "expand")) }
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
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.width(100.dp).height(150.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text(money(maximum), style = MaterialTheme.typography.labelSmall)
                                Text(money((maximum / 2) + (minimum / 2)), style = MaterialTheme.typography.labelSmall)
                                Text(money(minimum), style = MaterialTheme.typography.labelSmall)
                            }
                            Row(Modifier.weight(1f).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items.sortedBy { localizedUkraineRegion(it.region) }.forEach { item ->
                                    val region = localizedUkraineRegion(item.region).ifBlank { LocalizationManager.t("construction_unspecified") }
                                    Column(Modifier.width(156.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Row(Modifier.height(150.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            listOf(item.total, item.eib, item.local).forEachIndexed { index, amount ->
                                                val hint = "$region\n${labels[index]}: ${money(amount)}" +
                                                    if (!item.reconciled && item.total != null && item.eib != null && item.local != null) "\n${LocalizationManager.t("investment_source_difference")}" else ""
                                                OmsTooltipBox(modifier = Modifier.weight(1f), tooltip = { Text(hint) }) {
                                                    Box(Modifier.fillMaxWidth().height(150.dp).chartItemHighlight()
                                                        .semantics { contentDescription = hint }.focusable()
                                                        .pointerHoverIcon(PointerIcon.Hand)) {
                                                        Box(Modifier.fillMaxWidth().offset(y = (150.0 * (maximum - maxOf(0L, amount ?: 0L)) / span).toFloat().dp)
                                                            .height(if (amount == null || amount == 0L) 1.dp else (150.0 * kotlin.math.abs(amount.toDouble()) / span).toFloat().coerceIn(1f, 150f).dp)
                                                            .background(if (amount == null) MaterialTheme.colorScheme.outline else colors[index]))
                                                    }
                                                }
                                            }
                                        }
                                        Text(region, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                        TableScrollControls(scroll)
                    }
                }
            }
        }
    }
}
