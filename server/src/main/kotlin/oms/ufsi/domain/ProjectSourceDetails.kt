package oms.ufsi.domain

import java.math.BigDecimal
import java.time.LocalDate

data class ProgrammeDetails(
    val implementor: String,
    val financingInstitution: String,
    val financeContractNumber: String,
    val serapisNumber: String,
    val agreementDate: LocalDate,
    val loanAmount: BigDecimal,
    val loanCurrency: String,
    val sourceWorkbook: String,
    val sourceSnapshotDate: LocalDate?
)

data class ProjectMonitoringDetails(
    val sourceBatchId: Int?, val sourceSubprojectId: String, val sourceLotId: String,
    val nameEn: String?, val oblastCode: String?, val municipalityNameUk: String?,
    val municipalityNameEn: String?, val settlementNameEn: String?, val priorityAreaSource: String?,
    val projectManagerNameUk: String?, val projectManagerNameEn: String?, val projectManagerOrgId: String?,
    val beneficiaryNameUk: String?, val beneficiaryNameEn: String?, val beneficiaryOrgId: String?,
    val dreamProjectId: String?, val dreamProjectUrl: String?, val applicationId: String?,
    val dreamApplicationId: String?, val constructionProcurementStatus: String?,
    val constructionWorkStatus: String?, val geocodeAccuracy: String?, val geocodeQuery: String?,
    val geocodeDisplayName: String?, val sourceRows: String, val sourceWorkbook: String
)
