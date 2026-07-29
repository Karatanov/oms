package oms.ufsi.dto

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
     * Географічна широта.
     */
    val latitude: Double,

    /**
     * Географічна довгота.
     */
    val longitude: Double,

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

    /**
     * Ідентифікатор керівника проєкту.
     */
    val managerId: Long
)