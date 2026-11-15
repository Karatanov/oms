package oms.urp3.dto

import kotlinx.serialization.Serializable

/**
 * Зведена фінансова інформація
 * по проєкту.
 */
@Serializable
data class FinancialSummaryResponse(

    /**
     * Запланований бюджет.
     */
    val budgetPlanned: Double?,

    /** Construction-contract amount used as the completion baseline. */
    val constructionContractAmount: Double? = budgetPlanned,

    /**
     * Витрачена сума.
     */
    val amountSpent: Double?,

    /** Total amount across all financial documents. */
    val financialDocumentsAmount: Double? = amountSpent,

    /**
     * Залишок бюджету.
     */
    val budgetRemaining: Double?,

    /**
     * Відсоток виконання бюджету.
     */
    val completionPct: Double?,

    /** V1: document inventory ratio. V2: Works payments/advances divided by the contract. */
    val financialCompletionPct: Double? = completionPct,
    val summaryVersion: Int = 1,
    val currency: String? = null,
    val amountPaid: Double? = null,
    val unavailableActAmounts: Int = 0,
    val unavailablePaymentAmounts: Int = 0,
    val unavailableDocumentAmounts: Int = 0,
    val dataSource: String? = null
)

fun oms.urp3.service.NormalizedFinancialSummary.toResponse() = FinancialSummaryResponse(
    budgetPlanned = constructionContractAmount?.toDouble(),
    constructionContractAmount = constructionContractAmount?.toDouble(),
    amountSpent = amountSpent?.toDouble(),
    financialDocumentsAmount = financialDocumentsAmount?.toDouble(),
    budgetRemaining = budgetRemaining?.toDouble(),
    completionPct = completionPct,
    financialCompletionPct = financialCompletionPct,
    summaryVersion = 2, currency = "UAH", amountPaid = amountPaid?.toDouble(),
    unavailableActAmounts = unavailableActAmounts, unavailablePaymentAmounts = unavailablePaymentAmounts,
    unavailableDocumentAmounts = unavailableDocumentAmounts, dataSource = "legacy_financial_records"
)
