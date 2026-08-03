package oms.ufsi.dto

import kotlinx.serialization.Serializable

/** Partial administrative user update. Password is optional and write-only. */
@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val roleCode: String? = null,
    val password: String? = null
)
