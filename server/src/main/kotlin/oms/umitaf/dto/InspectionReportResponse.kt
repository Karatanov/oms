package oms.umitaf.dto

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

    val reportCode: String? = null,

    val inspectionType: String = "planned",

    /**
     * Дата проведення інспекції.
     */
    val inspectionDate: String,

    /**
     * Короткий підсумок.
     */
    val summary: String?,

    val status: String,

    val rejectionReason: String?,

    val latitude: Double? = null,

    val longitude: Double? = null,
    val authorUsername: String? = null,

    /** Code of the owning subproject, resolved for dashboard presentation. */
    val subprojectCode: String? = null
)

/** A report together with the project it belongs to, for the reports list. */
@Serializable
data class InspectionReportListItemResponse(
    val projectUuid: String,
    val report: InspectionReportResponse
)
