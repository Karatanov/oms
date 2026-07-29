package oms.ufsi.dto

import kotlinx.serialization.Serializable

/**
 * DTO звіту інспекції.
 */
@Serializable
data class InspectionReportResponse(

    /**
     * Публічний UUID звіту.
     */
    val uuid: String,

    /**
     * Дата проведення інспекції.
     */
    val inspectionDate: String,

    /**
     * Відсоток готовності.
     */
    val completionPct: Double,

    /**
     * Короткий підсумок.
     */
    val summary: String?,

    val status: String,

    val rejectionReason: String?
)
