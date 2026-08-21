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

    /** Stable human-readable code derived from the report UUID. */
    val inspectionCode: String,

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

/** A report together with the project it belongs to, for the reports list. */
@Serializable
data class InspectionReportListItemResponse(
    val projectUuid: String,
    val report: InspectionReportResponse
)
