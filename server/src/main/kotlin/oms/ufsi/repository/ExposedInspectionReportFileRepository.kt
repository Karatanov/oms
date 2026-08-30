package oms.ufsi.repository

import oms.ufsi.database.tables.InspectionReportFileTable
import oms.ufsi.domain.InspectionReportFile
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class ExposedInspectionReportFileRepository : InspectionReportFileRepository {
    override fun findByReportId(reportId: Long) = transaction {
        InspectionReportFileTable.selectAll().firstOrNull { it[InspectionReportFileTable.inspectionReportId].value == reportId }?.let {
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

    override fun replace(file: InspectionReportFile) {
        transaction {
            val updated = InspectionReportFileTable.update({ InspectionReportFileTable.inspectionReportId eq file.inspectionReportId }) {
                it[originalName] = file.originalName
                it[storagePath] = file.storagePath
                it[contentType] = file.contentType
                it[fileSizeBytes] = file.fileSizeBytes
            }
            if (updated == 0) {
                InspectionReportFileTable.insert {
                    it[inspectionReportId] = file.inspectionReportId
                    it[originalName] = file.originalName
                    it[storagePath] = file.storagePath
                    it[contentType] = file.contentType
                    it[fileSizeBytes] = file.fileSizeBytes
                }
            }
        }
    }
}
