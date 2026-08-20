package oms.ufsi.dto

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
    val budgetPlanned: Long,

    /** Construction-contract amount used as the completion baseline. */
    val constructionContractAmount: Long = budgetPlanned,

    /**
     * Витрачена сума.
     */
    val amountSpent: Long,

    /**
     * Залишок бюджету.
     */
    val budgetRemaining: Long,

    /**
     * Відсоток виконання бюджету.
     */
    val completionPct: Double
)
