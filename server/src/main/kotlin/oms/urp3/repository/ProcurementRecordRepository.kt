package oms.urp3.repository

import oms.urp3.database.tables.ProcurementRecordTable
import oms.urp3.domain.ProcurementRecord
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import java.math.BigDecimal

interface ProcurementRecordRepository {
    fun findAll(): List<ProcurementRecord>
    fun findBySubProjectId(subProjectId: String): List<ProcurementRecord>
    fun findBySubProjectLotId(subProjectLotId: String): List<ProcurementRecord>
    /** Replaces the complete procurement registry from the approved source, atomically. */
    /**
     * Replaces the approved registry and verifies that contract links survived
     * the database write before committing the transaction.
     */
    fun replaceAll(records: List<ProcurementRecord>, expectedContractLinkCount: Int): Int
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

    override fun findBySubProjectLotId(subProjectLotId: String): List<ProcurementRecord> = transaction {
        ProcurementRecordTable.selectAll()
            .where { ProcurementRecordTable.subProjectLotId eq subProjectLotId }
            .map(::map)
    }

    override fun replaceAll(records: List<ProcurementRecord>, expectedContractLinkCount: Int): Int = transaction {
        require(records.isNotEmpty()) { "Approved source contains no procurement records." }
        ProcurementRecordTable.deleteAll()
        ProcurementRecordTable.batchInsert(records, shouldReturnGeneratedValues = false) { record ->
            applyRecord(record)
        }
        val persistedContractLinks = ProcurementRecordTable.selectAll().count {
            !it[ProcurementRecordTable.contractLink].isNullOrBlank()
        }
        require(persistedContractLinks == expectedContractLinkCount) {
            "Only $persistedContractLinks of $expectedContractLinkCount contract links were saved. The procurement registry was not changed."
        }
        records.size
    }

    private fun UpdateBuilder<*>.applyRecord(record: ProcurementRecord) {
        this[ProcurementRecordTable.batchId] = record.batchId
        this[ProcurementRecordTable.oblastName] = record.oblastName
        this[ProcurementRecordTable.oblastId] = record.oblastId
        this[ProcurementRecordTable.promotorName] = record.promotorName
        this[ProcurementRecordTable.fbName] = record.fbName
        this[ProcurementRecordTable.subprojectNameUk] = record.subprojectNameUk
        this[ProcurementRecordTable.subprojectNameEn] = record.subprojectNameEn
        this[ProcurementRecordTable.subProjectId] = record.subProjectId
        this[ProcurementRecordTable.subProjectLotId] = record.subProjectLotId
        this[ProcurementRecordTable.procurementId] = record.procurementId
        this[ProcurementRecordTable.sourceContractType] = record.sourceContractType
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
        this[ProcurementRecordTable.actualisedContractEndDate] = record.actualisedContractEndDate
        this[ProcurementRecordTable.contractDurationMonths] = record.contractDurationMonths
        this[ProcurementRecordTable.contractAmountUah] = record.contractAmountUah?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.signedContractAmountUah] = record.signedContractAmountUah?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.contractAmountUahWithoutVat] = record.contractAmountUahWithoutVat?.let(BigDecimal::valueOf)
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
        this[ProcurementRecordTable.tenderAttemptCount] = record.tenderAttemptCount
        this[ProcurementRecordTable.pigViolations] = record.pigViolations
        this[ProcurementRecordTable.dreamCoFinancingPct] = record.dreamCoFinancingPct?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.realLocalCoFinancingPct] = record.realLocalCoFinancingPct?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.realEibFinancingUah] = record.realEibFinancingUah?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.bankGuarantee] = record.bankGuarantee?.let(BigDecimal::valueOf)
        this[ProcurementRecordTable.contractLink] = record.contractLink
        this[ProcurementRecordTable.comments] = record.comments
        this[ProcurementRecordTable.sourceStatusCode] = record.sourceStatusCode
        this[ProcurementRecordTable.projectId] = record.projectId?.let { org.jetbrains.exposed.v1.core.dao.id.EntityID(it, oms.urp3.database.tables.ProjectTable) }
    }

    private fun map(row: org.jetbrains.exposed.v1.core.ResultRow) = ProcurementRecord(
        id = row[ProcurementRecordTable.id].value,
        batchId = row[ProcurementRecordTable.batchId],
        oblastName = row[ProcurementRecordTable.oblastName], oblastId = row[ProcurementRecordTable.oblastId],
        subProjectId = row[ProcurementRecordTable.subProjectId], subProjectLotId = row[ProcurementRecordTable.subProjectLotId], procurementId = row[ProcurementRecordTable.procurementId],
        purchaseStatus = row[ProcurementRecordTable.purchaseStatus], tenderId = row[ProcurementRecordTable.tenderId],
        prozorroTenderId = row[ProcurementRecordTable.prozorroTenderId], contractorNameUkr = row[ProcurementRecordTable.contractorNameUkr],
        contractorNameEng = row[ProcurementRecordTable.contractorNameEng], contractorId = row[ProcurementRecordTable.contractorId],
        contractDate = row[ProcurementRecordTable.contractDate], contractEndDate = row[ProcurementRecordTable.contractEndDate], actualisedContractEndDate = row[ProcurementRecordTable.actualisedContractEndDate],
        contractDurationMonths = row[ProcurementRecordTable.contractDurationMonths],
        contractAmountUah = row[ProcurementRecordTable.contractAmountUah]?.toDouble(), signedContractAmountUah = row[ProcurementRecordTable.signedContractAmountUah]?.toDouble(), contractAmountUahWithoutVat = row[ProcurementRecordTable.contractAmountUahWithoutVat]?.toDouble(), contractAmountEur = row[ProcurementRecordTable.contractAmountEur]?.toDouble(),
        financingContractDifferencePct = row[ProcurementRecordTable.financingContractDifferencePct]?.toDouble(),
        promotorName = row[ProcurementRecordTable.promotorName], fbName = row[ProcurementRecordTable.fbName], subprojectNameUk = row[ProcurementRecordTable.subprojectNameUk], subprojectNameEn = row[ProcurementRecordTable.subprojectNameEn],
        sourceContractType = row[ProcurementRecordTable.sourceContractType],
        subprojectTotalCostUah = row[ProcurementRecordTable.subprojectTotalCostUah]?.toDouble(), subprojectEibFinancingUah = row[ProcurementRecordTable.subprojectEibFinancingUah]?.toDouble(), subprojectLocalFinancingUah = row[ProcurementRecordTable.subprojectLocalFinancingUah]?.toDouble(),
        estimatedTotalEur = row[ProcurementRecordTable.estimatedTotalEur]?.toDouble(), estimatedTotalUah = row[ProcurementRecordTable.estimatedTotalUah]?.toDouble(), estimatedEibEur = row[ProcurementRecordTable.estimatedEibEur]?.toDouble(), estimatedEibUah = row[ProcurementRecordTable.estimatedEibUah]?.toDouble(), estimatedLocalEur = row[ProcurementRecordTable.estimatedLocalEur]?.toDouble(), estimatedLocalUah = row[ProcurementRecordTable.estimatedLocalUah]?.toDouble(),
        procurementMethod = row[ProcurementRecordTable.procurementMethod], tenderDocumentType = row[ProcurementRecordTable.tenderDocumentType], publishedInOjeu = row[ProcurementRecordTable.publishedInOjeu],
        estimatedProzorroDate = row[ProcurementRecordTable.estimatedProzorroDate], estimatedBidSubmissionDate = row[ProcurementRecordTable.estimatedBidSubmissionDate], estimatedContractDate = row[ProcurementRecordTable.estimatedContractDate], estimatedContractEndDate = row[ProcurementRecordTable.estimatedContractEndDate],
        localFinancingPct = row[ProcurementRecordTable.localFinancingPct]?.toDouble(), tenderAttemptCount = row[ProcurementRecordTable.tenderAttemptCount], pigViolations = row[ProcurementRecordTable.pigViolations], dreamCoFinancingPct = row[ProcurementRecordTable.dreamCoFinancingPct]?.toDouble(), realLocalCoFinancingPct = row[ProcurementRecordTable.realLocalCoFinancingPct]?.toDouble(), realEibFinancingUah = row[ProcurementRecordTable.realEibFinancingUah]?.toDouble(), bankGuarantee = row[ProcurementRecordTable.bankGuarantee]?.toDouble(), contractLink = row[ProcurementRecordTable.contractLink], comments = row[ProcurementRecordTable.comments], sourceStatusCode = row[ProcurementRecordTable.sourceStatusCode], projectId = row[ProcurementRecordTable.projectId]?.value
    )
}
