package oms.umitaf.dto

import kotlinx.serialization.Serializable

/** Moves an existing inspection report to another project. */
@Serializable
data class MoveInspectionReportRequest(val projectUuid: String)
