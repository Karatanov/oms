package oms.ufsi.domain

import java.time.LocalDate

/** Fields of a project that may be changed without changing its lifecycle status. */
data class ProjectPatch(
    val name: String,
    val siteName: String,
    val siteNumber: String,
    val description: String?,
    val address: String,
    val region: String,
    val city: String,
    val status: ProjectStatus,
    val latitude: Double,
    val longitude: Double,
    val sector: String,
    val constructionType: String,
    val budgetPlanned: Long,
    val engineerConsultantContractAmount: Long?,
    val technicalSupervisionAmount: Long?,
    val subprojectContractAmount: Long?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val contractSignedDate: LocalDate?,
    val plannedEndDate: LocalDate?,
    val designContractSigningDate: LocalDate?,
    val constructionContractSigningDate: LocalDate?,
    val constructionStartDate: LocalDate?,
    val projectedCompletionTime: LocalDate?,
    val currency: String,
    val contractorName: String?
)
