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