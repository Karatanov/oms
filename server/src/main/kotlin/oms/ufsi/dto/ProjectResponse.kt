package oms.ufsi.dto

import kotlinx.serialization.Serializable

/**
 * DTO-відповідь для проєкту.
 *
 * Містить дані, які можуть
 * бути безпечно повернуті клієнту.
 */
@Serializable
data class ProjectResponse(

    /**
     * Публічний ідентифікатор проєкту.
     */
    val uuid: String,

    /**
     * Тип запису.
     */
    val projectType: String,

    /** Tranche number; current records default to 1. */
    val trancheNumber: Int,

    /**
     * UUID батьківського проєкту.
     */
    val parentProjectId: Long?,

    /** Public UUID of the parent project, used to render the project tree. */
    val parentProjectUuid: String? = null,

    /**
     * Назва проєкту.
     */
    val name: String,

    /**
     * Код майданчика.
     */
    val siteName: String,

    /**
     * Номер майданчика.
     */
    val siteNumber: String,

    val description: String?,

    /**
     * Повна адреса.
     */
    val address: String,

    /**
     * Область.
     */
    val region: String,

    /**
     * Населений пункт.
     */
    val city: String,

    /**
     * Географічні координати.
     */
    val latitude: Double,

    val longitude: Double,

    /**
     * Поточний статус.
     */
    val status: String,

    /**
     * Галузь.
     */
    val sector: String,

    /**
     * Тип будівництва.
     */
    val constructionType: String,

    /**
     * Плановий бюджет у гривнях.
     */
    val budgetPlanned: Long,

    /** Optional contract amount for the engineer-consultant, in UAH. */
    val engineerConsultantContractAmount: Long?,

    /** Optional technical-supervision amount, in UAH. */
    val technicalSupervisionAmount: Long?,

    val subprojectContractAmount: Long?,
    val startDate: String?,
    val endDate: String?,
    val contractSignedDate: String?,
    val plannedEndDate: String?,
    val designContractSigningDate: String?,
    val designStartDate: String? = null,
    val designPlannedEndDate: String? = null,
    val constructionContractSigningDate: String?,
    val constructionStartDate: String?,
    val projectedCompletionTime: String?,
    val contractDurationDays: Long?,
    val designDurationDays: Long? = null,

    /**
     * Валюта відображення.
     */
    val currency: String,

    /**
     * Назва підрядника.
     */
    val contractorName: String?,
    val designerName: String? = null,
    val designContractNumber: String? = null,
    val designContractTerm: String? = null,
    val constructionContractNumber: String? = null,
    val technicalSupervisionName: String? = null,
    val technicalSupervisionContractNumber: String? = null,
    val technicalSupervisionContractDate: String? = null,
    val technicalSupervisionStartDate: String? = null,
    val technicalSupervisionPlannedEndDate: String? = null,
    val technicalSupervisionDurationDays: Long? = null,
    val engineerConsultantName: String? = null,
    val engineerConsultantContractNumber: String? = null,
    val engineerConsultantContractDate: String? = null,
    val engineerConsultantStartDate: String? = null,
    val engineerConsultantPlannedEndDate: String? = null,
    val engineerConsultantDurationDays: Long? = null,
    val amounts: Map<String, ProjectAmountDto> = emptyMap()
)
