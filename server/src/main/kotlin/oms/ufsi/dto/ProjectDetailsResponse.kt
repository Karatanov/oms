package oms.ufsi.dto

import kotlinx.serialization.Serializable

/**
 * Детальна інформація про проєкт.
 */
@Serializable
data class ProjectDetailsResponse(

    /**
     * Дані проєкту.
     */
    val data: ProjectResponse,

    /**
     * Фінансова зведена інформація.
     */
    val financialSummary: FinancialSummaryResponse,
    val programmeDetails: ProgrammeDetailsResponse? = null,
    val monitoringDetails: ProjectMonitoringDetailsResponse? = null
)

@Serializable
data class ProgrammeDetailsResponse(
    val implementor: String,
    val financingInstitution: String,
    val financeContractNumber: String,
    val serapisNumber: String,
    val agreementDate: String,
    val loanAmount: String,
    val loanCurrency: String,
    val sourceWorkbook: String,
    val sourceSnapshotDate: String? = null
)

@Serializable
data class ProjectMonitoringDetailsResponse(
    val sourceBatchId: Int? = null,
    val sourceSubprojectId: String,
    val sourceLotId: String,
    val nameEn: String? = null,
    val oblastCode: String? = null,
    val municipalityNameUk: String? = null,
    val municipalityNameEn: String? = null,
    val settlementNameEn: String? = null,
    val priorityAreaSource: String? = null,
    val projectManagerNameUk: String? = null,
    val projectManagerNameEn: String? = null,
    val projectManagerOrgId: String? = null,
    val beneficiaryNameUk: String? = null,
    val beneficiaryNameEn: String? = null,
    val beneficiaryOrgId: String? = null,
    val dreamProjectId: String? = null,
    val dreamProjectUrl: String? = null,
    val applicationId: String? = null,
    val dreamApplicationId: String? = null,
    val constructionProcurementStatus: String? = null,
    val constructionWorkStatus: String? = null,
    val geocodeAccuracy: String? = null,
    val geocodeQuery: String? = null,
    val geocodeDisplayName: String? = null,
    val sourceRows: String,
    val sourceWorkbook: String
)
