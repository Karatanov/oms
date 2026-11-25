package oms.screens

import oms.data.ApiPaymentMonitoringItem
import oms.urp3.canonicalUkraineRegion
import oms.urp3.decimalMoneyCents
import oms.urp3.paymentSubprojectKey

internal data class RegionalInvestment(val region: String, val total: Long?, val eib: Long?, val local: Long?) {
    val reconciled: Boolean get() = total != null && eib != null && local != null && total - eib == local
}

/** Investment budgets are cumulative, unlike dated cash transactions. Month/purpose do not alter budgets. */
internal fun regionalInvestment(items: List<ApiPaymentMonitoringItem>, projects: List<PaymentProjectContext>, scope: PaymentMonitoringScope): List<RegionalInvestment> {
    val byCode = projects.associateBy { it.code.trim().uppercase() }
    return scopePaymentItems(items, projects, scope)
        .groupBy { canonicalUkraineRegion(byCode[paymentSubprojectKey(it.code).uppercase()]?.region.orEmpty()) }
        .map { (region, rows) ->
            fun cents(value: (ApiPaymentMonitoringItem) -> Double?) = sumRollupPayments(rows, value)?.let { decimalMoneyCents(it) }
            RegionalInvestment(region, cents { it.totalInvestmentUah }, cents { it.eibInvestmentUah }, cents { it.localCoFinancingUah })
        }
}
