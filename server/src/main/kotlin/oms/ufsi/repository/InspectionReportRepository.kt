package oms.ufsi.repository

import oms.ufsi.domain.InspectionReport

/**
 * Контракт доступу до звітів інспекцій.
 */
interface InspectionReportRepository {

    /**
     * Повертає всі інспекції проєкту.
     */
    fun findByProjectId(
        projectId: Long
    ): List<InspectionReport>

    /**
     * Повертає інспекцію
     * за її публічним UUID.
     */
    fun findByUuid(
        uuid: String
    ): InspectionReport?
    
    /**
     * Створює новий звіт інспекції.
     */
    fun create(
        projectId: Long,
        inspectionDate: String,
        completionPct: Double,
        summary: String?,
        createdBy: Long
    ): InspectionReport

    fun update(
        uuid: String,
        inspectionDate: String,
        completionPct: Double,
        summary: String?
    ): InspectionReport?

    fun changeStatus(
        uuid: String,
        status: String,
        rejectionReason: String?
    ): InspectionReport?
}
