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
