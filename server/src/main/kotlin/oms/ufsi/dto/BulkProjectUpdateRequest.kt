package oms.ufsi.dto

import kotlinx.serialization.Serializable

/** Applies one lifecycle status to several selected projects. */
@Serializable
data class BulkProjectUpdateRequest(
    val projectUuids: List<String>,
    val status: String
)

@Serializable
data class BulkProjectUpdateResponse(val updated: Int)
