package oms.umitaf.dto

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
    val budgetPlanned: Double,

    /** Construction-contract amount used as the completion baseline. */
    val constructionContractAmount: Double = budgetPlanned,

    /**
     * Витрачена сума.
     */
    val amountSpent: Double,

    /** Total amount across all financial documents. */
    val financialDocumentsAmount: Double = amountSpent,

    /**
     * Залишок бюджету.
     */
    val budgetRemaining: Double,

    /**
     * Відсоток виконання бюджету.
     */
    val completionPct: Double,

    /** All financial documents as a percentage of the construction contract. */
    val financialCompletionPct: Double = completionPct
)
