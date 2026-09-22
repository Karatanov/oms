package oms.umitaf.repository

import oms.umitaf.database.tables.InspectionFindingTable
import oms.umitaf.domain.FindingSeverity
import oms.umitaf.domain.InspectionFinding
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*

/**
 * Реалізація репозиторію
 * зауважень інспекцій.
 */
class ExposedInspectionFindingRepository :
    InspectionFindingRepository {

    /**
     * Повертає всі зауваження
     * для вказаної інспекції.
     */
    override fun findByInspectionReportId(
        inspectionReportId: Long
    ): List<InspectionFinding> = transaction {

        InspectionFindingTable
            .selectAll()
            .filter { row ->
                row[InspectionFindingTable.inspectionReportId].value == inspectionReportId
            }
            .map(::toFinding)
    }

    override fun findByUuid(
        inspectionReportId: Long,
        uuid: String
    ): InspectionFinding? = transaction {
        InspectionFindingTable
            .selectAll()
            .firstOrNull {
                it[InspectionFindingTable.inspectionReportId].value == inspectionReportId &&
                    it[InspectionFindingTable.uuid] == uuid
            }
            ?.let(::toFinding)
    }

    override fun create(
        inspectionReportId: Long,
        category: String,
        severity: FindingSeverity,
        description: String,
        recommendation: String?
    ): InspectionFinding = transaction {
        val findingUuid = UUID.randomUUID()
        val findingId = InspectionFindingTable.insertAndGetId {
            it[uuid] = findingUuid.toString()
            it[this.inspectionReportId] = inspectionReportId
            it[this.category] = category
            it[this.severity] = severity.name.lowercase()
            it[this.description] = description
            it[this.recommendation] = recommendation
            it[this.isResolved] = false
        }

        InspectionFinding(
            id = findingId.value,
            uuid = findingUuid,
            inspectionReportId = inspectionReportId,
            category = category,
            severity = severity,
            description = description,
            recommendation = recommendation,
            isResolved = false
        )
    }

    override fun update(
        inspectionReportId: Long,
        uuid: String,
        category: String,
        severity: FindingSeverity,
        description: String,
        recommendation: String?,
        isResolved: Boolean
    ): InspectionFinding? = transaction {
        val updated = InspectionFindingTable.update({
            (InspectionFindingTable.inspectionReportId eq inspectionReportId) and
                (InspectionFindingTable.uuid eq uuid)
        }) {
            it[this.category] = category
            it[this.severity] = severity.name.lowercase()
            it[this.description] = description
            it[this.recommendation] = recommendation
            it[this.isResolved] = isResolved
        }

        if (updated == 0) null else findByUuid(inspectionReportId, uuid)
    }

    override fun delete(
        inspectionReportId: Long,
        uuid: String
    ): Boolean = transaction {
        InspectionFindingTable.deleteWhere {
            (InspectionFindingTable.inspectionReportId eq inspectionReportId) and
                (InspectionFindingTable.uuid eq uuid)
        } > 0
    }

    private fun toFinding(row: org.jetbrains.exposed.v1.core.ResultRow) =
        InspectionFinding(
            id = row[InspectionFindingTable.id].value,
            uuid = UUID.fromString(row[InspectionFindingTable.uuid]),
            inspectionReportId = row[InspectionFindingTable.inspectionReportId].value,
            category = row[InspectionFindingTable.category],
            severity = FindingSeverity.valueOf(row[InspectionFindingTable.severity].uppercase()),
            description = row[InspectionFindingTable.description],
            recommendation = row[InspectionFindingTable.recommendation],
            isResolved = row[InspectionFindingTable.isResolved]
        )
}
