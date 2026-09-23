package oms.umitaf.repository

import oms.umitaf.database.tables.InspectionFindingTable
import oms.umitaf.database.tables.InspectionReportTable
import org.jetbrains.exposed.v1.jdbc.batchInsert
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

    override fun replaceCategory(inspectionReportId: Long, category: String, severity: FindingSeverity,
                                 entries: List<Pair<String, String?>>) {
        transaction {
            // Lock the parent even when no findings exist yet. Competing
            // replacements cannot interleave their delete/insert operations.
            require(InspectionReportTable.selectAll()
                .where { InspectionReportTable.id eq inspectionReportId }
                .forUpdate().firstOrNull() != null) { "Inspection report not found." }
            InspectionFindingTable.deleteWhere {
                (InspectionFindingTable.inspectionReportId eq inspectionReportId) and
                    (InspectionFindingTable.category eq category)
            }
            InspectionFindingTable.batchInsert(entries, shouldReturnGeneratedValues = false) { entry ->
                this[InspectionFindingTable.uuid] = UUID.randomUUID().toString()
                this[InspectionFindingTable.inspectionReportId] = inspectionReportId
                this[InspectionFindingTable.category] = category
                this[InspectionFindingTable.severity] = severity.name.lowercase()
                this[InspectionFindingTable.description] = entry.first
                this[InspectionFindingTable.recommendation] = entry.second
                this[InspectionFindingTable.isResolved] = false
            }
        }
    }

    /**
     * Повертає всі зауваження
     * для вказаної інспекції.
     */
    override fun findByInspectionReportId(
        inspectionReportId: Long
    ): List<InspectionFinding> = transaction {

        InspectionFindingTable
            .selectAll()
            .where { InspectionFindingTable.inspectionReportId eq inspectionReportId }
            .map(::toFinding)
    }

    override fun findByUuid(
        inspectionReportId: Long,
        uuid: String
    ): InspectionFinding? = transaction {
        InspectionFindingTable
            .selectAll()
            .where {
                (InspectionFindingTable.inspectionReportId eq inspectionReportId) and
                    (InspectionFindingTable.uuid eq uuid)
            }
            .limit(1)
            .firstOrNull()
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
