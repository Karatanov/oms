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
    val tooltipByMonth: Map<String, String>,
    val unavailableRecords: Int = 0
)

private data class MonthlyChartState(val aggregation: MonthlyFinancialAggregation, val loadingRates: Boolean, val retry: () -> Unit)
private data class ChartRateState(val rates: Map<String, Double>, val loading: Boolean, val retry: () -> Unit)

@Composable
fun MonthlyPaymentsChart(
    records: List<FinancialChartRecord>,
    rates: Map<String, Double> = emptyMap(),
    regionName: String? = null,
    onMonthClick: (String) -> Unit = {}
) {
    var currency by remember { mutableStateOf("EUR") }
    val state = rememberMonthlyPayments(records, "works", currency, rates)
    val aggregation = state.aggregation
    var expanded by remember { mutableStateOf(true) }
    MonthlyMoneyChart(
        titleKey = "monthly_project_payments",
        hintKey = "monthly_project_payments_hint",
        currency = currency,
        titleSuffix = regionName,
        payments = aggregation.payments,
        tooltipByMonth = aggregation.tooltipByMonth,
        currencySelector = { CurrencySelector(currency) { currency = it } },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        onMonthClick = onMonthClick,
        emptyContent = { MonthlyChartAvailability(state, currency) }
    )
}

@Composable
fun MonthlyEquipmentPaymentsChart(
    records: List<FinancialChartRecord>,
    rates: Map<String, Double> = emptyMap(),
    regionName: String? = null,
    onMonthClick: (String) -> Unit = {}
) {
    MonthlyPurposePaymentsChart(
        records = records,
        rates = rates,
        purpose = "equipment",
        titleKey = "monthly_equipment_payments",
        hintKey = "monthly_equipment_payments_hint",
        regionName = regionName,
        onMonthClick = onMonthClick
    )
}

@Composable
fun MonthlyTechnicalSupervisionPaymentsChart(
    records: List<FinancialChartRecord>,
    rates: Map<String, Double> = emptyMap(),
    regionName: String? = null,
    onMonthClick: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(true) }
    MonthlyPurposePaymentsChart(
        records = records,
        rates = rates,
        purpose = "technical_supervision",
        titleKey = "monthly_technical_supervision_payments",
        hintKey = "monthly_technical_supervision_payments_hint",
        regionName = regionName,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        onMonthClick = onMonthClick
    )
}

@Composable
fun MonthlyEngineerConsultantPaymentsChart(
    records: List<FinancialChartRecord>,
    rates: Map<String, Double> = emptyMap(),
    regionName: String? = null,
    onMonthClick: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(true) }
    MonthlyPurposePaymentsChart(
        records = records,
        rates = rates,
        purpose = "engineer_consultant",
        titleKey = "monthly_engineer_consultant_payments",
        hintKey = "monthly_engineer_consultant_payments_hint",
        regionName = regionName,
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
    regionName: String? = null,
    showWhenEmpty: Boolean = false,
    expanded: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onMonthClick: (String) -> Unit = {}
) {
    var currency by remember { mutableStateOf("EUR") }
    val state = rememberMonthlyPayments(records, purpose, currency, rates)
    val aggregation = state.aggregation
    if (aggregation.payments.isEmpty() && aggregation.unavailableRecords == 0 && !state.loadingRates && !showWhenEmpty) return
    MonthlyMoneyChart(
        titleKey, hintKey, aggregation.payments, currency, titleSuffix = regionName, tooltipByMonth = aggregation.tooltipByMonth,
        currencySelector = { CurrencySelector(currency) { currency = it } },
        expanded = expanded, onExpandedChange = onExpandedChange, onMonthClick = onMonthClick,
        emptyContent = { MonthlyChartAvailability(state, currency) }
    )
}

@Composable
private fun MonthlyChartAvailability(state: MonthlyChartState, currency: String) {
    if (state.loadingRates) oms.components.ContentState(LocalizationManager.t("financial_rates_loading"), loading = true)
    else if (state.aggregation.unavailableRecords > 0) oms.components.ContentState(
        LocalizationManager.t("financial_chart_incomplete")
            .replace("{count}", state.aggregation.unavailableRecords.toString())
            .replace("{currency}", LocalizationManager.currencyLabel(currency)),
        error = true, onRetry = state.retry
    ) else androidx.compose.material3.Text(LocalizationManager.t("no_chart_data"))
}

/** Timer ticks and expand/collapse changes do not alter payment aggregates. */
@Composable
private fun rememberMonthlyPayments(
    records: List<FinancialChartRecord>,
    purpose: String,
    currency: String,
    suppliedRates: Map<String, Double>
): MonthlyChartState {
    val resolution = chartRates(records, purpose, currency, suppliedRates)
    // Tooltips contain localized text, so language changes must invalidate them.
    val aggregation = remember(records, purpose, currency, resolution.rates, LocalizationManager.currentLanguage) {
        aggregateMonthlyPayments(records, purpose, currency, resolution.rates)
    }
    return MonthlyChartState(aggregation, resolution.loading, resolution.retry)
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
): ChartRateState {
    var fetchedRates by remember(records, purpose) {
        mutableStateOf<Map<String, Double>>(emptyMap())
    }
    var loading by remember(records, purpose, currency) { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    val available = validChartRates(fetchedRates, suppliedRates)
    LaunchedEffect(records, purpose, currency, suppliedRates, retry) {
        val missingDates = missingChartRateDates(records, purpose, currency, available)
        if (missingDates.isEmpty()) { loading = false; return@LaunchedEffect }
        loading = true
        try {
            // Bound the whole batch, not 75 seconds per date. A timed-out or
            // failed lookup stays unavailable and exposes the chart's retry action.
            val resolved = resolveMonthlyChartRates(missingDates) { date ->
                OmsApiClient.projectExchangeRate(date).uahPerEur.toDoubleOrNull()
            }
            if (resolved.isNotEmpty()) fetchedRates = fetchedRates + resolved
        } finally {
            loading = false
        }
    }
    return ChartRateState(available, loading) { retry++ }
}

internal fun aggregateMonthlyPayments(
    records: List<FinancialChartRecord>,
    purpose: String? = null,
    currency: String = "EUR",
    rates: Map<String, Double> = emptyMap()
): MonthlyFinancialAggregation {
    var unavailable = 0
    val monthlyRecords = records
        .filter { it.record.recordType in setOf("payment", "advance") && (purpose == null || it.record.paymentPurpose == purpose) }
        .mapNotNull { chartRecord ->
            val cents = chartRecord.record.displayAmountCents(currency, rates[chartRecord.record.recordDate])
            val month = paymentChartMonth(chartRecord.record.paymentDate ?: chartRecord.record.recordDate)
            if (cents == null || month == null) { unavailable++; null }
            else month to (chartRecord to cents)
        }
        .groupBy({ it.first }, { it.second })
    // Never turn a missing conversion/date into an understated bar or a gap zero.
    if (unavailable > 0) return MonthlyFinancialAggregation(emptyList(), emptyMap(), unavailable)
    val paymentsWithData = monthlyRecords.entries
        .sortedBy { it.key }
        .map { (month, entries) ->
            var total = 0L
            for ((_, value) in entries) {
                if ((value > 0 && total > Long.MAX_VALUE - value) || (value < 0 && total < Long.MIN_VALUE - value))
                    return MonthlyFinancialAggregation(emptyList(), emptyMap(), entries.size)
                total += value
            }
            MonthlyMoneyAmount(month, total)
        }
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

internal fun validChartRates(fetched: Map<String, Double>, supplied: Map<String, Double>): Map<String, Double> =
    (fetched.filterValues { it.isFinite() && it > 0.0 } + supplied.filterValues { it.isFinite() && it > 0.0 })

internal fun missingChartRateDates(records: List<FinancialChartRecord>, purpose: String?, currency: String, rates: Map<String, Double>): List<String> =
    records.asSequence().map { it.record }
        .filter { it.recordType in setOf("payment", "advance") && (purpose == null || it.paymentPurpose == purpose) }
        .filter { it.amount.isFinite() && it.currency in setOf("EUR", "UAH") && it.currency != currency }
        .filter { it.displayAmountCents(currency, rates[it.recordDate]) == null }
        .map { it.recordDate }.distinct().toList()

private fun paymentChartMonth(date: String): String? {
    if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return null
    val year = date.take(4).toInt()
    val month = date.substring(5, 7).toInt()
    val day = date.takeLast(2).toInt()
    if (year !in 1..9999 || month !in 1..12) return null
    val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val days = when (month) { 2 -> if (leap) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
    return date.take(7).takeIf { day in 1..days }
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
