package oms.umitaf.dto

import kotlinx.serialization.Serializable

/**
 * Запит на створення нового зауваження.
 */
@Serializable
data class CreateInspectionFindingRequest(

    /**
     * Категорія зауваження.
     */
    val category: String,

    /**
     * Рівень критичності.
     *
     * low
     * medium
     * high
     * critical
     */
    val severity: String,

    /**
     * Опис проблеми.
     */
    val description: String,

    /**
     * Рекомендації.
     */
    val recommendation: String? = null
)
