package oms.umitaf.dto

import kotlinx.serialization.Serializable

/**
 * Метадані сторінки результатів.
 *
 * Використовуються для пагінації
 * у REST API.
 */
@Serializable
data class PageMetadata(

    /**
     * Поточна сторінка.
     */
    val page: Int,

    /**
     * Кількість записів на сторінці.
     */
    val perPage: Int,

    /**
     * Загальна кількість записів.
     */
    val total: Long,

    /**
     * Загальна кількість сторінок.
     */
    val totalPages: Int
)