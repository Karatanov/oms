package oms.umitaf.service

import oms.umitaf.domain.InspectionFinding
import oms.umitaf.domain.FindingSeverity
import oms.umitaf.repository.InspectionFindingRepository

/**
 * Бізнес-логіка роботи
 * із зауваженнями інспекцій.
 */
class InspectionFindingService(
    private val repository:
    InspectionFindingRepository
) {

    /**
     * Повертає всі зауваження
     * конкретної інспекції.
     */
    fun getFindings(
        inspectionReportId: Long
    ): List<InspectionFinding> {

        return repository
            .findByInspectionReportId(
                inspectionReportId
            )
    }

    fun getFinding(
        inspectionReportId: Long,
        uuid: String
    ): InspectionFinding? = repository.findByUuid(
        inspectionReportId,
        uuid.trim()
    )

    fun createFinding(
        inspectionReportId: Long,
        category: String,
        severity: String,
        description: String,
        recommendation: String?
    ): InspectionFinding = repository.create(
        inspectionReportId = inspectionReportId,
        category = validateCategory(category),
        severity = parseSeverity(severity),
        description = validateDescription(description),
        recommendation = normalizeRecommendation(recommendation)
    )

    fun updateFinding(
        inspectionReportId: Long,
        uuid: String,
        category: String,
        severity: String,
        description: String,
        recommendation: String?,
        isResolved: Boolean
    ): InspectionFinding? = repository.update(
        inspectionReportId = inspectionReportId,
        uuid = uuid.trim(),
        category = validateCategory(category),
        severity = parseSeverity(severity),
        description = validateDescription(description),
        recommendation = normalizeRecommendation(recommendation),
        isResolved = isResolved
    )

    fun deleteFinding(
        inspectionReportId: Long,
        uuid: String
    ): Boolean = repository.delete(inspectionReportId, uuid.trim())

    private fun validateCategory(value: String): String {
        val category = value.trim()
        require(category.isNotBlank()) { "Category is required." }
        require(category.length <= 50) { "Category must not exceed 50 characters." }
        return category
    }

    private fun validateDescription(value: String): String {
        val description = value.trim()
        require(description.isNotBlank()) { "Description is required." }
        require(description.length <= 10_000) { "Description must not exceed 10000 characters." }
        return description
    }

    private fun normalizeRecommendation(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.also {
            require(it.length <= 10_000) {
                "Recommendation must not exceed 10000 characters."
            }
        }

    private fun parseSeverity(value: String): FindingSeverity = try {
        FindingSeverity.valueOf(value.trim().uppercase())
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException(
            "Severity must be one of: low, medium, high, critical."
        )
    }
}
