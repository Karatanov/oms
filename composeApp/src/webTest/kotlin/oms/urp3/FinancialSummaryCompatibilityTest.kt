package oms.urp3

import kotlinx.serialization.json.Json
import oms.data.ApiFinancialSummary
import kotlin.test.*

class FinancialSummaryCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test fun legacySummaryStillDecodesWithItsOriginalNumbers() {
        val result = json.decodeFromString<ApiFinancialSummary>("""{"budgetPlanned":1000,"amountSpent":100,"financialDocumentsAmount":200,"budgetRemaining":900,"completionPct":10,"financialCompletionPct":20}""")
        assertEquals(1, result.summaryVersion)
        assertEquals(100.0, result.amountSpent)
        assertEquals(20.0, result.financialCompletionPct)
        assertNull(result.amountPaid)
    }
    @Test fun unavailablePerformedWorkDoesNotBreakKnownCashOrDeserialization() {
        val result = json.decodeFromString<ApiFinancialSummary>("""{"budgetPlanned":1000,"constructionContractAmount":1000,"amountSpent":null,"financialDocumentsAmount":null,"budgetRemaining":null,"completionPct":null,"financialCompletionPct":40,"summaryVersion":2,"currency":"UAH","amountPaid":400,"unavailableActAmounts":1,"unavailableDocumentAmounts":1,"dataSource":"legacy_financial_records"}""")
        assertEquals(2, result.summaryVersion)
        assertEquals("UAH", result.currency)
        assertNull(result.amountSpent)
        assertNull(result.budgetRemaining)
        assertNull(result.completionPct)
        assertEquals(400.0, result.amountPaid)
        assertEquals(40.0, result.financialCompletionPct)
        assertEquals(1, result.unavailableActAmounts)
    }
    @Test fun absentOptionalNullFieldsAlsoRemainUnavailable() {
        val result = json.decodeFromString<ApiFinancialSummary>("""{"summaryVersion":2,"currency":"UAH","amountPaid":0}""")
        assertNull(result.completionPct)
        assertNull(result.amountSpent)
        assertEquals(0.0, result.amountPaid)
    }
    @Test fun omittedUnavailableCashNeverFallsBackToTheActPercentage() {
        val result = json.decodeFromString<ApiFinancialSummary>("""{"summaryVersion":2,"completionPct":10,"amountSpent":100}""")
        assertEquals(10.0, result.completionPct)
        assertNull(result.financialCompletionPct)
    }
}
