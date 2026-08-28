package oms.ufsi.domain

import java.time.LocalDate
import java.util.*

/**
 * Доменна модель звіту інспекції.
 *
 * Інспекція є однією з ключових
 * сутностей OMS.
 */
data class InspectionReport(

    /**
     * Внутрішній числовий ID.
     */
    val id: Long,

    /**
     * Публічний UUID.
     */
    val uuid: UUID,

    /**
     * Проєкт, до якого належить звіт.
     */
    val projectId: Long,

    /** Human-readable SIR code. Legacy reports use a UUID-derived fallback. */
    val reportCode: String?,

    /** Planned, unplanned or final inspection. */
    val inspectionType: String,

    /**
     * Дата проведення інспекції.
     */
    val inspectionDate: LocalDate,

    /**
     * Короткий опис результатів.
     */
    val summary: String?,

    val status: InspectionReportStatus,

    val rejectionReason: String?,

    /** Optional coordinates captured during the inspection. */
    val latitude: Double?,

    val longitude: Double?,

    /**
     * Автор звіту.
     */
    val createdBy: Long,
    val authorUsername: String? = null
)
