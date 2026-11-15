package oms.urp3.service

import java.math.BigDecimal

internal data class DisbursementNode(val id: Long, val parentId: Long?, val code: String)
internal data class DisbursementPayment(val code: String, val eurCents: Long?, val uah: BigDecimal?, val works: Boolean, val projectId: Long? = null)
internal data class ProjectDisbursement(val eurCents: Long? = 0L, val worksUah: BigDecimal? = BigDecimal.ZERO)

/** One cash projection for list, map and details. A loaded empty source is authoritative.
 * Payments are attributed to their exact lot, then rolled up once to each ancestor.
 */
internal fun projectDisbursements(
    nodes: List<DisbursementNode>,
    approved: List<PaymentMonitoringWorkbookSource.Transaction>?,
    legacy: () -> List<DisbursementPayment>
): Map<Long, ProjectDisbursement> {
    val byId = nodes.associateBy { it.id }
    val byCode = nodes.associateBy { it.code.trim().uppercase() }
    val payments = approved?.map { row ->
        DisbursementPayment(row.code, row.amountEur?.let { moneyCents(it) }, BigDecimal.valueOf(row.amountUah),
            row.code.paymentMonitoringContractType() == "W")
    } ?: legacy()
    val totals = mutableMapOf<Long, ProjectDisbursement>()
    for (payment in payments) {
        val rawCode = payment.code.substringBefore('$').trim()
        val code = (if (payment.code.paymentMonitoringContractType() != null) rawCode.substringBeforeLast('_') else rawCode).uppercase()
        var node = payment.projectId?.let(byId::get) ?: byCode[code] ?: byCode[code.substringBefore('#')] ?: continue
        val visited = mutableSetOf<Long>()
        while (visited.add(node.id)) {
            val before = totals[node.id] ?: ProjectDisbursement()
            totals[node.id] = ProjectDisbursement(
                before.eurCents?.let { total -> payment.eurCents?.let { value -> runCatching { Math.addExact(total, value) }.getOrNull() } },
                if (!payment.works) before.worksUah else before.worksUah?.let { total -> payment.uah?.let(total::add) }
            )
            node = node.parentId?.let(byId::get) ?: break
        }
    }
    return totals
}
