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
fun MonthlyActPaymentsChart(primary: Color, payments: List<ApiMonthlyActPayment>) {
    val data = payments.map { BarData(it.month.toStartMonthLabel(), it.amount.toFloat()) }
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
            else VerticalBarChart(data = data, color = primary, valueLabel = { "${it.toLong()} ₴" })
        }
    }
}
