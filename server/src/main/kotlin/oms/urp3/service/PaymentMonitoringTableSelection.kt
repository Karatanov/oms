package oms.urp3.service

import java.util.Locale

internal enum class PaymentMonitoringExportSort {
    Code, Total, Eib, Local, PaidAndInProgress, Paid, Remaining, PlannedShare, FactualShare, Compliance
}

internal data class PaymentMonitoringExportQuery(
    val code: String,
    val sort: PaymentMonitoringExportSort,
    val ascending: Boolean,
    val selectedCodes: Set<String>? = null,
    val month: String? = null,
    val year: Int? = null,
    val purpose: String? = null
)

internal fun paymentMonitoringExportQuery(code: String?, sort: String?, ascending: String?, codes: String? = null,
    month: String? = null, year: String? = null, purpose: String? = null): PaymentMonitoringExportQuery {
    require(codes == null || codes.length <= 8000) { "Export selection is too large." }
    // An explicit empty selection must never expand to the entire register.
    val selectedCodes = codes?.split(',')?.filter { it.isNotBlank() }?.map { it.trim().lowercase(Locale.ROOT) }?.toSet()
    val query = code.orEmpty().trim()
    require(query.length <= 100) { "Subproject code search must not exceed 100 characters." }
    val column = if (sort == null) PaymentMonitoringExportSort.Code else
        PaymentMonitoringExportSort.entries.firstOrNull { it.name.equals(sort, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown payment monitoring sort column.")
    val direction = ascending?.toBooleanStrictOrNull() ?: if (ascending == null) true else
        throw IllegalArgumentException("Sort direction must be true or false.")
    require(month == null || runCatching { java.time.YearMonth.parse(month) }.isSuccess) { "Invalid payment month." }
    val selectedYear = year?.toIntOrNull()
    require(year == null || selectedYear != null && selectedYear in 1..9999) { "Invalid payment year." }
    require(purpose == null || purpose in setOf("works", "technical_supervision", "engineer_consultant", "equipment")) { "Invalid payment purpose." }
    return PaymentMonitoringExportQuery(query.lowercase(Locale.ROOT), column, direction, selectedCodes, month, selectedYear, purpose)
}

/** Export dated source rows once; cumulative parent/lot indicators are never payments. */
internal fun selectPaymentMonitoringExportTransactions(
    transactions: List<PaymentMonitoringWorkbookSource.Transaction>, query: PaymentMonitoringExportQuery
): List<PaymentMonitoringWorkbookSource.Transaction> {
    val projects = query.selectedCodes?.map { oms.urp3.paymentSubprojectKey(it).lowercase(Locale.ROOT) }?.toSet()
    return transactions.filter { row ->
        val purpose = when (row.code.substringBefore('$').substringAfterLast('_').uppercase(Locale.ROOT)) {
            "W" -> "works"
            "TS" -> "technical_supervision"
            "CSC" -> "engineer_consultant"
            "E", "EQUIPMENT" -> "equipment"
            else -> null
        }
        (projects == null || oms.urp3.paymentSubprojectKey(row.code).lowercase(Locale.ROOT) in projects) &&
            (query.code.isBlank() || row.code.lowercase(Locale.ROOT).contains(query.code)) &&
            (query.month == null || row.paymentDate.toString().startsWith(query.month)) &&
            (query.year == null || row.paymentDate.year == query.year) &&
            (query.purpose == null || purpose == query.purpose)
    }.sortedWith(compareBy({ it.paymentDate }, { it.code.lowercase(Locale.ROOT) }, { it.sourceReference }))
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
    return items.filter {
        (query.selectedCodes == null || it.code.lowercase(Locale.ROOT) in query.selectedCodes) &&
            (query.code.isBlank() || it.code.lowercase(Locale.ROOT).contains(query.code))
    }
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
