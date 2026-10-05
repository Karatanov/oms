package oms.umitaf.service

import oms.umitaf.repository.ProcurementRecordRepository
import oms.umitaf.domain.ProcurementRecord
import oms.umitaf.domain.ProjectType
import oms.umitaf.dto.ProcurementRecordRequest
import java.time.LocalDate

class ProcurementService(
    private val repository: ProcurementRecordRepository,
    private val projectService: ProjectService
) {
    fun getAll() = repository.findAll()
    fun getBySubProjectId(subProjectId: String) = repository.findBySubProjectId(subProjectId)
    fun create(request: ProcurementRecordRequest) = repository.create(request.toRecord())
    fun update(id: Long, request: ProcurementRecordRequest) = repository.update(id, request.toRecord())
    fun delete(id: Long) = repository.delete(id)

    /**
     * Rebuilds the registry from the approved DB6 workbook. Parsing and
     * project-link validation complete before the single replacement
     * transaction starts, so a bad or inaccessible source can never erase the
     * last good registry. Batches absent from the source stay untouched.
     */
    fun refreshFromApprovedSource(): Int {
        val sourceRows = ProcurementWorkbookSource.downloadAndParse()
        require(sourceRows.isNotEmpty()) { "The approved procurement workbook contains no records." }
        val projects = projectService.getAllProjects()
        val subprojects = projects.filter { it.projectType == ProjectType.SUBPROJECT }
            .associateBy { it.siteNumber.trim().uppercase() }
        val parts = projects.filter { it.projectType == ProjectType.SUBPROJECT_PART }
            .associateBy { it.siteNumber.trim().uppercase() }
        val records = sourceRows.map { source ->
            val subproject = subprojects[source.subprojectCode.uppercase()]
                ?: throw IllegalArgumentException("Source row ${source.sourceId} references unknown subproject ${source.subprojectCode}.")
            val part = source.partCode?.let { code ->
                parts[code.uppercase()] ?: throw IllegalArgumentException("Source row ${source.sourceId} references unknown subproject part $code.")
            }
            require(part == null || part.parentProjectId == subproject.id) {
                "Source row ${source.sourceId} contains a subproject part outside ${source.subprojectCode}."
            }
            ProcurementRecord(
                id = 0, batchId = source.batchId,
                oblastName = subproject.region?.trim().orEmpty().ifBlank { source.oblastId },
                oblastId = source.oblastId, subProjectId = source.subprojectCode, subProjectLotId = source.partCode,
                purchaseStatus = source.purchaseStatus, tenderId = source.tenderId, prozorroTenderId = source.prozorroUrl,
                contractorNameUkr = null, contractorNameEng = null, contractorId = null,
                contractDate = source.contractDate, contractEndDate = null, contractDurationMonths = source.contractDurationMonths,
                contractAmountUah = source.contractAmountUah, contractAmountEur = null, financingContractDifferencePct = null,
                promotorName = source.promotorName, subprojectNameUk = subproject.name, subprojectNameEn = source.subprojectNameEn,
                sourceContractType = source.contractType, subprojectTotalCostUah = subproject.budgetPlanned.toDouble(),
                subprojectEibFinancingUah = null, subprojectLocalFinancingUah = null,
                estimatedTotalEur = null, estimatedTotalUah = source.estimatedTotalUah,
                estimatedEibEur = null, estimatedEibUah = source.estimatedEibUah,
                estimatedLocalEur = null, estimatedLocalUah = source.estimatedLocalUah,
                procurementMethod = source.procurementMethod, tenderDocumentType = null, publishedInOjeu = null,
                estimatedProzorroDate = source.estimatedProzorroDate,
                estimatedBidSubmissionDate = source.estimatedBidSubmissionDate,
                estimatedContractDate = source.estimatedContractDate,
                estimatedContractEndDate = source.estimatedContractEndDate,
                localFinancingPct = source.estimatedTotalUah?.takeIf { it != 0.0 }?.let { total -> source.estimatedLocalUah?.times(100.0)?.div(total) },
                comments = source.comments, sourceStatusCode = source.typeCode, projectId = part?.id ?: subproject.id
            )
        }
        return repository.replaceForBatches(records)
    }

    private fun ProcurementRecordRequest.toRecord(): ProcurementRecord {
        val subproject = projectService.getAllProjects().firstOrNull {
            it.projectType == ProjectType.SUBPROJECT && it.siteNumber.equals(subProjectId.trim(), ignoreCase = true)
        } ?: throw IllegalArgumentException("Select a valid subproject code.")
        val part = subProjectLotId.clean()?.let { lotCode ->
            projectService.getAllProjects().firstOrNull {
                it.projectType == ProjectType.SUBPROJECT_PART &&
                    it.parentProjectId == subproject.id &&
                    it.siteNumber.equals(lotCode, ignoreCase = true)
            } ?: throw IllegalArgumentException("Selected subproject part does not belong to this subproject.")
        }
        val derivedBatch = if (subproject.trancheNumber in setOf(2, 9)) 9 else 8
        val derivedRegion = subproject.region?.trim().orEmpty()
        require(derivedRegion.isNotBlank()) { "The selected subproject has no region." }
        require(contractDurationMonths == null || contractDurationMonths >= 0) { "Contract duration cannot be negative." }
        require(contractAmountUah == null || contractAmountUah >= 0) { "Contract amount cannot be negative." }
        require(contractAmountEur == null || contractAmountEur >= 0) { "Contract amount cannot be negative." }
        return ProcurementRecord(0, derivedBatch, derivedRegion, subproject.siteNumber.take(2).uppercase(), subproject.siteNumber, part?.siteNumber, purchaseStatus.clean(),
            tenderId.clean(), prozorroTenderId.clean(), contractorNameUkr.clean(), contractorNameEng.clean(), contractorId.clean(),
            contractDate.toDate(), contractEndDate.toDate(), contractDurationMonths, contractAmountUah, contractAmountEur, financingContractDifferencePct,
            promotorName.clean(), subproject.name, subprojectNameEn.clean(), sourceContractType.clean(),
            subprojectTotalCostUah, subprojectEibFinancingUah, subprojectLocalFinancingUah,
            estimatedTotalEur, estimatedTotalUah, estimatedEibEur, estimatedEibUah, estimatedLocalEur, estimatedLocalUah,
            procurementMethod.clean(), tenderDocumentType.clean(), publishedInOjeu.clean(),
            estimatedProzorroDate.toDate(), estimatedBidSubmissionDate.toDate(), estimatedContractDate.toDate(), estimatedContractEndDate.toDate(),
            localFinancingPct, comments.clean(), sourceStatusCode.clean(), projectId = part?.id ?: subproject.id)
    }

    private fun String?.clean() = this?.trim()?.ifBlank { null }
    private fun String?.toDate() = clean()?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw IllegalArgumentException("Dates must use YYYY-MM-DD.") } }
}
