package oms.screens

import oms.data.ApiPaymentMonitoringTransaction
import oms.urp3.canonicalUkraineRegion
import oms.urp3.decimalMoneyCents
import oms.urp3.paymentSubprojectKey

internal data class RegionalFinancingProject(val context: PaymentProjectContext, val plannedCents: Long?)
internal data class RegionalFinancingAmount(val region: String, val plannedCents: Long?, val paidCents: Long?)

/** Plans have SP grain; cash has transaction/lot grain. Never add both parent and component budgets. */
internal fun regionalFinancing(
    projects: List<RegionalFinancingProject>,
    payments: List<ApiPaymentMonitoringTransaction>?,
    scope: PaymentMonitoringScope
): List<RegionalFinancingAmount> {
    val selectedIds = scopePaymentProjects(projects.map { it.context }, scope).map { it.uuid }.toSet()
    val selected = projects.filter { it.context.uuid in selectedIds && '#' !in it.context.code }
        .distinctBy { it.context.code.trim().uppercase() }
    val byCode = selected.associateBy { it.context.code.trim().uppercase() }
    val paidByRegion = payments?.let { source ->
        scopePaymentTransactions(source, selected.map { it.context }, scope)
            .filter { sourcePaymentPurpose(it.code) in setOf("works", "technical_supervision", "engineer_consultant") }
            .mapNotNull { row -> byCode[paymentSubprojectKey(row.code).uppercase()]?.let { project ->
                canonicalUkraineRegion(project.context.region) to decimalMoneyCents(row.amountUah)
            } }.groupBy({ it.first }, { it.second })
    }
    fun sum(values: List<Long?>): Long? {
        var total = 0L
        for (value in values) {
            if (value == null || (value > 0 && total > Long.MAX_VALUE - value) ||
                (value < 0 && total < Long.MIN_VALUE - value)) return null
            total += value
        }
        return total
    }
    return selected.groupBy { canonicalUkraineRegion(it.context.region) }.map { (region, rows) ->
        RegionalFinancingAmount(region, sum(rows.map { it.plannedCents }),
            if (payments == null) null else sum(paidByRegion?.get(region).orEmpty()))
    }
}
