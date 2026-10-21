package oms.urp3.repository

import oms.urp3.domain.InspectionReportFile

interface InspectionReportFileRepository {
    fun findByReportId(reportId: Long): InspectionReportFile?
    fun create(file: InspectionReportFile)
    fun replace(file: InspectionReportFile)
}
