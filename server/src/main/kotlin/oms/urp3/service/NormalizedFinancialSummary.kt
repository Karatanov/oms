package oms.urp3.service

import java.math.BigDecimal
import java.math.RoundingMode
import oms.urp3.domain.FinancialRecord
import oms.urp3.domain.FinancialRecordType

/** Version 2 covers the legacy financial-record ledger, not the imported UR III register. */
data class NormalizedFinancialSummary(
    val constructionContractAmount: BigDecimal?,
    val amountSpent: BigDecimal?,
    val amountPaid: BigDecimal?,
    val financialDocumentsAmount: BigDecimal?,
    val unavailableActAmounts: Int,
    val unavailablePaymentAmounts: Int,
    val unavailableDocumentAmounts: Int
) {
    val budgetRemaining: BigDecimal? get() = constructionContractAmount?.let { base -> amountSpent?.let(base::subtract) }
    val completionPct: Double? get() = percentage(amountSpent)
    val financialCompletionPct: Double? get() = percentage(amountPaid)
    private fun percentage(amount: BigDecimal?): Double? = constructionContractAmount?.takeIf { it.signum() > 0 }
        ?.let { base -> amount?.multiply(BigDecimal(100))?.divide(base, 12, RoundingMode.HALF_UP)?.toDouble() }
}

/** Convert once using frozen values only. Incomplete categories are null, not partial totals. */
internal fun normalizedFinancialSummary(baseUah: Long, records: List<FinancialRecord>): NormalizedFinancialSummary {
    data class Total(val value: BigDecimal?, val unavailable: Int)
    val converted = records.map { it to financialDisplayCents(it, "UAH") }
    fun total(predicate: (FinancialRecord) -> Boolean): Total {
        var cents = BigDecimal.ZERO
        var unavailable = 0
        for ((record, value) in converted) if (predicate(record)) {
            if (value == null) unavailable++ else cents = cents.add(BigDecimal.valueOf(value))
        }
        return Total(if (unavailable == 0) cents.movePointLeft(2) else null, unavailable)
    }
    val acts = total { it.recordType == FinancialRecordType.ACT && it.paymentPurpose.equals("works", true) }
    val paid = total { it.recordType in setOf(FinancialRecordType.PAYMENT, FinancialRecordType.ADVANCE) && it.paymentPurpose.equals("works", true) }
    // Inventory statistic only: invoices/acts/payments describe overlapping stages.
    // It must never be the numerator of financial execution.
    val documents = total { true }
    return NormalizedFinancialSummary(BigDecimal.valueOf(baseUah).takeIf { it.signum() >= 0 },
        acts.value, paid.value, documents.value, acts.unavailable, paid.unavailable, documents.unavailable)
}
