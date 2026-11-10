package oms.urp3.service

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
