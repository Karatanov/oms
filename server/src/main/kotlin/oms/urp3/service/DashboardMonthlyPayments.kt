package oms.urp3.service

import java.time.LocalDate
import java.time.YearMonth

data class MonthlyActPayment(
    val month: String, val amountEurCents: Long, val amountUahCents: Long? = null,
    val hasCompleteEurAmounts: Boolean = true
)

internal data class DashboardPaymentAmount(
    val date: LocalDate, val eurCents: Long?, val uahCents: Long?, val batchId: Int? = null
)

/** Preserve currency availability independently; an unknown conversion is not a zero payment. */
internal fun dashboardMonthlyPayments(rows: List<DashboardPaymentAmount>, tranche: Int? = null): List<MonthlyActPayment> {
    val selected = rows.filter { row -> when (tranche) {
        null -> true
        1, 8 -> row.batchId in setOf(1, 8)
        2, 9 -> row.batchId in setOf(2, 9)
        else -> false
    } }.groupBy { YearMonth.from(it.date) }
    if (selected.isEmpty()) return emptyList()
    val start = selected.keys.min()
    val end = selected.keys.max()
    fun completeSum(values: List<Long?>): Long? {
        var total = 0L
        for (value in values) {
            if (value == null) return null
            if ((value > 0 && total > Long.MAX_VALUE - value) || (value < 0 && total < Long.MIN_VALUE - value)) return null
            total += value
        }
        return total
    }
    return generateSequence(start) { it.plusMonths(1) }.takeWhile { it <= end }.map { month ->
        val payments = selected[month].orEmpty()
        val eur = completeSum(payments.map { it.eurCents })
        val uah = completeSum(payments.map { it.uahCents })
        // EUR's non-null wire field is retained for cached clients; its flag is authoritative.
        MonthlyActPayment(month.toString(), eur ?: 0L, uah, eur != null)
    }.toList()
}
