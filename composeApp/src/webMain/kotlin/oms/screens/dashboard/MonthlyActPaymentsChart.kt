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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import oms.charts.BarData
import oms.charts.VerticalBarChart
import oms.data.ApiMonthlyActPayment
import oms.localization.LocalizationManager

@Composable
fun MonthlyActPaymentsChart(primary: Color, payments: List<ApiMonthlyActPayment>, onOpenFinancial: () -> Unit = {}) {
    val sortedPayments = payments.sortedBy { it.month }
    val yearCenterIndexes = sortedPayments
        .withIndex()
        .groupBy { it.value.month.take(4) }
        .values
        .associate { group -> group[group.size / 2].index to group.first().value.month.take(4) }
    val data = sortedPayments
        .mapIndexed { index, payment ->
            BarData(
                label = payment.month.toMonthName(),
                value = payment.amountEurCents.toFloat(),
                groupLabel = yearCenterIndexes[index]
            )
        }
    Card(modifier = Modifier.fillMaxWidth().height(320.dp), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(LocalizationManager.t("monthly_act_payments"), style = MaterialTheme.typography.titleMedium)
            Text(
                LocalizationManager.t("monthly_act_payments_hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            if (data.isEmpty()) Text(LocalizationManager.t("no_payments_yet"))
            else VerticalBarChart(
                data = data,
                color = primary,
                valueLabel = { formatActAmount(it.toInt().toLong()) },
                labelWidth = 76.dp,
                labelMaxLines = 1,
                onItemClick = { onOpenFinancial() }
            )
        }
    }
}

private fun formatActAmount(cents: Long): String =
    "€ " + (cents / 100).toString() + "." + (cents % 100).toString().padStart(2, '0')
