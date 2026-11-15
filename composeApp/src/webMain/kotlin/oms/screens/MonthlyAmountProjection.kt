package oms.screens

import oms.data.ApiMonthlyActPayment

internal data class MonthlyAmountProjection(
    val payments: List<MonthlyMoneyAmount>,
    val unavailableMonths: Int = 0
)

/** A missing month must not become a zero or disappear from an otherwise complete-looking series. */
internal fun projectMonthlyAmounts(rows: List<ApiMonthlyActPayment>, currency: String): MonthlyAmountProjection {
    val amounts = rows.map { row ->
        val cents = when (currency) {
            "EUR" -> row.amountEurCents.takeIf { row.hasCompleteEurAmounts }
            "UAH" -> row.amountUahCents
            else -> null
        }
        row to cents
    }
    val unavailable = amounts.count { it.second == null }
    if (unavailable > 0) return MonthlyAmountProjection(emptyList(), unavailable)
    return MonthlyAmountProjection(amounts.sortedBy { it.first.month }.map { (row, cents) ->
        MonthlyMoneyAmount(row.month, checkNotNull(cents))
    })
}
