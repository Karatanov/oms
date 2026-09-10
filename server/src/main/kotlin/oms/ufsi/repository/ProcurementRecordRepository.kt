package oms.ufsi.repository

import oms.ufsi.database.tables.ProcurementRecordTable
import oms.ufsi.domain.ProcurementRecord
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import java.math.BigDecimal

interface ProcurementRecordRepository {
    fun findAll(): List<ProcurementRecord>
    fun findBySubProjectId(subProjectId: String): List<ProcurementRecord>
    fun create(record: ProcurementRecord): ProcurementRecord
    fun update(id: Long, record: ProcurementRecord): ProcurementRecord?
    fun delete(id: Long): Boolean
}

class ExposedProcurementRecordRepository : ProcurementRecordRepository {
    override fun findAll(): List<ProcurementRecord> = transaction {
        ProcurementRecordTable.selectAll().map(::map)
    }

    override fun findBySubProjectId(subProjectId: String): List<ProcurementRecord> = transaction {
        ProcurementRecordTable.selectAll()
            .where { ProcurementRecordTable.subProjectId eq subProjectId }
            .map(::map)
    }

    override fun create(record: ProcurementRecord): ProcurementRecord = transaction {
        val id = ProcurementRecordTable.insertAndGetId { row -> row.applyRecord(record) }.value
        ProcurementRecordTable.selectAll().first { it[ProcurementRecordTable.id].value == id }.let(::map)
    }

    override fun update(id: Long, record: ProcurementRecord): ProcurementRecord? = transaction {
        if (ProcurementRecordTable.update({ ProcurementRecordTable.id eq id }) { row -> row.applyRecord(record) } == 0) null
        else ProcurementRecordTable.selectAll().first { it[ProcurementRecordTable.id].value == id }.let(::map)
    }

    override fun delete(id: Long): Boolean = transaction { ProcurementRecordTable.deleteWhere { ProcurementRecordTable.id eq id } > 0 }

    private fun UpdateBuilder<*>.applyRecord(record: ProcurementRecord) {
        this[ProcurementRecordTable.recordNumber] = record.recordNumber
        this[ProcurementRecordTable.batchId] = record.batchId
        this[ProcurementRecordTable.oblastName] = record.oblastName
        this[ProcurementRecordTable.oblastId] = record.oblastId
        this[ProcurementRecordTable.promotorName] = record.promotorName
        this[ProcurementRecordTable.subprojectNameUk] = record.subprojectNameUk
        this[ProcurementRecordTable.subprojectNameEn] = record.subprojectNameEn
        this[ProcurementRecordTable.subProjectId] = record.subProjectId
        this[ProcurementRecordTable.subProjectLotId] = record.subProjectLotId
        this[ProcurementRecordTable.spId] = record.spId
        this[ProcurementRecordTable.sourceContractType] = record.sourceContractType
        this[ProcurementRecordTable.sourceType] = record.sourceType
        this[ProcurementRecordTable.procurementId] = record.procurementId
        this[ProcurementRecordTable.subprojectTotalCostUah] = record.subprojectTotalCostUah?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.subprojectEibFinancingUah] = record.subprojectEibFinancingUah?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.subprojectLocalFinancingUah] = record.subprojectLocalFinancingUah?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.estimatedTotalEur] = record.estimatedTotalEur?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.estimatedTotalUah] = record.estimatedTotalUah?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.estimatedEibEur] = record.estimatedEibEur?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.estimatedEibUah] = record.estimatedEibUah?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.estimatedLocalEur] = record.estimatedLocalEur?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.estimatedLocalUah] = record.estimatedLocalUah?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.purchaseStatus] = record.purchaseStatus
        this[ProcurementRecordTable.tenderId] = record.tenderId
        this[ProcurementRecordTable.prozorroTenderId] = record.prozorroTenderId
        this[ProcurementRecordTable.contractorNameUkr] = record.contractorNameUkr
        this[ProcurementRecordTable.contractorNameEng] = record.contractorNameEng
        this[ProcurementRecordTable.contractorId] = record.contractorId
        this[ProcurementRecordTable.contractDate] = record.contractDate
        this[ProcurementRecordTable.contractEndDate] = record.contractEndDate
        this[ProcurementRecordTable.contractDurationMonths] = record.contractDurationMonths
        this[ProcurementRecordTable.contractAmountUah] = record.contractAmountUah?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.contractAmountEur] = record.contractAmountEur?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.financingContractDifferencePct] = record.financingContractDifferencePct?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.procurementMethod] = record.procurementMethod
        this[ProcurementRecordTable.tenderDocumentType] = record.tenderDocumentType
        this[ProcurementRecordTable.publishedInOjeu] = record.publishedInOjeu
        this[ProcurementRecordTable.estimatedProzorroDate] = record.estimatedProzorroDate
        this[ProcurementRecordTable.estimatedBidSubmissionDate] = record.estimatedBidSubmissionDate
        this[ProcurementRecordTable.estimatedContractDate] = record.estimatedContractDate
        this[ProcurementRecordTable.estimatedContractEndDate] = record.estimatedContractEndDate
        this[ProcurementRecordTable.localFinancingPct] = record.localFinancingPct?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.comments] = record.comments
        this[ProcurementRecordTable.sourceStatusCode] = record.sourceStatusCode
        this[ProcurementRecordTable.projectId] = record.projectId?.let { org.jetbrains.exposed.v1.core.dao.id.EntityID(it, oms.ufsi.database.tables.ProjectTable) }
    }

    private fun map(row: org.jetbrains.exposed.v1.core.ResultRow) = ProcurementRecord(
        id = row[ProcurementRecordTable.id].value,
        recordNumber = row[ProcurementRecordTable.recordNumber], batchId = row[ProcurementRecordTable.batchId],
        oblastName = row[ProcurementRecordTable.oblastName], oblastId = row[ProcurementRecordTable.oblastId],
        subProjectId = row[ProcurementRecordTable.subProjectId], subProjectLotId = row[ProcurementRecordTable.subProjectLotId],
        purchaseStatus = row[ProcurementRecordTable.purchaseStatus], tenderId = row[ProcurementRecordTable.tenderId],
        prozorroTenderId = row[ProcurementRecordTable.prozorroTenderId], contractorNameUkr = row[ProcurementRecordTable.contractorNameUkr],
        contractorNameEng = row[ProcurementRecordTable.contractorNameEng], contractorId = row[ProcurementRecordTable.contractorId],
        contractDate = row[ProcurementRecordTable.contractDate], contractEndDate = row[ProcurementRecordTable.contractEndDate],
        contractDurationMonths = row[ProcurementRecordTable.contractDurationMonths],
        contractAmountUah = row[ProcurementRecordTable.contractAmountUah]?.toDouble(), contractAmountEur = row[ProcurementRecordTable.contractAmountEur]?.toDouble(),
        financingContractDifferencePct = row[ProcurementRecordTable.financingContractDifferencePct]?.toDouble(),
        promotorName = row[ProcurementRecordTable.promotorName], subprojectNameUk = row[ProcurementRecordTable.subprojectNameUk], subprojectNameEn = row[ProcurementRecordTable.subprojectNameEn],
        spId = row[ProcurementRecordTable.spId], sourceContractType = row[ProcurementRecordTable.sourceContractType], sourceType = row[ProcurementRecordTable.sourceType], procurementId = row[ProcurementRecordTable.procurementId],
        subprojectTotalCostUah = row[ProcurementRecordTable.subprojectTotalCostUah]?.toDouble(), subprojectEibFinancingUah = row[ProcurementRecordTable.subprojectEibFinancingUah]?.toDouble(), subprojectLocalFinancingUah = row[ProcurementRecordTable.subprojectLocalFinancingUah]?.toDouble(),
        estimatedTotalEur = row[ProcurementRecordTable.estimatedTotalEur]?.toDouble(), estimatedTotalUah = row[ProcurementRecordTable.estimatedTotalUah]?.toDouble(), estimatedEibEur = row[ProcurementRecordTable.estimatedEibEur]?.toDouble(), estimatedEibUah = row[ProcurementRecordTable.estimatedEibUah]?.toDouble(), estimatedLocalEur = row[ProcurementRecordTable.estimatedLocalEur]?.toDouble(), estimatedLocalUah = row[ProcurementRecordTable.estimatedLocalUah]?.toDouble(),
        procurementMethod = row[ProcurementRecordTable.procurementMethod], tenderDocumentType = row[ProcurementRecordTable.tenderDocumentType], publishedInOjeu = row[ProcurementRecordTable.publishedInOjeu],
        estimatedProzorroDate = row[ProcurementRecordTable.estimatedProzorroDate], estimatedBidSubmissionDate = row[ProcurementRecordTable.estimatedBidSubmissionDate], estimatedContractDate = row[ProcurementRecordTable.estimatedContractDate], estimatedContractEndDate = row[ProcurementRecordTable.estimatedContractEndDate],
        localFinancingPct = row[ProcurementRecordTable.localFinancingPct]?.toDouble(), comments = row[ProcurementRecordTable.comments], sourceStatusCode = row[ProcurementRecordTable.sourceStatusCode], projectId = row[ProcurementRecordTable.projectId]?.value
    )
}
