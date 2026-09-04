package oms.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import oms.data.ApiFinancialRecord
import oms.data.ApiMonthlyActPayment
import oms.localization.LocalizationManager

data class FinancialChartRecord(
    val record: ApiFinancialRecord,
    val subprojectCode: String
)

private data class MonthlyFinancialAggregation(
    val payments: List<ApiMonthlyActPayment>,
    val tooltipByMonth: Map<String, String>
)

@Composable
fun MonthlyPaymentsChart(records: List<FinancialChartRecord>) {
    val aggregation = aggregateMonthlyPayments(records, "works")
    var expanded by remember { mutableStateOf(false) }
    MonthlyAmountsChart(
        titleKey = "monthly_project_payments",
        hintKey = null,
        payments = aggregation.payments,
        tooltipByMonth = aggregation.tooltipByMonth,
        expanded = expanded,
        onExpandedChange = { expanded = it }
    )
}

@Composable
fun MonthlyEquipmentPaymentsChart(records: List<FinancialChartRecord>) {
    MonthlyPurposePaymentsChart(
        records = records,
        purpose = "equipment",
        titleKey = "monthly_equipment_payments",
        hintKey = "monthly_equipment_payments_hint"
    )
}

@Composable
fun MonthlyTechnicalSupervisionPaymentsChart(records: List<FinancialChartRecord>) {
    MonthlyPurposePaymentsChart(
        records = records,
        purpose = "technical_supervision",
        titleKey = "monthly_technical_supervision_payments",
        hintKey = "monthly_technical_supervision_payments_hint"
    )
}

@Composable
fun MonthlyEngineerConsultantPaymentsChart(records: List<FinancialChartRecord>) {
    MonthlyPurposePaymentsChart(
        records = records,
        purpose = "engineer_consultant",
        titleKey = "monthly_engineer_consultant_payments",
        hintKey = "monthly_engineer_consultant_payments_hint"
    )
}

@Composable
private fun MonthlyPurposePaymentsChart(
    records: List<FinancialChartRecord>,
    purpose: String,
    titleKey: String,
    hintKey: String
) {
    val aggregation = aggregateMonthlyPayments(records, purpose)
    if (aggregation.payments.isEmpty()) return
    MonthlyAmountsChart(titleKey, hintKey, aggregation.payments, aggregation.tooltipByMonth)
}

private fun aggregateMonthlyPayments(
    records: List<FinancialChartRecord>,
    purpose: String? = null
): MonthlyFinancialAggregation {
    val monthlyRecords = records
        .filter { it.record.recordType in setOf("payment", "advance") && (purpose == null || it.record.paymentPurpose == purpose) }
        .mapNotNull { chartRecord ->
            chartRecord.record.amountEurCents?.let { cents ->
                (chartRecord.record.paymentDate ?: chartRecord.record.recordDate)
                    .takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                    ?.take(7)
                    ?.let { month -> month to (chartRecord to cents) }
            }
        }
        .groupBy({ it.first }, { it.second })
    val payments = monthlyRecords.entries
        .sortedBy { it.key }
        .map { (month, entries) -> ApiMonthlyActPayment(month, entries.sumOf { it.second }) }
    val tooltipByMonth = monthlyRecords.mapValues { (_, entries) ->
        val codes = entries.map { it.first.subprojectCode }.filter { it.isNotBlank() }.distinct().sorted()
        "${LocalizationManager.t("subproject_codes")}: ${codes.joinToString(", ").ifBlank { "—" }}"
    }
    return MonthlyFinancialAggregation(payments, tooltipByMonth)
}
