package oms.ufsi.dto

import kotlinx.serialization.Serializable

/** A lightweight, browser-friendly representation of an uploaded SIR workbook. */
@Serializable
data class InspectionReportPreviewResponse(
    val fileName: String,
    val sheets: List<InspectionReportPreviewSheet>
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
