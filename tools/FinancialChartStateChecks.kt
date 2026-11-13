package oms.urp3

/** Runs the same source-selection/cancellation checks on standalone JVM or JS. */
fun main() {
    FinancialProgressLoadTest().apply {
        availableEmptyProgressRemainsSuccessful()
        requestFailureIsAvailableForRetryPresentation()
        cancellationIsNotConvertedIntoLoadFailure()
    }
    FinancialChartSourceTest().apply {
        legacyLoadingRequiresBothAnUnavailableSourceAndProjectContext()
        emptyApprovedDataNeverResurrectsOldPayments()
        pendingSourceDoesNotFlashUnrelatedLegacyData()
        legacyFallbackRequiresAnUnavailableSourceAndACompletedAttempt()
    }
    println("PASS: 7 financial chart source/loading/cancellation checks")
}
