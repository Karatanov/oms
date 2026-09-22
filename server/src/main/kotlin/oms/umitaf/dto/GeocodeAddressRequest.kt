package oms.umitaf.dto

import kotlinx.serialization.Serializable

@Serializable
data class GeocodeAddressRequest(
    val address: String = "",
    val city: String = "",
    val region: String = ""
)

@Serializable
data class GeocodeAddressResponse(
    val latitude: Double,
    val longitude: Double
)
