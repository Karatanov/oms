package oms.ufsi.dto

import kotlinx.serialization.Serializable

/**
 * Детальна інформація про проєкт.
 */
@Serializable
data class ProjectDetailsResponse(

    /**
     * Дані проєкту.
     */
    val data: ProjectResponse,

    /**
     * Фінансова зведена інформація.
     */
    val financialSummary:
    FinancialSummaryResponse
)