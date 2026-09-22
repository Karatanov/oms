package oms.umitaf.dto

import kotlinx.serialization.Serializable

/** A single row from the `OBSERVANCES ON HEALTH & SAFETY` section of an uploaded SIR. */
@Serializable
data class HealthSafetyObservationResponse(
    val reportUuid: String,
    val inspectionDate: String,
    val observation: String,
    val answer: String? = null,
    val comment: String? = null
)

@Serializable
data class HealthSafetyObservationsResponse(
    val uploadedReportsCount: Int,
    val observations: List<HealthSafetyObservationResponse>
)
