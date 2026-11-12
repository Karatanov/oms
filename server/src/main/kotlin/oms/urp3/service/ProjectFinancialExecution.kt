package oms.urp3.service

import oms.urp3.domain.FinancialRecord
import oms.urp3.domain.FinancialRecordType
import oms.urp3.domain.Project
import oms.urp3.domain.ProjectMonitoringDetails

data class ProjectFinancialExecution(val paidUah: Double, val baseUah: Double)

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
    val paid = financialProgressPayments(approvedPayments, mapOf(project.siteNumber.substringBefore('#').trim().uppercase() to project.id), null) {
        mapOf(project.id to legacy().asSequence()
            .filter { it.recordType in setOf(FinancialRecordType.PAYMENT, FinancialRecordType.ADVANCE) }
            .filter { it.paymentPurpose.equals("works", true) }
            .mapNotNull { financialDisplayCents(it, "UAH") }
            .sum().toDouble() / 100.0)
    }[project.id] ?: 0.0
    return ProjectFinancialExecution(paid, base)
}
