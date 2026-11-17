package oms.screens

import oms.data.ApiPaymentMonitoringItem
import oms.data.ApiPaymentMonitoringTransaction
import oms.urp3.decimalMoneyCents

/** Presentation-only footer over the full filtered selection, not its page.
 * Sum filled monetary cells; average filled percentage cells. Missing cells
 * are not zero observations; a real zero participates in the average.
 */
internal fun paymentMonitoringTotals(rows: List<ApiPaymentMonitoringItem>): ApiPaymentMonitoringItem = ApiPaymentMonitoringItem(
    code = "",
    totalInvestmentUah = sumTableMoney(rows.map { it.totalInvestmentUah }),
    eibInvestmentUah = sumTableMoney(rows.map { it.eibInvestmentUah }),
    localCoFinancingUah = sumTableMoney(rows.map { it.localCoFinancingUah }),
    paidAndInProgressUah = sumTableMoney(rows.map { it.paidAndInProgressUah }),
    paidUah = sumTableMoney(rows.map { it.paidUah }),
    remainingUah = sumTableMoney(rows.map { it.remainingUah }),
    plannedEibSharePct = averageTablePercent(rows.map { it.plannedEibSharePct }),
    factualEibSharePct = averageTablePercent(rows.map { it.factualEibSharePct })
)

internal data class PaymentTransactionTotals(val amountUah: Double?, val amountEur: Double?)

internal fun paymentTransactionTotals(rows: List<ApiPaymentMonitoringTransaction>) = PaymentTransactionTotals(
    sumTableMoney(rows.map { it.amountUah }), sumTableMoney(rows.map { it.amountEur })
)

private fun sumTableMoney(values: List<Double?>): Double? {
    var total = 0L
    var filled = false
    for (value in values) {
        if (value == null) continue
        val cents = decimalMoneyCents(value) ?: return null
        if ((cents > 0 && total > Long.MAX_VALUE - cents) || (cents < 0 && total < Long.MIN_VALUE - cents)) return null
        total += cents
        filled = true
    }
    return if (filled || values.isEmpty()) total / 100.0 else null
}

private fun averageTablePercent(values: List<Double?>): Double? {
    val filled = values.filterNotNull()
    if (filled.isEmpty() || filled.any { !it.isFinite() }) return null
    return filled.sumOf { it / filled.size }.takeIf { it.isFinite() }
}
