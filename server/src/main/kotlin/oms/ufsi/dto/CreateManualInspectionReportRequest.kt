package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable data class ManualActivity(val location: String = "", val description: String = "", val onSchedule: String = "no", val remarks: String? = null)
@Serializable data class ManualHseObservation(val observation: String, val answer: String? = null, val comment: String? = null)
@Serializable data class ManualRemark(val comment: String, val rectification: String? = null)

/** Complete information entered in the browser to create a standard SIR workbook. */
@Serializable data class CreateManualInspectionReportRequest(
    val inspectionDate: String = "", val contractor: String = "", val contractorRepresentative: String? = null,
    val qaStaff: String? = null, val usifRepresentative: String? = null,
    val skilledLabor: String? = null, val unskilledLabor: String? = null, val siteManagement: String? = null,
    val weather: String? = null, val activities: List<ManualActivity> = emptyList(),
    val ongoingObservations: List<String> = emptyList(), val hseObservations: List<ManualHseObservation> = emptyList(),
    val qualityRemarks: List<ManualRemark> = emptyList(), val progressComment: String? = null,
    val scheduleRemark: String? = null, val inspectorName: String = "", val inspectorTitle: String? = null
)
