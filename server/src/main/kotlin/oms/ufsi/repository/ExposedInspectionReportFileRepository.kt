package oms.ufsi.repository

import oms.ufsi.database.tables.InspectionReportFileTable
import oms.ufsi.domain.InspectionReportFile
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExposedInspectionReportFileRepository : InspectionReportFileRepository {
    override fun findByReportId(reportId: Long) = transaction {
        InspectionReportFileTable.select(InspectionReportFileTable.inspectionReportId eq reportId).singleOrNull()?.let {
            InspectionReportFile(it[InspectionReportFileTable.inspectionReportId].value, it[InspectionReportFileTable.originalName], it[InspectionReportFileTable.storagePath], it[InspectionReportFileTable.contentType], it[InspectionReportFileTable.fileSizeBytes])
        }
    }
    override fun create(file: InspectionReportFile) {
        transaction {
        InspectionReportFileTable.insert {
            it[inspectionReportId] = file.inspectionReportId; it[originalName] = file.originalName; it[storagePath] = file.storagePath; it[contentType] = file.contentType; it[fileSizeBytes] = file.fileSizeBytes
        }
        }
    }
}
