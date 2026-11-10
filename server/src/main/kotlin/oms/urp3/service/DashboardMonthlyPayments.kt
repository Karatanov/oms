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
    return generateSequence(start) { it.plusMonths(1) }.takeWhile { it <= end }.map { month ->
        val payments = selected[month].orEmpty()
        MonthlyActPayment(month.toString(), payments.sumOf { it.eurCents ?: 0L },
            payments.takeIf { group -> group.all { it.uahCents != null } }?.sumOf { it.uahCents!! },
            payments.all { it.eurCents != null })
    }.toList()
}
