package oms.umitaf.service

import oms.umitaf.domain.InspectionReport
import oms.umitaf.domain.InspectionReportStatus
import oms.umitaf.repository.InspectionReportRepository

/**
 * Бізнес-логіка роботи
 * зі звітами інспекцій.
 */
class InspectionReportService(
    private val repository:
    InspectionReportRepository
) {

    /**
     * The registry and dashboard analytics commonly read the same report
     * metadata within one screen load. Cache only that lightweight metadata
     * briefly and invalidate it on every workflow or placement mutation.
     */
    private val reportCacheLock = Any()
    @Volatile private var cachedReports: List<InspectionReport>? = null
    @Volatile private var reportCacheExpiresAt = 0L

    private fun invalidateReportCache() = synchronized(reportCacheLock) {
        cachedReports = null
        reportCacheExpiresAt = 0L
    }

    private fun currentReportSnapshot(): List<InspectionReport>? =
        cachedReports?.takeIf { System.currentTimeMillis() < reportCacheExpiresAt }

    fun getAllReports(): List<InspectionReport> {
        currentReportSnapshot()?.let { return it }
        return synchronized(reportCacheLock) {
            val current = System.currentTimeMillis()
            cachedReports?.takeIf { current < reportCacheExpiresAt } ?: repository.findAll().also { reports ->
                cachedReports = reports
                reportCacheExpiresAt = current + 15_000L
            }
        }
    }

    /**
     * Повертає всі інспекції проєкту.
     */
    fun getProjectReports(
        projectId: Long
    ): List<InspectionReport> {

        return currentReportSnapshot()?.filter { it.projectId == projectId }
            ?: repository.findByProjectId(projectId)
    }
    
    /**
     * Повертає інспекцію
     * за її UUID.
     */
    fun getByUuid(
        uuid: String
    ): InspectionReport? {

        val normalizedUuid = uuid.trim()
        return currentReportSnapshot()
            ?.firstOrNull { it.uuid.toString() == normalizedUuid }
            ?: repository.findByUuid(normalizedUuid)
    }

    /**
     * Створює новий звіт інспекції.
     */
    fun createReport(
        projectId: Long,
        inspectionDate: String,
        summary: String?,
        createdBy: Long,
        reportCode: String? = null,
        inspectionType: String = "planned",
        latitude: Double? = null,
        longitude: Double? = null
    ): InspectionReport {
        validateDate(inspectionDate)

        return repository.create(

            projectId = projectId,

            inspectionDate = inspectionDate,

            summary = summary,

            createdBy = createdBy,
            reportCode = reportCode?.trim()?.takeIf(String::isNotBlank),
            inspectionType = validateType(inspectionType),
            latitude = validateLatitude(latitude),
            longitude = validateLongitude(longitude)
        ).also { invalidateReportCache() }
    }

    fun updateReport(
        uuid: String,
        inspectionDate: String,
        summary: String?,
        reportCode: String?,
        inspectionType: String,
        latitude: Double?,
        longitude: Double?
    ): InspectionReport? {
        validateDate(inspectionDate)
        val existing = getByUuid(uuid) ?: return null
        // Metadata corrections (date, type, SIR code and coordinates) remain possible
        // after review; the workflow status itself is still changed only via submit/review.
        return repository.update(
            uuid.trim(), inspectionDate, summary?.trim(),
            // The code is generated/imported report metadata, not an editable
            // UI field. Preserve it when a metadata update omits it.
            reportCode?.trim()?.takeIf(String::isNotBlank) ?: existing.reportCode, validateType(inspectionType),
            validateLatitude(latitude), validateLongitude(longitude)
        )?.also { invalidateReportCache() }
    }

    fun submitReport(uuid: String): InspectionReport? {
        val report = getByUuid(uuid) ?: return null
        require(report.status == InspectionReportStatus.DRAFT) {
            "Only draft inspection reports can be submitted."
        }
        return repository.changeStatus(uuid.trim(), "pending_review", null)
            ?.also { invalidateReportCache() }
    }

    /**
     * Allows an authorised manager to correct a report workflow state from the
     * edit screen. Inspectors still use the normal submit workflow only.
     */
    fun setStatus(uuid: String, status: String): InspectionReport? {
        val normalizedStatus = status.trim().lowercase()
        require(normalizedStatus in setOf("draft", "pending_review", "completed")) {
            "Inspection report status is invalid."
        }
        getByUuid(uuid) ?: return null
        return repository.changeStatus(uuid.trim(), normalizedStatus, null)
            ?.also { invalidateReportCache() }
    }

    fun reviewReport(
        uuid: String,
        action: String,
        rejectionReason: String?
    ): InspectionReport? {
        val report = getByUuid(uuid) ?: return null
        require(report.status == InspectionReportStatus.PENDING_REVIEW) {
            "Only submitted inspection reports can be reviewed."
        }
        return when (action.trim().lowercase()) {
            "approve" -> repository.changeStatus(uuid.trim(), "completed", null)
                ?.also { invalidateReportCache() }
            "reject" -> {
                val reason = rejectionReason?.trim()
                require(!reason.isNullOrBlank()) { "Rejection reason is required." }
                repository.changeStatus(uuid.trim(), "draft", reason)
                    ?.also { invalidateReportCache() }
            }
            else -> throw IllegalArgumentException("Action must be either approve or reject.")
        }
    }

    fun deleteReport(uuid: String): Boolean = repository.delete(uuid.trim())
        .also { deleted -> if (deleted) invalidateReportCache() }

    fun moveToProject(uuid: String, targetProjectId: Long): InspectionReport? {
        val report = getByUuid(uuid) ?: return null
        if (report.projectId == targetProjectId) return report
        return repository.moveToProject(uuid.trim(), targetProjectId)
            ?.also { invalidateReportCache() }
    }

    private fun validateDate(inspectionDate: String) {
        try {
            java.time.LocalDate.parse(inspectionDate.trim())
        } catch (_: Exception) {
            throw IllegalArgumentException("Inspection date must use YYYY-MM-DD format.")
        }
    }

    private fun validateType(value: String): String {
        val normalized = value.trim().lowercase()
        require(normalized in setOf("planned", "unplanned", "final")) {
            "Inspection type must be planned, unplanned or final."
        }
        return normalized
    }

    private fun validateLatitude(value: Double?): Double? {
        require(value == null || value in -90.0..90.0) { "Latitude must be between -90 and 90." }
        return value
    }

    private fun validateLongitude(value: Double?): Double? {
        require(value == null || value in -180.0..180.0) { "Longitude must be between -180 and 180." }
        return value
    }
}
