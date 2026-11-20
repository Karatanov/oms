package oms.screens.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import oms.charts.BarData
import oms.charts.VerticalBarChart
import oms.charts.HorizontalBarChart
import oms.charts.BarChartOrientation
import oms.data.ApiMonthlyActPayment
import oms.localization.LocalizationManager
import oms.components.CurrencySelector
import oms.components.ChartPngExportButton
import oms.components.OmsChartHeader
import oms.components.formatGroupedNumber
import oms.screens.projectMonthlyAmounts
import oms.screens.MonthlyAmountsUnavailable
import kotlin.math.roundToLong

@Composable
fun MonthlyActPaymentsChart(primary: Color, payments: List<ApiMonthlyActPayment>, onOpenFinancialByMonth: (String) -> Unit = {}, orientation: BarChartOrientation = BarChartOrientation.Vertical, onRetry: (() -> Unit)? = null) {
    var currency by remember { mutableStateOf("EUR") }
    val projection = projectMonthlyAmounts(payments, currency)
    val data = projection.payments
        .map { payment ->
            val amountCents = payment.amountCents
            BarData(
                label = payment.month.toMonthName(),
                value = amountCents.toFloat(),
                id = payment.month,
                groupLabel = payment.month.take(4),
                formattedValue = formatActAmount(amountCents, currency)
            )
        }
    val cardModifier = Modifier.fillMaxWidth()
    Card(modifier = cardModifier, shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(8.dp)) {
            OmsChartHeader(LocalizationManager.t("monthly_act_payments")) {
                CurrencySelector(currency) { currency = it }
                if (data.isNotEmpty()) {
                    ChartPngExportButton(
                        title = LocalizationManager.t("monthly_act_payments"),
                        filenameStem = "monthly-act-payments",
                        data = data,
                        orientation = orientation,
                        showDataLabels = true,
                        valueLabel = { formatActAmount(it.toLong(), currency) },
                        currency = currency
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (data.isEmpty()) {
                if (projection.unavailableMonths > 0) MonthlyAmountsUnavailable(projection.unavailableMonths, currency, onRetry)
                else Text(LocalizationManager.t("no_payments_yet"))
            }
            else if (orientation == BarChartOrientation.Vertical) VerticalBarChart(
                data = data, color = primary, valueLabel = { formatActAmount(it.toLong(), currency) }, labelMaxLines = 1,
                onItemClick = { bar -> bar.id?.let(onOpenFinancialByMonth) }
            ) else HorizontalBarChart(data, primary, valueLabel = { formatActAmount(it.toLong(), currency) }, onItemClick = { bar -> bar.id?.let(onOpenFinancialByMonth) })
        }
    }
}

private fun formatActAmount(cents: Long, currency: String): String =
    formatGroupedNumber((cents / 100.0).roundToLong())
