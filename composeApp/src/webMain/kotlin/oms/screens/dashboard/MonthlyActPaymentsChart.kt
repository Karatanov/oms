package oms.screens.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import oms.charts.BarData
import oms.charts.VerticalBarChart
import oms.charts.HorizontalBarChart
import oms.charts.BarChartOrientation
import oms.data.ApiMonthlyActPayment
import oms.localization.LocalizationManager
import oms.components.CurrencySelector

@Composable
fun MonthlyActPaymentsChart(primary: Color, payments: List<ApiMonthlyActPayment>, onOpenFinancial: () -> Unit = {}, orientation: BarChartOrientation = BarChartOrientation.Vertical) {
    var currency by remember { mutableStateOf("EUR") }
    val sortedPayments = payments.sortedBy { it.month }
    val data = sortedPayments
        .map { payment ->
            val amountCents = if (currency == "EUR") payment.amountEurCents else payment.amountUahCents ?: 0L
            BarData(
                label = payment.month.toMonthName(),
                value = amountCents.toFloat(),
                groupLabel = payment.month.take(4),
                formattedValue = formatActAmount(amountCents, currency)
            )
        }
    Card(modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp), shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                Text(
                    LocalizationManager.t("monthly_act_payments"),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
            Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                CurrencySelector(currency) { currency = it }
            }
            Spacer(Modifier.height(16.dp))
            if (data.isEmpty()) Text(LocalizationManager.t("no_payments_yet"))
            else if (orientation == BarChartOrientation.Vertical) VerticalBarChart(
                data = data, color = primary, valueLabel = { formatActAmount(it.toLong(), currency) }, labelMaxLines = 1, onItemClick = { onOpenFinancial() }
            ) else HorizontalBarChart(data, primary, valueLabel = { formatActAmount(it.toLong(), currency) }, onItemClick = { onOpenFinancial() })
        }
    }
}

private fun formatActAmount(cents: Long, currency: String): String =
    if (currency == "EUR") "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    else (cents / 100).toString()
