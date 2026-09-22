package oms.umitaf.repository

import oms.umitaf.domain.InspectionReportFile

interface InspectionReportFileRepository {
    fun findByReportId(reportId: Long): InspectionReportFile?
    fun create(file: InspectionReportFile)
    fun replace(file: InspectionReportFile)
}
