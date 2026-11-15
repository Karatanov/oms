package oms.urp3.service

import oms.urp3.domain.FinancialRecord
import oms.urp3.domain.FinancialRecordType
import oms.urp3.domain.Project
import oms.urp3.domain.ProjectMonitoringDetails
import java.math.BigDecimal

data class ProjectFinancialExecution(val paidUah: Double?, val baseUah: Double)

/** Same Works-only source, lot attribution and denominator precedence as financial progress charts. */
internal fun projectFinancialExecution(
    project: Project,
    monitoring: ProjectMonitoringDetails?,
    partSources: List<ProjectMonitoringDetails> = emptyList(),
    approvedPayments: List<PaymentMonitoringWorkbookSource.Transaction>? = null,
    legacy: () -> List<FinancialRecord>
): ProjectFinancialExecution {
    fun sourceAmount(source: ProjectMonitoringDetails?): Double? =
        source?.registryValues?.get("constructionAmount")?.let(::monitoringSourceAmount)
    val sourceWorks = financialProgressSourceWorks(sourceAmount(monitoring), partSources.map(::sourceAmount))
    val base = financialWorksExecutionBase(sourceWorks, project.subprojectContractAmount?.toDouble(), project.budgetPlanned.toDouble())
    val paid = if (approvedPayments != null) {
        // A parent includes its lots; an individual part must not inherit sibling payments.
        projectDisbursements(listOf(DisbursementNode(project.id, null, project.siteNumber)), approvedPayments) {
            error("A loaded approved register must not read the legacy ledger")
        }[project.id]?.worksUah?.toDouble() ?: 0.0
    } else {
        val amounts = legacy().asSequence()
            .filter { it.recordType in setOf(FinancialRecordType.PAYMENT, FinancialRecordType.ADVANCE) }
            .filter { it.paymentPurpose.equals("works", true) }
            .map { financialDisplayCents(it, "UAH") }.toList()
        if (amounts.any { it == null }) null
        else amounts.fold(BigDecimal.ZERO) { total, cents -> total.add(BigDecimal.valueOf(checkNotNull(cents))) }
            .movePointLeft(2).toDouble()
    }
    return ProjectFinancialExecution(paid, base)
}
