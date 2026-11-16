package oms.urp3.service

import java.math.BigDecimal

/**
 * G is the source amount paid. J (factual share) is F/B, an execution ratio,
 * not the EIB contribution to G. Multiplying G by J understates actual payments.
 * Component W/TS/CSC rows are already included in their parent and are excluded.
 */
internal fun primaryMonitoringPaidUah(
    items: List<PaymentMonitoringWorkbookSource.Item>,
    selectedSubprojectCodes: Set<String>
): Double = dashboardAmountTotal(items.asSequence()
    .filter { it.code.paymentMonitoringContractType() == null }
    .filter { it.code.paymentMonitoringSubprojectCode().uppercase() in selectedSubprojectCodes }
    .mapNotNull { it.paidUah?.takeIf(Double::isFinite) }
    .map(BigDecimal::valueOf))
