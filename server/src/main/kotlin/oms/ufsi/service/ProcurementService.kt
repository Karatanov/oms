package oms.ufsi.service

import oms.ufsi.repository.ProcurementRecordRepository
import oms.ufsi.domain.ProcurementRecord
import oms.ufsi.dto.ProcurementRecordRequest
import java.time.LocalDate

class ProcurementService(private val repository: ProcurementRecordRepository) {
    fun getAll() = repository.findAll()
    fun getBySubProjectId(subProjectId: String) = repository.findBySubProjectId(subProjectId)
    fun create(request: ProcurementRecordRequest) = repository.create(request.toRecord())
    fun update(id: Long, request: ProcurementRecordRequest) = repository.update(id, request.toRecord())
    fun delete(id: Long) = repository.delete(id)

    private fun ProcurementRecordRequest.toRecord(): ProcurementRecord {
        require(recordNumber > 0) { "Record number must be positive." }
        require(batchId > 0) { "Pool number must be positive." }
        require(oblastName.isNotBlank() && oblastId.isNotBlank() && subProjectId.isNotBlank()) { "Region and subproject fields must not be blank." }
        require(contractDurationMonths == null || contractDurationMonths >= 0) { "Contract duration cannot be negative." }
        require(contractAmountUah == null || contractAmountUah >= 0) { "Contract amount cannot be negative." }
        require(contractAmountEur == null || contractAmountEur >= 0) { "Contract amount cannot be negative." }
        return ProcurementRecord(0, recordNumber, batchId, oblastName.trim(), oblastId.trim(), subProjectId.trim(), subProjectLotId.clean(), purchaseStatus.clean(),
            tenderId.clean(), prozorroTenderId.clean(), contractorNameUkr.clean(), contractorNameEng.clean(), contractorId.clean(),
            contractDate.toDate(), contractEndDate.toDate(), contractDurationMonths, contractAmountUah, contractAmountEur, financingContractDifferencePct,
            promotorName.clean(), subprojectNameUk.clean(), subprojectNameEn.clean(), spId.clean(), sourceContractType.clean(), sourceType.clean(), procurementId.clean(),
            subprojectTotalCostUah, subprojectEibFinancingUah, subprojectLocalFinancingUah,
            estimatedTotalEur, estimatedTotalUah, estimatedEibEur, estimatedEibUah, estimatedLocalEur, estimatedLocalUah,
            procurementMethod.clean(), tenderDocumentType.clean(), publishedInOjeu.clean(),
            estimatedProzorroDate.toDate(), estimatedBidSubmissionDate.toDate(), estimatedContractDate.toDate(), estimatedContractEndDate.toDate(),
            localFinancingPct, comments.clean(), sourceStatusCode.clean(), projectId = projectId)
    }

    private fun String?.clean() = this?.trim()?.ifBlank { null }
    private fun String?.toDate() = clean()?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw IllegalArgumentException("Dates must use YYYY-MM-DD.") } }
}
