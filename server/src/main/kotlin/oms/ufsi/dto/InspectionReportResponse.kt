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
     * Короткий підсумок.
     */
    val summary: String?,

    val status: String,

    val rejectionReason: String?
)
