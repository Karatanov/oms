package oms.screens

import oms.data.ApiFinancialRecord
import oms.data.ApiPaymentMonitoringTransaction
import oms.urp3.decimalMoneyCents

/** Both amounts are already authoritative source values; never round-trip UAH through EUR. */
internal fun approvedPaymentChartRecord(transaction: ApiPaymentMonitoringTransaction) = ApiFinancialRecord(
    uuid = transaction.sourceReference, recordType = "payment", referenceNumber = transaction.sourceReference,
    amount = transaction.amountUah, currency = "UAH", recordDate = transaction.paymentDate, paymentDate = transaction.paymentDate,
    paymentPurpose = sourcePaymentPurpose(transaction.code) ?: "unknown",
    amountEurCents = transaction.amountEur?.let { decimalMoneyCents(it) }
)

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
