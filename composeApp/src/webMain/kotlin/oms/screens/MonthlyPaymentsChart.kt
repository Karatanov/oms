package oms.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import oms.data.ApiFinancialRecord
import oms.data.displayAmountCents
import oms.localization.LocalizationManager

data class FinancialChartRecord(
    val record: ApiFinancialRecord,
    val subprojectCode: String
)

internal data class MonthlyFinancialAggregation(
    val payments: List<MonthlyMoneyAmount>,
    val tooltipByMonth: Map<String, String>
)

@Composable
fun MonthlyPaymentsChart(records: List<FinancialChartRecord>, currency: String = "EUR", rates: Map<String, Double> = emptyMap()) {
    val aggregation = aggregateMonthlyPayments(records, "works", currency, rates)
    var expanded by remember { mutableStateOf(true) }
    MonthlyMoneyChart(
        titleKey = "monthly_project_payments",
        hintKey = null,
        currency = currency,
        payments = aggregation.payments,
        tooltipByMonth = aggregation.tooltipByMonth,
        expanded = expanded,
        onExpandedChange = { expanded = it }
    )
}

@Composable
fun MonthlyEquipmentPaymentsChart(records: List<FinancialChartRecord>, currency: String = "EUR", rates: Map<String, Double> = emptyMap()) {
    MonthlyPurposePaymentsChart(
        records = records,
        currency = currency, rates = rates,
        purpose = "equipment",
        titleKey = "monthly_equipment_payments",
        hintKey = "monthly_equipment_payments_hint"
    )
}

@Composable
fun MonthlyTechnicalSupervisionPaymentsChart(records: List<FinancialChartRecord>, currency: String = "EUR", rates: Map<String, Double> = emptyMap()) {
    var expanded by remember { mutableStateOf(true) }
    MonthlyPurposePaymentsChart(
        records = records,
        currency = currency, rates = rates,
        purpose = "technical_supervision",
        titleKey = "monthly_technical_supervision_payments",
        hintKey = "monthly_technical_supervision_payments_hint",
        expanded = expanded,
        onExpandedChange = { expanded = it }
    )
}

@Composable
fun MonthlyEngineerConsultantPaymentsChart(records: List<FinancialChartRecord>, currency: String = "EUR", rates: Map<String, Double> = emptyMap()) {
    MonthlyPurposePaymentsChart(
        records = records,
        currency = currency, rates = rates,
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
    hintKey: String,
    currency: String,
    rates: Map<String, Double>,
    expanded: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    val aggregation = aggregateMonthlyPayments(records, purpose, currency, rates)
    if (aggregation.payments.isEmpty()) return
    MonthlyMoneyChart(titleKey, hintKey, aggregation.payments, currency, aggregation.tooltipByMonth, expanded, onExpandedChange)
}

internal fun aggregateMonthlyPayments(
    records: List<FinancialChartRecord>,
    purpose: String? = null,
    currency: String = "EUR",
    rates: Map<String, Double> = emptyMap()
): MonthlyFinancialAggregation {
    val monthlyRecords = records
        .filter { it.record.recordType in setOf("payment", "advance") && (purpose == null || it.record.paymentPurpose == purpose) }
        .mapNotNull { chartRecord ->
            chartRecord.record.displayAmountCents(currency, rates[chartRecord.record.recordDate])?.let { cents ->
                (chartRecord.record.paymentDate ?: chartRecord.record.recordDate)
                    .takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                    ?.take(7)
                    ?.let { month -> month to (chartRecord to cents) }
            }
        }
        .groupBy({ it.first }, { it.second })
    val payments = monthlyRecords.entries
        .sortedBy { it.key }
        .map { (month, entries) -> MonthlyMoneyAmount(month, entries.sumOf { it.second }) }
    val tooltipByMonth = monthlyRecords.mapValues { (_, entries) ->
        val codes = entries.map { it.first.subprojectCode }.filter { it.isNotBlank() }.distinct().sorted()
        "${LocalizationManager.t("subproject_codes")}: ${codes.joinToString(", ").ifBlank { "—" }}"
    }
    return MonthlyFinancialAggregation(payments, tooltipByMonth)
}
