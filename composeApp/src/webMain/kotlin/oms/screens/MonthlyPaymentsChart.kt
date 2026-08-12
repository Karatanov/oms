package oms.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.charts.BarData
import oms.charts.VerticalBarChart
import oms.data.ApiFinancialRecord
import oms.localization.LocalizationManager

@Composable
fun MonthlyPaymentsChart(records: List<ApiFinancialRecord>) {
    val data = records
        .filter { it.recordType in setOf("payment", "advance") }
        .mapNotNull { record -> (record.paymentDate ?: record.recordDate).takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }?.take(7)?.let { it to record.amount } }
        .groupBy({ it.first }, { it.second })
        .toList()
        .sortedBy { it.first }
        .map { entry -> BarData(entry.first, entry.second.sum().toFloat()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(LocalizationManager.t("monthly_project_payments"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            if (data.isEmpty()) Text(LocalizationManager.t("no_payments_yet"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else VerticalBarChart(data, MaterialTheme.colorScheme.primary) { value -> formatPaymentAmount(value.toLong()) }
        }
    }
}

private fun formatPaymentAmount(amount: Long): String =
    "${amount.toString().reversed().chunked(3).joinToString(" ").reversed()} UAH"
