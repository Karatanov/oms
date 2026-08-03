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

    /**
     * UUID батьківського проєкту.
     */
    val parentProjectId: Long?,

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

    /**
     * Валюта відображення.
     */
    val currency: String,

    /**
     * Назва підрядника.
     */
    val contractorName: String?
)
