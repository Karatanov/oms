package oms.screens

/** A loaded empty source is authoritative; pending loads must not flash legacy totals. */
internal fun <T> financialChartSourceRows(approvedRows: List<T>?, loadingApproved: Boolean, legacyRows: List<T>): List<T> =
    when {
        approvedRows != null -> approvedRows
        loadingApproved -> emptyList()
        else -> legacyRows
    }

/** The retired ledger is a fallback, never a prerequisite for approved-source charts. */
internal fun shouldLoadLegacyFinancialRecords(approvedAvailable: Boolean, loadingApproved: Boolean, projectContextReady: Boolean): Boolean =
    !approvedAvailable && !loadingApproved && projectContextReady
