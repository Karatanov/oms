package oms.screens

import oms.data.ApiPaymentMonitoringItem
import oms.data.ApiPaymentMonitoringTransaction
import oms.urp3.canonicalUkraineRegion
import oms.urp3.paymentSubprojectKey

internal data class PaymentProjectContext(val uuid: String, val code: String, val tranche: Int, val region: String)
internal data class PaymentMonitoringScope(
    val tranche: Int? = null, val subproject: String? = null, val region: String? = null,
    val year: Int? = null, val month: String? = null, val purpose: String? = null
)

internal fun sourcePaymentPurpose(code: String): String? = when (code.substringBefore('$').substringAfterLast('_').uppercase()) {
    "W" -> "works"
    "TS" -> "technical_supervision"
    "CSC" -> "engineer_consultant"
    "E", "EQUIPMENT" -> "equipment"
    else -> null
}

private fun PaymentMonitoringScope.matchesProject(project: PaymentProjectContext?, batch: Int? = null): Boolean {
    if (subproject != null && project?.uuid != subproject) return false
    if (region != null && (project == null || canonicalUkraineRegion(project.region) != canonicalUkraineRegion(region))) return false
    val sourceTranche = project?.tranche ?: batch
    return tranche == null || when (tranche) {
        1, 8 -> sourceTranche in setOf(1, 8)
        2, 9 -> sourceTranche in setOf(2, 9)
        else -> false
    }
}

/** Cumulative indicators have no month/purpose grain: restrict them only to the selected SP population. */
internal fun scopePaymentProjects(projects: List<PaymentProjectContext>, scope: PaymentMonitoringScope): List<PaymentProjectContext> =
    projects.filter { scope.matchesProject(it) }

internal fun scopePaymentItems(items: List<ApiPaymentMonitoringItem>, projects: List<PaymentProjectContext>, scope: PaymentMonitoringScope): List<ApiPaymentMonitoringItem> {
    val byCode = projects.associateBy { it.code.trim().uppercase() }
    return items.filter { scope.matchesProject(byCode[paymentSubprojectKey(it.code).uppercase()]) }
}

/** Same scope for a chart's click and its auditable transaction rows; source amounts stay unchanged. */
internal fun scopePaymentTransactions(rows: List<ApiPaymentMonitoringTransaction>, projects: List<PaymentProjectContext>, scope: PaymentMonitoringScope): List<ApiPaymentMonitoringTransaction> {
    val byCode = projects.associateBy { it.code.trim().uppercase() }
    return rows.filter { row ->
        scope.matchesProject(byCode[paymentSubprojectKey(row.code).uppercase()], row.batchId) &&
            (scope.year == null || row.paymentDate.take(4).toIntOrNull() == scope.year) &&
            (scope.month == null || row.paymentDate.startsWith(scope.month)) &&
            (scope.purpose == null || sourcePaymentPurpose(row.code) == scope.purpose)
    }
}
