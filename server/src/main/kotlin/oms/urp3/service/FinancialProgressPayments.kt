package oms.urp3.service

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
    legacy: () -> Map<Long, Double>
): Map<Long, Double> {
    val payments = approved ?: return legacy()
    return payments.asSequence()
        .filter { it.code.paymentMonitoringContractType() == "W" }
        .filter { year == null || it.paymentDate.year == year }
        .mapNotNull { payment ->
            subprojectIdsByCode[payment.code.paymentMonitoringSubprojectCode().uppercase()]
                ?.let { it to payment.amountUah }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, amounts) -> amounts.sum() }
}
