package oms.umitaf.dto

import kotlinx.serialization.Serializable

/**
 * Запит на створення нового проєкту.
 *
 * Поля відповідають технічній
 * специфікації OMS.
 */
@Serializable
data class CreateProjectRequest(

    /**
     * Повна назва проєкту.
     */
    val name: String,
    val status: String? = null,

    /**
     * Короткий код майданчика.
     *
     * Наприклад: KYIV.
     */
    val siteName: String,

    /**
     * Номер майданчика.
     *
     * Наприклад: 042.
     */
    val siteNumber: String,

    val description: String? = null,

    /**
     * Повна адреса.
     */
    val address: String = "",

    /**
     * Область.
     */
    val region: String = "",

    /**
     * Населений пункт.
     */
    val city: String = "",

    /**
     * Географічна широта.
     */
    val latitude: Double = 0.0,

    /**
     * Географічна довгота.
     */
    val longitude: Double = 0.0,

    /**
     * Галузь.
     */
    val sector: String = "",

    /**
     * Тип будівництва.
     */
    val constructionType: String = "reconstruction",

    /**
     * Плановий бюджет у гривнях.
     */
    val budgetPlanned: Long,

    /** Optional contract amount for the engineer-consultant, in UAH. */
    val engineerConsultantContractAmount: Long? = null,

    /** Optional technical-supervision amount, in UAH. */
    val technicalSupervisionAmount: Long? = null,

    /**
     * Ідентифікатор керівника проєкту.
     */
    val managerId: Long? = null,

    /** Project hierarchy. A subproject must reference a parent project UUID. */
    val projectType: String = "project",
    val parentProjectUuid: String? = null,
    /** Customer-facing tranche: A = 1, B = 2. */
    val trancheNumber: Int = 1,

    /** Required contract and schedule data for subprojects. */
    val subprojectContractAmount: Long? = null,
    val startDate: String? = null,
    val contractSignedDate: String? = null,
    val plannedEndDate: String? = null,
    val endDate: String? = null,
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
