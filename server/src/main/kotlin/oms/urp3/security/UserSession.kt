package oms.urp3.security

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(val userId: Long, val roleCode: String)
