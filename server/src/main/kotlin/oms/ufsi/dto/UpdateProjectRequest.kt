package oms.ufsi.dto

import kotlinx.serialization.Serializable

/** Partial project update. Omitted fields keep their existing values. */
@Serializable
data class UpdateProjectRequest(
    val name: String? = null,
    val siteName: String? = null,
    val siteNumber: String? = null,
    val description: String? = null,
    val address: String? = null,
    val region: String? = null,
    val city: String? = null,
    val status: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sector: String? = null,
    val constructionType: String? = null,
    val budgetPlanned: Long? = null,
    val engineerConsultantContractAmount: Long? = null,
    val technicalSupervisionAmount: Long? = null,
    val subprojectContractAmount: Long? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val contractSignedDate: String? = null,
    val plannedEndDate: String? = null,
    val designContractSigningDate: String? = null,
    val constructionContractSigningDate: String? = null,
    val constructionStartDate: String? = null,
    val projectedCompletionTime: String? = null,
    val currency: String? = null,
    val contractorName: String? = null
)
