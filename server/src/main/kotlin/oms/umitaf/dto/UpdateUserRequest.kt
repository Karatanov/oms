package oms.umitaf.dto

import kotlinx.serialization.Serializable

/** Partial administrative user update. Password is optional and write-only. */
@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val roleCode: String? = null,
    val password: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val status: String? = null,
    val region: String? = null,
    val department: String? = null,
    val preferredLang: String? = null
)
