package oms.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import oms.data.ApiFinancialRecord
import oms.data.OmsApiClient
import oms.data.displayAmountCents
import oms.components.CurrencySelector
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
fun MonthlyPaymentsChart(
    records: List<FinancialChartRecord>,
    rates: Map<String, Double> = emptyMap(),
    onMonthClick: (String) -> Unit = {}
) {
    var currency by remember { mutableStateOf("EUR") }
    val aggregation = aggregateMonthlyPayments(records, "works", currency, chartRates(records, "works", currency, rates))
    var expanded by remember { mutableStateOf(true) }
    MonthlyMoneyChart(
        titleKey = "monthly_project_payments",
        hintKey = null,
        currency = currency,
        payments = aggregation.payments,
        tooltipByMonth = aggregation.tooltipByMonth,
        currencySelector = { CurrencySelector(currency) { currency = it } },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        onMonthClick = onMonthClick
    )
}

@Composable
fun MonthlyEquipmentPaymentsChart(
    records: List<FinancialChartRecord>,
    rates: Map<String, Double> = emptyMap(),
    onMonthClick: (String) -> Unit = {}
) {
    MonthlyPurposePaymentsChart(
        records = records,
        rates = rates,
        purpose = "equipment",
        titleKey = "monthly_equipment_payments",
        hintKey = "monthly_equipment_payments_hint",
        onMonthClick = onMonthClick
    )
}

@Composable
fun MonthlyTechnicalSupervisionPaymentsChart(
    records: List<FinancialChartRecord>,
    rates: Map<String, Double> = emptyMap(),
    onMonthClick: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(true) }
    MonthlyPurposePaymentsChart(
        records = records,
        rates = rates,
        purpose = "technical_supervision",
        titleKey = "monthly_technical_supervision_payments",
        hintKey = "monthly_technical_supervision_payments_hint",
        expanded = expanded,
        onExpandedChange = { expanded = it },
        onMonthClick = onMonthClick
    )
}

@Composable
fun MonthlyEngineerConsultantPaymentsChart(
    records: List<FinancialChartRecord>,
    rates: Map<String, Double> = emptyMap(),
    onMonthClick: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(true) }
    MonthlyPurposePaymentsChart(
        records = records,
        rates = rates,
        purpose = "engineer_consultant",
        titleKey = "monthly_engineer_consultant_payments",
        hintKey = "monthly_engineer_consultant_payments_hint",
        // Keep the dedicated CSC chart discoverable even before its first
        // payment is imported. It will show the standard empty-data message
        // and starts plotting automatically as soon as matching payments exist.
        showWhenEmpty = true,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        onMonthClick = onMonthClick
    )
}

@Composable
private fun MonthlyPurposePaymentsChart(
    records: List<FinancialChartRecord>,
    purpose: String,
    titleKey: String,
    hintKey: String,
    rates: Map<String, Double>,
    showWhenEmpty: Boolean = false,
    expanded: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onMonthClick: (String) -> Unit = {}
) {
    var currency by remember { mutableStateOf("EUR") }
    val aggregation = aggregateMonthlyPayments(records, purpose, currency, chartRates(records, purpose, currency, rates))
    if (aggregation.payments.isEmpty() && !showWhenEmpty) return
    MonthlyMoneyChart(
        titleKey, hintKey, aggregation.payments, currency, aggregation.tooltipByMonth,
        currencySelector = { CurrencySelector(currency) { currency = it } },
        expanded = expanded, onExpandedChange = onExpandedChange, onMonthClick = onMonthClick
    )
}

/**
 * A chart owns its currency switch, so it must resolve legacy EUR-to-UAH
 * rates itself instead of relying on the page-level currency selector.
 */
@Composable
private fun chartRates(
    records: List<FinancialChartRecord>,
    purpose: String?,
    currency: String,
    suppliedRates: Map<String, Double>
): Map<String, Double> {
    var fetchedRates by remember(records, purpose) {
        mutableStateOf<Map<String, Double>>(emptyMap())
    }
    LaunchedEffect(records, purpose, currency, suppliedRates) {
        if (currency != "UAH") return@LaunchedEffect
        val missingDates = records.asSequence()
            .map { it.record }
            .filter { it.recordType in setOf("payment", "advance") && (purpose == null || it.paymentPurpose == purpose) }
            .filter { it.currency == "EUR" && (it.eurExchangeRate == null || it.eurExchangeRate == 1.0) }
            .map { it.recordDate }
            .filter { it !in suppliedRates && it !in fetchedRates }
            .distinct()
            .toList()
        val resolved = buildMap {
            missingDates.forEach { date ->
                try {
                    put(date, OmsApiClient.projectExchangeRate(date).uahPerEur.toDouble())
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                }
            }
        }
        if (resolved.isNotEmpty()) fetchedRates = fetchedRates + resolved
    }
    return suppliedRates + fetchedRates
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
    val paymentsWithData = monthlyRecords.entries
        .sortedBy { it.key }
        .map { (month, entries) -> MonthlyMoneyAmount(month, entries.sumOf { it.second }) }
    // A monthly chart must not silently skip calendar gaps, but it must start
    // at its first actual payment rather than adding irrelevant zero months
    // from January onward.
    val payments = completeFinancialMonths(paymentsWithData)
    val tooltipByMonth = monthlyRecords.mapValues { (_, entries) ->
        val codes = entries.map { it.first.subprojectCode }.filter { it.isNotBlank() }.distinct().sorted()
        "${LocalizationManager.t("subproject_codes")}: ${codes.joinToString(", ").ifBlank { "—" }}"
    }
    return MonthlyFinancialAggregation(payments, tooltipByMonth)
}

internal fun completeFinancialMonths(payments: List<MonthlyMoneyAmount>): List<MonthlyMoneyAmount> {
    if (payments.isEmpty()) return emptyList()
    val amounts = payments.associate { it.month to it.amountCents }
    val first = payments.minOf { it.month }
    val last = payments.maxOf { it.month }
    val firstYear = first.substringBefore('-').toIntOrNull() ?: return payments.sortedBy { it.month }
    val firstMonth = first.substringAfter('-', "0").toIntOrNull()?.takeIf { it in 1..12 }
        ?: return payments.sortedBy { it.month }
    val lastYear = last.substringBefore('-').toIntOrNull() ?: return payments.sortedBy { it.month }
    val lastMonth = last.substringAfter('-', "0").toIntOrNull()?.takeIf { it in 1..12 }
        ?: return payments.sortedBy { it.month }
    return buildList {
        for (year in firstYear..lastYear) {
            val initialMonth = if (year == firstYear) firstMonth else 1
            val finalMonth = if (year == lastYear) lastMonth else 12
            for (month in initialMonth..finalMonth) {
                val key = "$year-${month.toString().padStart(2, '0')}"
                add(MonthlyMoneyAmount(key, amounts[key] ?: 0L))
            }
        }
    }
}
