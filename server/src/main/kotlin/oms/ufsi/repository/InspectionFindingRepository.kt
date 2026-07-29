package oms.ufsi.repository

import oms.ufsi.domain.FindingSeverity
import oms.ufsi.domain.InspectionFinding

/**
 * Контракт доступу до зауважень інспекцій.
 */
interface InspectionFindingRepository {

    /**
     * Повертає всі зауваження,
     * що належать певній інспекції.
     */
    fun findByInspectionReportId(
        inspectionReportId: Long
    ): List<InspectionFinding>

    fun findByUuid(
        inspectionReportId: Long,
        uuid: String
    ): InspectionFinding?

    /**
     * Створює нове зауваження.
     */
    fun create(
        inspectionReportId: Long,
        category: String,
        severity: FindingSeverity,
        description: String,
        recommendation: String?
    ): InspectionFinding

    fun update(
        inspectionReportId: Long,
        uuid: String,
        category: String,
        severity: FindingSeverity,
        description: String,
        recommendation: String?,
        isResolved: Boolean
    ): InspectionFinding?

    fun delete(
        inspectionReportId: Long,
        uuid: String
    ): Boolean
}
