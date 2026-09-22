package oms.umitaf.service

import oms.umitaf.domain.InspectionReport
import oms.umitaf.domain.InspectionReportStatus
import oms.umitaf.repository.InspectionReportRepository
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InspectionReportServiceTest {
    @Test
    fun `draft can be submitted and then approved`() {
        val repository = InMemoryInspectionReportRepository(draftReport())
        val service = InspectionReportService(repository)

        val submitted = service.submitReport(REPORT_UUID)
        val completed = service.reviewReport(REPORT_UUID, "approve", null)

        assertEquals(InspectionReportStatus.PENDING_REVIEW, submitted?.status)
        assertEquals(InspectionReportStatus.COMPLETED, completed?.status)
    }

    @Test
    fun `review rejects invalid workflow transitions and requires a reason`() {
        val repository = InMemoryInspectionReportRepository(draftReport())
        val service = InspectionReportService(repository)

        assertFailsWith<IllegalArgumentException> { service.reviewReport(REPORT_UUID, "approve", null) }
        service.submitReport(REPORT_UUID)
        assertFailsWith<IllegalArgumentException> { service.reviewReport(REPORT_UUID, "reject", " ") }

        val rejected = service.reviewReport(REPORT_UUID, "reject", "Missing completion evidence")
        assertEquals(InspectionReportStatus.DRAFT, rejected?.status)
        assertEquals("Missing completion evidence", rejected?.rejectionReason)
    }

    private fun draftReport() = InspectionReport(
        id = 1,
        uuid = UUID.fromString(REPORT_UUID),
        projectId = 10,
        reportCode = null,
        inspectionType = "planned",
        inspectionDate = LocalDate.parse("2026-08-20"),
        summary = "Draft",
        status = InspectionReportStatus.DRAFT,
        rejectionReason = null,
        latitude = null,
        longitude = null,
        createdBy = 7
    )

    private class InMemoryInspectionReportRepository(initial: InspectionReport) : InspectionReportRepository {
        private var report = initial
        override fun findAll() = listOf(report)
        override fun findByProjectId(projectId: Long) = listOf(report).filter { it.projectId == projectId }
        override fun findByUuid(uuid: String) = report.takeIf { it.uuid.toString() == uuid }
        override fun create(projectId: Long, inspectionDate: String, summary: String?, createdBy: Long, reportCode: String?, inspectionType: String, latitude: Double?, longitude: Double?): InspectionReport = report
        override fun update(uuid: String, inspectionDate: String, summary: String?, reportCode: String?, inspectionType: String, latitude: Double?, longitude: Double?) = report
        override fun changeStatus(uuid: String, status: String, rejectionReason: String?): InspectionReport? {
            if (report.uuid.toString() != uuid) return null
            report = report.copy(status = InspectionReportStatus.valueOf(status.uppercase()), rejectionReason = rejectionReason)
            return report
        }
        override fun moveToProject(uuid: String, projectId: Long) = report
        override fun delete(uuid: String) = false
    }

    private companion object {
        const val REPORT_UUID = "00000000-0000-4000-8000-000000000001"
    }
}
