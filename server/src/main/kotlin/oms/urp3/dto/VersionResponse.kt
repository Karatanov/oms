package oms.urp3.dto

import kotlinx.serialization.Serializable

/** Immutable revision of the server image currently serving requests. */
@Serializable
data class VersionResponse(
    val revision: String
)
