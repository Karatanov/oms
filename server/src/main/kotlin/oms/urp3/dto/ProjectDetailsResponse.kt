package oms.urp3.dto

import kotlinx.serialization.Serializable

/** Project detail payload consumed by the project card. */
@Serializable
data class ProjectDetailsResponse(

    val data: ProjectResponse,
    val financialSummary: FinancialSummaryResponse,
    val monitoringDetails: ProjectMonitoringDetailsResponse? = null,
    val contractorNameEn: String? = null
)

/** Only fields rendered by the project detail screen are exposed to the browser. */
@Serializable
data class ProjectMonitoringDetailsResponse(
    val nameEn: String? = null,
    val municipalityNameUk: String? = null,
    val municipalityNameEn: String? = null,
    val settlementNameEn: String? = null,
    val beneficiaryNameUk: String? = null,
    val beneficiaryNameEn: String? = null,
    val constructionProcurementStatus: String? = null,
    val constructionWorkStatus: String? = null
)
