package oms.umitaf.security

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(val userId: Long, val roleCode: String)
