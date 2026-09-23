package oms.umitaf.repository

import oms.umitaf.domain.FindingSeverity
import oms.umitaf.domain.InspectionFinding

/**
 * Контракт доступу до зауважень інспекцій.
 */
interface InspectionFindingRepository {

    /** Replace only the selected category, atomically and serialized per report. */
    fun replaceCategory(inspectionReportId: Long, category: String, severity: FindingSeverity,
                        entries: List<Pair<String, String?>>)

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
