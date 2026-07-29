package oms.ufsi.service

import oms.ufsi.domain.InspectionReport
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
}
