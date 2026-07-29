package oms.ufsi.domain

import java.util.*

/**
 * Зауваження, виявлене
 * під час інспекції.
 */
data class InspectionFinding(

    /**
     * Внутрішній числовий ID.
     */
    val id: Long,

    /**
     * Публічний UUID.
     */
    val uuid: UUID,

    /**
     * Звіт інспекції.
     */
    val inspectionReportId: Long,

    /**
     * Категорія проблеми.
     */
    val category: String,

    /**
     * Критичність.
     */
    val severity: FindingSeverity,

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