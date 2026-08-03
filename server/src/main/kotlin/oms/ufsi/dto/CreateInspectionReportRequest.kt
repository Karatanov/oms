package oms.ufsi.dto

import kotlinx.serialization.Serializable

/**
 * Запит на створення звіту інспекції.
 */
@Serializable
data class CreateInspectionReportRequest(

    /**
     * Дата проведення інспекції.
     *
     * Формат: YYYY-MM-DD.
     */
    val inspectionDate: String,

    /**
     * Короткий підсумок інспекції.
     */
    val summary: String?
)
