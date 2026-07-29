package oms.ufsi.dto

import kotlinx.serialization.Serializable

/**
 * DTO-відповідь для зауваження інспекції.
 */
@Serializable
data class InspectionFindingResponse(

    /**
     * Публічний UUID.
     */
    val uuid: String,

    /**
     * Категорія проблеми.
     */
    val category: String,

    /**
     * Рівень критичності.
     */
    val severity: String,

    /**
     * Опис проблеми.
     */
    val description: String,

    /**
     * Рекомендації щодо усунення.
     */
    val recommendation: String?,

    /**
     * Ознака виправлення.
     */
    val isResolved: Boolean
)