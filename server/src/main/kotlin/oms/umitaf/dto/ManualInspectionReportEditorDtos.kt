package oms.umitaf.dto

import kotlinx.serialization.Serializable

/** Full manual SIR form together with its current project placement. */
@Serializable
data class ManualInspectionReportEditorResponse(
    val projectUuid: String,
    val status: String,
    val manual: CreateManualInspectionReportRequest
)

/** Rewrites a manual SIR workbook and, when needed, changes its placement. */
@Serializable
data class UpdateManualInspectionReportRequest(
    val projectUuid: String,
    val status: String? = null,
    val manual: CreateManualInspectionReportRequest
)
