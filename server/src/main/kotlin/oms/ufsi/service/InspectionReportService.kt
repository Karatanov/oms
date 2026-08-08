package oms.ufsi.service

import oms.ufsi.domain.InspectionReport
import oms.ufsi.domain.InspectionReportStatus
import oms.ufsi.repository.InspectionReportRepository

/**
 * Бізнес-логіка роботи
 * зі звітами інспекцій.
 */
class InspectionReportService(
    private val repository:
    InspectionReportRepository
) {

    /**
     * Повертає всі інспекції проєкту.
     */
    fun getProjectReports(
        projectId: Long
    ): List<InspectionReport> {

        return repository.findByProjectId(
            projectId
        )
    }
    
    /**
     * Повертає інспекцію
     * за її UUID.
     */
    fun getByUuid(
        uuid: String
    ): InspectionReport? {

        return repository.findByUuid(
            uuid.trim()
        )
    }

    /**
     * Створює новий звіт інспекції.
     */
    fun createReport(
        projectId: Long,
        inspectionDate: String,
        summary: String?,
        createdBy: Long
    ): InspectionReport {
        validateDate(inspectionDate)

        /**
         * Тимчасово використовуємо
         * системного адміністратора.
         *
         * Після впровадження JWT
         * значення буде братися
         * з поточного користувача.
         */
        val currentUserId = 1L

        return repository.create(

            projectId = projectId,

            inspectionDate = inspectionDate,

            summary = summary,

            createdBy = createdBy
        )
    }

    fun updateReport(
        uuid: String,
        inspectionDate: String,
        summary: String?
    ): InspectionReport? {
        validateDate(inspectionDate)
        val report = getByUuid(uuid) ?: return null
        require(report.status == InspectionReportStatus.DRAFT) {
            "Only draft inspection reports can be edited."
        }
        return repository.update(uuid.trim(), inspectionDate, summary?.trim())
    }

    fun submitReport(uuid: String): InspectionReport? {
        val report = getByUuid(uuid) ?: return null
        require(report.status == InspectionReportStatus.DRAFT) {
            "Only draft inspection reports can be submitted."
        }
        return repository.changeStatus(uuid.trim(), "pending_review", null)
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
            "reject" -> {
                val reason = rejectionReason?.trim()
                require(!reason.isNullOrBlank()) { "Rejection reason is required." }
                repository.changeStatus(uuid.trim(), "draft", reason)
            }
            else -> throw IllegalArgumentException("Action must be either approve or reject.")
        }
    }

    fun deleteReport(uuid: String): Boolean = repository.delete(uuid.trim())

    fun moveToProject(uuid: String, targetProjectId: Long): InspectionReport? {
        val report = getByUuid(uuid) ?: return null
        if (report.projectId == targetProjectId) return report
        return repository.moveToProject(uuid.trim(), targetProjectId)
    }

    private fun validateDate(inspectionDate: String) {
        try {
            java.time.LocalDate.parse(inspectionDate.trim())
        } catch (_: Exception) {
            throw IllegalArgumentException("Inspection date must use YYYY-MM-DD format.")
        }
    }
}
