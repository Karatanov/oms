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
     * Загальний відсоток готовності.
     */
    val completionPct: Double,

    /**
     * Короткий підсумок інспекції.
     */
    val summary: String?
)