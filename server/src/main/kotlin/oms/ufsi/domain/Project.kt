package oms.ufsi.domain

import java.util.*
import java.time.LocalDate

/**
 * Доменна модель проєкту.
 *
 * Це одна з центральних сутностей OMS.
 */
data class Project(

    /**
     * Внутрішній числовий ідентифікатор.
     */
    val id: Long,

    /**
     * Публічний ідентифікатор,
     * який використовується в API.
     */
    val uuid: UUID,

    /**
     * Тип проєкту.
     */
    val projectType: ProjectType,

    /**
     * Батьківський проєкт.
     *
     * Null означає, що це кореневий проєкт.
     */
    val parentProjectId: Long?,

    /**
     * Повна назва.
     */
    val name: String,

    /**
     * Короткий код майданчика.
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
    val status: ProjectStatus,

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

    /** Contract amount required for a subproject, in UAH. */
    val subprojectContractAmount: Long?,

    /** Schedule fields required for a subproject. */
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val contractSignedDate: LocalDate?,
    val plannedEndDate: LocalDate?,
    val designContractSigningDate: LocalDate?,
    val constructionContractSigningDate: LocalDate?,
    val constructionStartDate: LocalDate?,
    val projectedCompletionTime: LocalDate?,

    /**
     * Код валюти.
     */
    val currency: String,

    /**
     * Назва основного підрядника.
     */
    val contractorName: String?
) {
    /** Duration from contract signing to the planned end date, in days. */
    val contractDurationDays: Long?
        get() = if (contractSignedDate != null && plannedEndDate != null)
            java.time.temporal.ChronoUnit.DAYS.between(contractSignedDate, plannedEndDate)
        else null
}
