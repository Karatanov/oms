package oms.umitaf.repository

import oms.umitaf.domain.InspectionReport

/**
 * Контракт доступу до звітів інспекцій.
 */
interface InspectionReportRepository {

    /** Returns every inspection report in one database query. */
    fun findAll(): List<InspectionReport>

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
        summary: String?,
        createdBy: Long,
        reportCode: String? = null,
        inspectionType: String = "planned",
        latitude: Double? = null,
        longitude: Double? = null
    ): InspectionReport

    fun update(
        uuid: String,
        inspectionDate: String,
        summary: String?,
        reportCode: String?,
        inspectionType: String,
        latitude: Double?,
        longitude: Double?
    ): InspectionReport?

    fun changeStatus(
        uuid: String,
        status: String,
        rejectionReason: String?
    ): InspectionReport?

    fun moveToProject(uuid: String, projectId: Long): InspectionReport?

    fun delete(uuid: String): Boolean
}
