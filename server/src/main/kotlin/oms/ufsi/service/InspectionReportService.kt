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
        completionPct: Double,
        summary: String?
    ): InspectionReport {

        /**
         * Відсоток готовності
         * повинен бути в межах 0..100.
         */
        if (completionPct !in 0.0..100.0) {

            throw IllegalArgumentException(
                "Відсоток готовності повинен бути від 0 до 100."
            )
        }

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

            completionPct = completionPct,

            summary = summary,

            createdBy = currentUserId
        )
    }

    fun updateReport(
        uuid: String,
        inspectionDate: String,
        completionPct: Double,
        summary: String?
    ): InspectionReport? {
        validate(completionPct, inspectionDate)
        val report = getByUuid(uuid) ?: return null
        require(report.status == InspectionReportStatus.DRAFT) {
            "Only draft inspection reports can be edited."
        }
        return repository.update(uuid.trim(), inspectionDate, completionPct, summary?.trim())
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

    private fun validate(completionPct: Double, inspectionDate: String) {
        require(completionPct in 0.0..100.0) {
            "Completion percentage must be between 0 and 100."
        }
        try {
            java.time.LocalDate.parse(inspectionDate.trim())
        } catch (_: Exception) {
            throw IllegalArgumentException("Inspection date must use YYYY-MM-DD format.")
        }
    }
}
