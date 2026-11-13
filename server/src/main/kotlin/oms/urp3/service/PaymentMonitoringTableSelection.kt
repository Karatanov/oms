package oms.urp3.service

import java.util.Locale

internal enum class PaymentMonitoringExportSort {
    Code, Total, Eib, Local, PaidAndInProgress, Paid, Remaining, PlannedShare, FactualShare, Compliance
}

internal data class PaymentMonitoringExportQuery(
    val code: String,
    val sort: PaymentMonitoringExportSort,
    val ascending: Boolean
)

internal fun paymentMonitoringExportQuery(code: String?, sort: String?, ascending: String?): PaymentMonitoringExportQuery {
    val query = code.orEmpty().trim()
    require(query.length <= 100) { "Subproject code search must not exceed 100 characters." }
    val column = if (sort == null) PaymentMonitoringExportSort.Code else
        PaymentMonitoringExportSort.entries.firstOrNull { it.name.equals(sort, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown payment monitoring sort column.")
    val direction = ascending?.toBooleanStrictOrNull() ?: if (ascending == null) true else
        throw IllegalArgumentException("Sort direction must be true or false.")
    return PaymentMonitoringExportQuery(query.lowercase(Locale.ROOT), column, direction)
}

/** Match the table's code search and nulls-last sorting, without pagination. */
internal fun selectPaymentMonitoringExportItems(
    items: List<PaymentMonitoringWorkbookSource.Item>,
    query: PaymentMonitoringExportQuery
): List<PaymentMonitoringWorkbookSource.Item> {
    fun compareOptional(left: Double?, right: Double?): Int = when {
        left == null && right == null -> 0
        left == null -> 1
        right == null -> -1
        else -> left.compareTo(right).let { if (query.ascending) it else -it }
    }
    fun compareText(left: String, right: String): Int =
        left.lowercase(Locale.ROOT).compareTo(right.lowercase(Locale.ROOT)).let { if (query.ascending) it else -it }
    fun compareCompliance(left: String?, right: String?): Int = when {
        left.isNullOrBlank() && right.isNullOrBlank() -> 0
        left.isNullOrBlank() -> 1
        right.isNullOrBlank() -> -1
        else -> compareText(left, right)
    }
    return items.filter { query.code.isBlank() || it.code.lowercase(Locale.ROOT).contains(query.code) }
        .sortedWith { left, right ->
            when (query.sort) {
                PaymentMonitoringExportSort.Code -> compareText(left.code, right.code)
                PaymentMonitoringExportSort.Total -> compareOptional(left.totalInvestmentUah, right.totalInvestmentUah)
                PaymentMonitoringExportSort.Eib -> compareOptional(left.eibInvestmentUah, right.eibInvestmentUah)
                PaymentMonitoringExportSort.Local -> compareOptional(left.localCoFinancingUah, right.localCoFinancingUah)
                PaymentMonitoringExportSort.PaidAndInProgress -> compareOptional(left.paidAndInProgressUah, right.paidAndInProgressUah)
                PaymentMonitoringExportSort.Paid -> compareOptional(left.paidUah, right.paidUah)
                PaymentMonitoringExportSort.Remaining -> compareOptional(left.remainingUah, right.remainingUah)
                PaymentMonitoringExportSort.PlannedShare -> compareOptional(left.plannedEibSharePct, right.plannedEibSharePct)
                PaymentMonitoringExportSort.FactualShare -> compareOptional(left.factualEibSharePct, right.factualEibSharePct)
                PaymentMonitoringExportSort.Compliance -> compareCompliance(left.eibShareCompliance, right.eibShareCompliance)
            }
        }
}
