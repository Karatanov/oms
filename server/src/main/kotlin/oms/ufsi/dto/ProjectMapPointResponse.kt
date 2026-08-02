package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProjectMapPointResponse(
    val uuid: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val status: String
)
