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
import oms.data.ApiMonthlyActPayment
import oms.localization.LocalizationManager

@Composable
fun MonthlyPaymentsChart(records: List<ApiFinancialRecord>) {
    val data = records
        .filter { it.recordType in setOf("payment", "advance") }
        .mapNotNull { record -> record.amountEurCents?.let { cents -> (record.paymentDate ?: record.recordDate).takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }?.take(7)?.let { month -> month to cents } } }
        .groupBy({ it.first }, { it.second })
        .toList()
        .sortedBy { it.first }
        .map { entry -> BarData(entry.first, entry.second.sum().toFloat()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(LocalizationManager.t("monthly_project_payments"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            if (data.isEmpty()) Text(LocalizationManager.t("no_payments_yet"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else VerticalBarChart(
                data = data,
                color = MaterialTheme.colorScheme.primary,
                labelMaxLines = 2,
                maxVisibleItems = 12,
                initialScrollToEnd = true,
                valueLabel = { value -> formatPaymentAmount(value.toInt().toLong()) }
            )
        }
    }
}

@Composable
fun MonthlyEquipmentPaymentsChart(records: List<ApiFinancialRecord>) {
    MonthlyPurposePaymentsChart(
        records = records,
        purpose = "equipment",
        titleKey = "monthly_equipment_payments",
        hintKey = "monthly_equipment_payments_hint"
    )
}

@Composable
fun MonthlyTechnicalSupervisionPaymentsChart(records: List<ApiFinancialRecord>) {
    MonthlyPurposePaymentsChart(
        records = records,
        purpose = "technical_supervision",
        titleKey = "monthly_technical_supervision_payments",
        hintKey = "monthly_technical_supervision_payments_hint"
    )
}

@Composable
fun MonthlyEngineerConsultantPaymentsChart(records: List<ApiFinancialRecord>) {
    MonthlyPurposePaymentsChart(
        records = records,
        purpose = "engineer_consultant",
        titleKey = "monthly_engineer_consultant_payments",
        hintKey = "monthly_engineer_consultant_payments_hint"
    )
}

@Composable
private fun MonthlyPurposePaymentsChart(
    records: List<ApiFinancialRecord>,
    purpose: String,
    titleKey: String,
    hintKey: String
) {
    val payments = records
        .filter { it.paymentPurpose == purpose && it.recordType in setOf("payment", "advance") }
        .mapNotNull { record -> record.amountEurCents?.let { cents -> (record.paymentDate ?: record.recordDate).takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }?.take(7)?.let { it to cents } } }
        .groupBy({ it.first }, { it.second })
        .map { ApiMonthlyActPayment(it.key, it.value.sum()) }
    if (payments.isEmpty()) return
    MonthlyAmountsChart(titleKey, hintKey, payments)
}

private fun formatPaymentAmount(cents: Long): String =
    "€ " + (cents / 100).toString() + "." + (cents % 100).toString().padStart(2, '0')
