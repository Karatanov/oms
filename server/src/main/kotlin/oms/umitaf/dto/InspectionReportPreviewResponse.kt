package oms.umitaf.dto

import kotlinx.serialization.Serializable

/** A lightweight, browser-friendly representation of an uploaded SIR workbook. */
@Serializable
data class InspectionReportPreviewResponse(
    val fileName: String,
    val sheets: List<InspectionReportPreviewSheet>,
    /** Parsed standard SIR values when the source workbook follows the SIR template. */
    val manual: CreateManualInspectionReportRequest? = null
)

@Serializable
data class InspectionReportPreviewSheet(
    val name: String,
    val rows: List<InspectionReportPreviewRow>
)

@Serializable
data class InspectionReportPreviewRow(
    val rowNumber: Int,
    val cells: List<String>
)
