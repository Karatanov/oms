package oms.umitaf.service

import oms.umitaf.repository.ProcurementRecordRepository
import oms.umitaf.domain.ProcurementRecord
import oms.umitaf.domain.ProjectType
import oms.umitaf.dto.ProcurementRecordRequest
import java.time.LocalDate
import java.util.UUID

class ProcurementService(
    private val repository: ProcurementRecordRepository,
    private val projectService: ProjectService
) {
    data class RefreshStatus(
        val id: String,
        val status: String,
        val progress: Int,
        val phase: String,
        val importedCount: Int? = null,
        val error: String? = null
    )

    private class RefreshJob(val id: String) {
        @Volatile var status = "RUNNING"
        @Volatile var progress = 5
        @Volatile var phase = "downloading"
        @Volatile var importedCount: Int? = null
        @Volatile var error: String? = null
        @Volatile var cancelRequested = false
    }

    private val refreshLock = Any()
    @Volatile private var activeRefresh: RefreshJob? = null

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
    /** Starts a cancellable, single-flight approved-source refresh. */
    fun startApprovedSourceRefresh(): RefreshStatus = synchronized(refreshLock) {
        activeRefresh?.takeIf { it.status in setOf("RUNNING", "CANCELLING") }?.let {
            throw IllegalStateException("A procurement refresh is already running.")
        }
        val job = RefreshJob(UUID.randomUUID().toString())
        activeRefresh = job
        Thread({ runRefresh(job) }, "procurement-refresh-${job.id.take(8)}").apply {
            isDaemon = true
            start()
        }
        job.snapshot()
    }

    fun getApprovedSourceRefresh(id: String): RefreshStatus? = activeRefresh
        ?.takeIf { it.id == id }
        ?.snapshot()

    /** Cancellation never touches the existing register. It is checked before replacement starts. */
    fun cancelApprovedSourceRefresh(id: String): RefreshStatus? = synchronized(refreshLock) {
        val job = activeRefresh?.takeIf { it.id == id } ?: return null
        if (job.status == "RUNNING") {
            job.cancelRequested = true
            job.status = "CANCELLING"
            job.phase = "cancelling"
        }
        job.snapshot()
    }

    private fun runRefresh(job: RefreshJob) {
        try {
            val imported = refreshFromApprovedSource(
                shouldCancel = { job.cancelRequested },
                onProgress = { progress, phase -> job.progress = progress; job.phase = phase }
            )
            if (job.cancelRequested) throw RefreshCancelled()
            job.importedCount = imported
            job.progress = 100
            job.phase = "completed"
            job.status = "COMPLETED"
        } catch (_: RefreshCancelled) {
            job.progress = 0
            job.phase = "cancelled"
            job.status = "CANCELLED"
        } catch (error: Exception) {
            job.phase = "failed"
            job.status = "FAILED"
            job.error = error.message ?: "The procurement registry could not be refreshed."
        }
    }

    private fun RefreshJob.snapshot() = RefreshStatus(id, status, progress, phase, importedCount, error)
    private class RefreshCancelled : RuntimeException()
    private fun checkCancelled(shouldCancel: () -> Boolean) { if (shouldCancel()) throw RefreshCancelled() }

    fun refreshFromApprovedSource(
        shouldCancel: () -> Boolean = { false },
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Int {
        onProgress(10, "downloading")
        val sourceRows = ProcurementWorkbookSource.downloadAndParse()
        checkCancelled(shouldCancel)
        onProgress(40, "validating")
        require(sourceRows.isNotEmpty()) { "The approved procurement workbook contains no records." }
        val projects = projectService.getAllProjects()
        val subprojects = projects.filter { it.projectType == ProjectType.SUBPROJECT }
            .associateBy { it.siteNumber.trim().uppercase() }
        val parts = projects.filter { it.projectType == ProjectType.SUBPROJECT_PART }
            .associateBy { it.siteNumber.trim().uppercase() }
        val records = sourceRows.mapIndexed { index, source ->
            if (index % 10 == 0) {
                checkCancelled(shouldCancel)
                onProgress(40 + (index * 45 / sourceRows.size), "validating")
            }
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
                purchaseStatus = source.purchaseStatus, tenderId = source.sourceId, prozorroTenderId = source.prozorroUrl,
                contractorNameUkr = source.contractorNameUkr, contractorNameEng = source.contractorNameEng, contractorId = source.contractorId,
                contractDate = source.contractDate, contractEndDate = source.contractEndDate,
                contractDurationMonths = source.contractDate?.let { start -> source.actualisedContractEndDate?.let { end -> java.time.temporal.ChronoUnit.MONTHS.between(start, end).toInt().coerceAtLeast(0) } },
                contractAmountUah = source.contractAmountUah, contractAmountEur = null, financingContractDifferencePct = null,
                actualisedContractEndDate = source.actualisedContractEndDate, contractAmountUahWithoutVat = source.contractAmountUahWithoutVat,
                promotorName = source.promotorName, fbName = source.fbName, subprojectNameUk = source.subprojectNameUk ?: subproject.name, subprojectNameEn = source.subprojectNameEn,
                sourceContractType = source.sourceContractType, subprojectTotalCostUah = subproject.budgetPlanned.toDouble(),
                subprojectEibFinancingUah = null, subprojectLocalFinancingUah = null,
                estimatedTotalEur = null, estimatedTotalUah = null,
                estimatedEibEur = null, estimatedEibUah = source.estimatedEibUah,
                estimatedLocalEur = null, estimatedLocalUah = null,
                procurementMethod = source.procurementMethod, tenderDocumentType = null, publishedInOjeu = null,
                estimatedProzorroDate = null, estimatedBidSubmissionDate = null,
                estimatedContractDate = null, estimatedContractEndDate = null,
                localFinancingPct = source.realLocalCoFinancingPct,
                tenderAttemptCount = source.tenderAttemptCount, pigViolations = source.pigViolations,
                dreamCoFinancingPct = source.dreamCoFinancingPct, realLocalCoFinancingPct = source.realLocalCoFinancingPct,
                realEibFinancingUah = source.realEibFinancingUah, bankGuarantee = source.bankGuarantee,
                comments = source.comments, sourceStatusCode = source.sourceContractType, projectId = part?.id ?: subproject.id
            )
        }
        checkCancelled(shouldCancel)
        onProgress(90, "saving")
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
        return ProcurementRecord(
            id = 0, batchId = derivedBatch, oblastName = derivedRegion, oblastId = subproject.siteNumber.take(2).uppercase(),
            subProjectId = subproject.siteNumber, subProjectLotId = part?.siteNumber, purchaseStatus = purchaseStatus.clean(),
            tenderId = tenderId.clean(), prozorroTenderId = prozorroTenderId.clean(), contractorNameUkr = contractorNameUkr.clean(),
            contractorNameEng = contractorNameEng.clean(), contractorId = contractorId.clean(), contractDate = contractDate.toDate(),
            contractEndDate = contractEndDate.toDate(), contractDurationMonths = contractDurationMonths, contractAmountUah = contractAmountUah,
            contractAmountEur = contractAmountEur, financingContractDifferencePct = financingContractDifferencePct,
            actualisedContractEndDate = actualisedContractEndDate.toDate(), contractAmountUahWithoutVat = contractAmountUahWithoutVat,
            promotorName = promotorName.clean(), fbName = fbName.clean(), subprojectNameUk = subproject.name,
            subprojectNameEn = subprojectNameEn.clean(), sourceContractType = sourceContractType.clean(),
            subprojectTotalCostUah = subprojectTotalCostUah, subprojectEibFinancingUah = subprojectEibFinancingUah,
            subprojectLocalFinancingUah = subprojectLocalFinancingUah, estimatedTotalEur = estimatedTotalEur,
            estimatedTotalUah = estimatedTotalUah, estimatedEibEur = estimatedEibEur, estimatedEibUah = estimatedEibUah,
            estimatedLocalEur = estimatedLocalEur, estimatedLocalUah = estimatedLocalUah, procurementMethod = procurementMethod.clean(),
            tenderDocumentType = tenderDocumentType.clean(), publishedInOjeu = publishedInOjeu.clean(),
            estimatedProzorroDate = estimatedProzorroDate.toDate(), estimatedBidSubmissionDate = estimatedBidSubmissionDate.toDate(),
            estimatedContractDate = estimatedContractDate.toDate(), estimatedContractEndDate = estimatedContractEndDate.toDate(),
            localFinancingPct = localFinancingPct, tenderAttemptCount = tenderAttemptCount, pigViolations = pigViolations.clean(),
            dreamCoFinancingPct = dreamCoFinancingPct, realLocalCoFinancingPct = realLocalCoFinancingPct,
            realEibFinancingUah = realEibFinancingUah, bankGuarantee = bankGuarantee, comments = comments.clean(),
            sourceStatusCode = sourceStatusCode.clean(), projectId = part?.id ?: subproject.id
        )
    }

    private fun String?.clean() = this?.trim()?.ifBlank { null }
    private fun String?.toDate() = clean()?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw IllegalArgumentException("Dates must use YYYY-MM-DD.") } }
}
