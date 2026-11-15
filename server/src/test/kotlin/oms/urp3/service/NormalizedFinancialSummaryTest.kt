package oms.urp3.service

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import oms.urp3.domain.FinancialRecord
import oms.urp3.domain.FinancialRecordType
import oms.urp3.dto.FinancialSummaryResponse
import oms.urp3.dto.toResponse
import kotlin.test.*

class NormalizedFinancialSummaryTest {
    private fun row(type: FinancialRecordType, amount: Double, currency: String = "UAH", rate: Double? = null, purpose: String = "works") =
        FinancialRecord(1, UUID.randomUUID(), 1, type, "test", amount, currency, LocalDate.of(2026, 11, 15),
            null, null, null, purpose, rate)
    private fun money(expected: String, actual: BigDecimal?) = assertEquals(0, actual?.compareTo(BigDecimal(expected)))

    @Test fun currencyNormalizationSeparatesPerformedWorksCashAndDocumentInventory() {
        val summary = normalizedFinancialSummary(10000, listOf(
            row(FinancialRecordType.ACT, 100.0, "EUR", 50.0),
            row(FinancialRecordType.PAYMENT, 1000.0), row(FinancialRecordType.ADVANCE, 200.0),
            row(FinancialRecordType.INVOICE, 1000.0), row(FinancialRecordType.ACT, 300.0, purpose = "technical_supervision")))
        money("5000", summary.amountSpent)
        money("1200", summary.amountPaid)
        money("7500", summary.financialDocumentsAmount)
        money("5000", summary.budgetRemaining)
        assertEquals(50.0, summary.completionPct)
        assertEquals(12.0, summary.financialCompletionPct)
    }
    @Test fun unknownActConversionDoesNotInventZeroOrHideKnownCash() {
        val summary = normalizedFinancialSummary(1000, listOf(row(FinancialRecordType.ACT, 100.0, "EUR", 1.0),
            row(FinancialRecordType.PAYMENT, 400.0)))
        assertNull(summary.amountSpent)
        assertNull(summary.budgetRemaining)
        assertNull(summary.completionPct)
        assertNull(summary.financialDocumentsAmount)
        money("400", summary.amountPaid)
        assertEquals(40.0, summary.financialCompletionPct)
        assertEquals(1, summary.unavailableActAmounts)
        assertEquals(0, summary.unavailablePaymentAmounts)
        assertEquals(1, summary.unavailableDocumentAmounts)
    }
    @Test fun unknownPaymentDoesNotBecomeAPartialFinancialExecutionPercentage() {
        val summary = normalizedFinancialSummary(1000, listOf(row(FinancialRecordType.ACT, 100.0),
            row(FinancialRecordType.PAYMENT, 20.0), row(FinancialRecordType.PAYMENT, 10.0, "EUR", 0.0)))
        money("100", summary.amountSpent)
        assertEquals(10.0, summary.completionPct)
        assertNull(summary.amountPaid)
        assertNull(summary.financialCompletionPct)
        assertEquals(1, summary.unavailablePaymentAmounts)
    }
    @Test fun unrelatedServiceDocumentsDoNotBlockWorksIndicators() {
        val summary = normalizedFinancialSummary(1000, listOf(row(FinancialRecordType.ACT, 100.0, "EUR", 1.0, "technical_supervision")))
        money("0", summary.amountSpent)
        money("0", summary.amountPaid)
        assertEquals(0.0, summary.completionPct)
        assertEquals(0.0, summary.financialCompletionPct)
        assertNull(summary.financialDocumentsAmount)
        assertEquals(0, summary.unavailableActAmounts)
        assertEquals(1, summary.unavailableDocumentAmounts)
    }
    @Test fun emptyLedgerHasRealZeroTotalsButNoPercentageForAZeroDenominator() {
        val summary = normalizedFinancialSummary(0, emptyList())
        money("0", summary.amountSpent)
        money("0", summary.amountPaid)
        money("0", summary.budgetRemaining)
        assertNull(summary.completionPct)
        assertNull(summary.financialCompletionPct)
    }
    @Test fun frozenDecimalRoundingIsUsedOnceWithoutChangingSourceRecords() {
        val records = listOf(row(FinancialRecordType.ACT, 0.84, "EUR", 48.125), row(FinancialRecordType.PAYMENT, 0.84, "EUR", 48.125))
        val original = records.toList()
        val summary = normalizedFinancialSummary(100, records)
        money("40.43", summary.amountSpent)
        money("40.43", summary.amountPaid)
        money("80.86", summary.financialDocumentsAmount)
        assertEquals(40.43, summary.financialCompletionPct)
        assertEquals(original, records)
    }
    @Test fun versionedResponseIdentifiesCurrencySourceAndIndependentUnavailableCategories() {
        val response = normalizedFinancialSummary(1000, listOf(row(FinancialRecordType.ACT, 100.0, "EUR", 1.0))).toResponse()
        assertEquals(2, response.summaryVersion)
        assertEquals("UAH", response.currency)
        assertEquals("legacy_financial_records", response.dataSource)
        assertNull(response.amountSpent)
        assertEquals(0.0, response.amountPaid)
        assertEquals(1, response.unavailableActAmounts)
        val legacy = FinancialSummaryResponse(1000.0, 1000.0, 100.0, 200.0, 900.0, 10.0, 20.0)
        assertEquals(1, legacy.summaryVersion)
        assertEquals(20.0, legacy.financialCompletionPct)
        assertNull(legacy.currency)
    }
}
