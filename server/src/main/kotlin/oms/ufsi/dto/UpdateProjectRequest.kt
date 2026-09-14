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
    /** Customer-facing tranche: A = 1, B = 2. */
    val trancheNumber: Int? = null,
    val budgetPlanned: Long? = null,
    val engineerConsultantContractAmount: Long? = null,
    val technicalSupervisionAmount: Long? = null,
    val subprojectContractAmount: Long? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val contractSignedDate: String? = null,
    val plannedEndDate: String? = null,
    val designContractSigningDate: String? = null,
    val designStartDate: String? = null,
    val designPlannedEndDate: String? = null,
    val constructionContractSigningDate: String? = null,
    val constructionStartDate: String? = null,
    val projectedCompletionTime: String? = null,
    val currency: String? = null,
    val contractorName: String? = null,
    val designerName: String? = null,
    val designContractNumber: String? = null,
    val designContractTerm: String? = null,
    val constructionContractNumber: String? = null,
    val technicalSupervisionName: String? = null,
    val technicalSupervisionContractNumber: String? = null,
    val technicalSupervisionContractDate: String? = null,
    val technicalSupervisionStartDate: String? = null,
    val technicalSupervisionPlannedEndDate: String? = null,
    val engineerConsultantName: String? = null,
    val engineerConsultantContractNumber: String? = null,
    val engineerConsultantContractDate: String? = null,
    val engineerConsultantStartDate: String? = null,
    val engineerConsultantPlannedEndDate: String? = null,
    val amounts: Map<String, ProjectAmountDto>? = null
)
