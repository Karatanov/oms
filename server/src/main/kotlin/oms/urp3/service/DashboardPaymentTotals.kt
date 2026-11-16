package oms.urp3.service

import java.math.BigDecimal

internal data class DashboardPaymentTotals(val eurCents: Long?, val uahCents: Long?)

/** Preserve completeness per currency. Neither missing cents nor overflow is a zero payment. */
internal fun dashboardPaymentTotals(rows: List<Pair<Long?, Long?>>): DashboardPaymentTotals {
    fun completeTotal(values: List<Long?>): Long? {
        if (values.any { it == null }) return null
        // Add before range checking so negative corrections cannot make the result order-dependent.
        val total = values.fold(BigDecimal.ZERO) { sum, cents -> sum.add(BigDecimal.valueOf(checkNotNull(cents))) }
        return try { total.longValueExact() } catch (_: ArithmeticException) { null }
    }
    return DashboardPaymentTotals(completeTotal(rows.map { it.first }), completeTotal(rows.map { it.second }))
}
