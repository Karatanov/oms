package oms.urp3.service

import java.math.BigDecimal

/** Keep a present-but-unavailable payment distinct from no payment in the selected scope. */
internal fun financialPaymentTotals(rows: List<Pair<Long, Long?>>): Map<Long, Double?> =
    rows.groupBy({ it.first }, { it.second }).mapValues { (_, cents) ->
        if (cents.any { it == null }) null else
            cents.fold(BigDecimal.ZERO) { total, amount -> total.add(BigDecimal.valueOf(checkNotNull(amount))) }
                .movePointLeft(2).toDouble()
    }

internal data class FinancialProgressContribution(val contractUah: Double, val paidUah: Double?)
internal data class FinancialProgressGroupTotal(val contractUah: Double, val paidUah: Double?)

/** Grouping must not revive a zero for an unavailable SP or accumulate binary rounding drift. */
internal fun financialProgressGroupTotal(rows: List<FinancialProgressContribution>): FinancialProgressGroupTotal {
    val base = rows.fold(BigDecimal.ZERO) { total, row -> total.add(BigDecimal.valueOf(row.contractUah)) }.toDouble()
    val paid = if (rows.any { it.paidUah == null || !it.paidUah.isFinite() }) null else
        rows.fold(BigDecimal.ZERO) { total, row -> total.add(BigDecimal.valueOf(checkNotNull(row.paidUah))) }.toDouble()
    return FinancialProgressGroupTotal(base, paid)
}

/** Prefer Works-only source funding over legacy totals that may include services. */
internal fun financialWorksExecutionBase(sourceWorks: Double?, storedContract: Double?, approved: Double): Double =
    listOfNotNull(sourceWorks, storedContract, approved).firstOrNull { it.isFinite() && it > 0.0 } ?: 0.0

/** A parent total and its component lots are alternative bases, never additive. */
internal fun financialProgressSourceWorks(parentAmount: Double?, lotAmounts: List<Double?>): Double? =
    parentAmount?.takeIf { it.isFinite() && it > 0.0 }
        ?: lotAmounts.filterNotNull().filter { it.isFinite() && it > 0.0 }.sum().takeIf { it > 0.0 }

/** A persisted empty register is authoritative, not a reason to revive legacy payments. */
internal fun financialProgressPayments(
    approved: List<PaymentMonitoringWorkbookSource.Transaction>?,
    subprojectIdsByCode: Map<String, Long>,
    year: Int?,
    legacy: () -> Map<Long, Double?>
): Map<Long, Double?> {
    val payments = approved ?: return legacy()
    return financialPaymentTotals(payments.asSequence()
        .filter { it.code.paymentMonitoringContractType() == "W" }
        .filter { year == null || it.paymentDate.year == year }
        .mapNotNull { payment ->
            subprojectIdsByCode[payment.code.paymentMonitoringSubprojectCode().uppercase()]
                ?.let { it to moneyCents(payment.amountUah) }
        }
        .toList())
}
