package oms.ufsi.repository

import oms.ufsi.domain.InspectionReportFile

interface InspectionReportFileRepository {
    fun findByReportId(reportId: Long): InspectionReportFile?
    fun create(file: InspectionReportFile)
    fun replace(file: InspectionReportFile)
}
