package oms.umitaf.service

import oms.umitaf.repository.ProcurementRecordRepository
import oms.umitaf.repository.UserRepository
import oms.umitaf.domain.ProcurementRecord
import oms.umitaf.domain.ProjectType
import oms.umitaf.dto.UpdateProjectRequest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong
import org.slf4j.LoggerFactory

class ProcurementService(
    private val repository: ProcurementRecordRepository,
    private val projectService: ProjectService,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    data class RefreshStatus(
        val id: String,
        val status: String,
        val progress: Int,
        val phase: String,
        val importedCount: Int? = null,
        val error: String? = null
    )

    private class RefreshJob(val id: String, val uploadedWorkbook: ByteArray? = null) {
        @Volatile var status = "RUNNING"
        @Volatile var progress = 5
        @Volatile var phase = "downloading"
        @Volatile var importedCount: Int? = null
        @Volatile var error: String? = null
        @Volatile var cancelRequested = false
    }

    private val refreshLock = Any()
    @Volatile private var activeRefresh: RefreshJob? = null
    @Volatile private var automaticRefreshStarted = false
    private var automaticRefreshExecutor: ScheduledExecutorService? = null

    fun getAll() = repository.findAll()
    fun getBySubProjectId(subProjectId: String) = repository.findBySubProjectId(subProjectId)

    /**
     * Keeps the procurement registry aligned with the approved DB6.3 source
     * without turning an application restart into a long, blocking download.
     * The existing refresh workflow validates the complete source before it
     * replaces records, so a failed scheduled run leaves the last good data
     * untouched.  The administrator can still request an immediate refresh.
     */
    fun startAutomaticApprovedSourceRefresh(
        initialDelayMinutes: Long,
        intervalHours: Long
    ) = synchronized(refreshLock) {
        if (automaticRefreshStarted) return
        automaticRefreshStarted = true
        automaticRefreshExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "procurement-auto-refresh").apply { isDaemon = true }
        }.also { executor ->
            executor.scheduleWithFixedDelay({
                try {
                    startApprovedSourceRefresh()
                    logger.info("Scheduled procurement registry refresh started.")
                } catch (error: IllegalStateException) {
                    logger.info("Scheduled procurement registry refresh skipped: {}", error.message)
                } catch (error: Exception) {
                    logger.warn("Scheduled procurement registry refresh could not start.", error)
                }
            }, initialDelayMinutes.coerceAtLeast(1), intervalHours.coerceAtLeast(1) * 60, TimeUnit.MINUTES)
        }
        logger.info(
            "Scheduled procurement registry refresh enabled: every {} hour(s), first run in {} minute(s).",
            intervalHours.coerceAtLeast(1), initialDelayMinutes.coerceAtLeast(1)
        )
    }

    fun stopAutomaticApprovedSourceRefresh() = synchronized(refreshLock) {
        automaticRefreshExecutor?.shutdownNow()
        automaticRefreshExecutor = null
        automaticRefreshStarted = false
    }

    /**
     * Rebuilds the registry from the approved DB6 workbook. Parsing and
     * project-link validation complete before the single replacement
     * transaction starts, so a bad or inaccessible source can never erase the
     * last good registry. A successful run replaces the complete registry:
     * DB-Procurement Tables.xlsx is the sole authoritative source.
     */
    /** Starts a cancellable, single-flight approved-source refresh. */
    fun startApprovedSourceRefresh(): RefreshStatus = synchronized(refreshLock) {
        startRefresh(null)
    }

    /**
     * A private Google Drive workbook cannot be fetched by the server with an
     * anonymous HTTP request.  This path accepts the same approved XLSX from
     * an administrator and applies the identical validation/replacement flow.
     */
    fun startApprovedWorkbookRefresh(workbook: ByteArray): RefreshStatus = synchronized(refreshLock) {
        require(workbook.isNotEmpty()) { "Select an approved procurement workbook." }
        require(workbook.size <= 20_000_000) { "The approved procurement workbook must not exceed 20 MB." }
        startRefresh(workbook)
    }

    private fun startRefresh(uploadedWorkbook: ByteArray?): RefreshStatus {
        activeRefresh?.takeIf { it.status in setOf("RUNNING", "CANCELLING") }?.let {
            throw IllegalStateException("A procurement refresh is already running.")
        }
        val job = RefreshJob(UUID.randomUUID().toString(), uploadedWorkbook)
        if (uploadedWorkbook != null) {
            job.progress = 10
            job.phase = "opening_workbook"
        }
        activeRefresh = job
        Thread({ runRefresh(job) }, "procurement-refresh-${job.id.take(8)}").apply {
            isDaemon = true
            start()
        }
        return job.snapshot()
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
                uploadedWorkbook = job.uploadedWorkbook,
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
        uploadedWorkbook: ByteArray? = null,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Int {
        if (uploadedWorkbook == null) onProgress(10, "downloading")
        val parseProgress: (Int, String) -> Unit = { progress, phase -> onProgress(progress, phase) }
        val sourceRows = uploadedWorkbook?.let { ProcurementWorkbookSource.parseApprovedWorkbook(it, parseProgress) }
            ?: ProcurementWorkbookSource.downloadAndParse(parseProgress)
        checkCancelled(shouldCancel)
        require(sourceRows.isNotEmpty()) { "The approved procurement workbook contains no records." }
        onProgress(47, "synchronizing_projects")
        synchronizeProjectHierarchy(sourceRows, shouldCancel, onProgress)
        checkCancelled(shouldCancel)
        onProgress(61, "validating")
        val projects = projectService.getAllProjects()
        val subprojects = projects.filter { it.projectType == ProjectType.SUBPROJECT }
            .associateBy { it.siteNumber.trim().uppercase() }
        val parts = projects.filter { it.projectType == ProjectType.SUBPROJECT_PART }
            .associateBy { it.siteNumber.trim().uppercase() }
        val records = sourceRows.mapIndexed { index, source ->
            if (index % 10 == 0) {
                checkCancelled(shouldCancel)
                onProgress(61 + (index * 28 / sourceRows.size), "validating")
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
                contractLink = source.contractLink,
                comments = source.comments, sourceStatusCode = source.sourceContractType, projectId = part?.id ?: subproject.id
            )
        }
        checkCancelled(shouldCancel)
        onProgress(90, "saving")
        return repository.replaceAll(records)
    }

    /**
     * DB6.3 is the approved source for the procurement registry. New rows may
     * reference a subproject or a lot that has not reached OMS yet, so create
     * the missing hierarchy before validating the procurement records.
     * Existing project records are deliberately left untouched: the workbook
     * does not contain a complete project passport and must not overwrite it.
     */
    private fun synchronizeProjectHierarchy(
        sourceRows: List<ProcurementWorkbookSource.SourceRow>,
        shouldCancel: () -> Boolean,
        onProgress: (Int, String) -> Unit
    ) {
        var projects = projectService.getAllProjects()
        val programme = projects.firstOrNull {
            it.projectType == ProjectType.PROJECT &&
                (it.siteNumber.equals("URP-III", true) || it.name.equals("Ukraine Recovery Programme III", true))
        } ?: throw IllegalArgumentException("The Ukraine Recovery Programme III parent project is missing.")
        // Import is a system-maintenance operation.  Older seed data may not
        // have an assignee on the root programme, but that must not prevent an
        // administrator from synchronising the approved procurement source.
        // Repair the missing assignment using an active administrator so the
        // newly created records also satisfy the project ownership invariant.
        val importManagerId = projectService.managerIdForProject(programme.uuid.toString())
            ?: userRepository.findAll()
                .firstOrNull { user ->
                    user.role.code.equals("admin", ignoreCase = true) &&
                        user.status.equals("active", ignoreCase = true) && !user.isArchived
                }
                ?.id
                ?.also { fallbackAdminId ->
                    projectService.bulkReassign(listOf(programme.uuid.toString()), fallbackAdminId)
                    logger.info("Assigned administrator {} to the root programme for procurement import.", fallbackAdminId)
                }
            ?: throw IllegalStateException("No active administrator is available to own imported projects.")

        val subprojectsByCode = projects.filter { it.projectType == ProjectType.SUBPROJECT }
            .associateBy { it.siteNumber.trim().uppercase() }.toMutableMap()
        val subprojectGroups = sourceRows.groupBy { it.subprojectCode.uppercase() }.entries.toList()
        subprojectGroups.forEachIndexed { index, (code, rows) ->
            checkCancelled(shouldCancel)
            if (index % 4 == 0) onProgress(47 + (index * 7 / subprojectGroups.size.coerceAtLeast(1)), "synchronizing_projects")
            if (code !in subprojectsByCode) {
                val source = rows.firstMeaningfulSource()
                val region = source.regionDetails()
                val subproject = projectService.createProject(
                    name = source.subprojectNameUk?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: source.subprojectNameEn?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: source.subprojectCode,
                    address = "",
                    region = region.name,
                    city = region.centre,
                    sector = "",
                    constructionType = source.inferredConstructionType(),
                    budgetPlanned = rows.importBudget(),
                    siteName = source.subprojectCode,
                    siteNumber = source.subprojectCode,
                    latitude = region.latitude,
                    longitude = region.longitude,
                    engineerConsultantContractAmount = null,
                    technicalSupervisionAmount = null,
                    projectType = "subproject",
                    parentProjectUuid = programme.uuid.toString(),
                    trancheNumber = source.trancheNumber(),
                    subprojectContractAmount = rows.importBudget(),
                    startDate = rows.primaryWorksContract()?.contractDate?.toString(),
                    contractSignedDate = rows.primaryWorksContract()?.contractDate?.toString(),
                    plannedEndDate = rows.primaryWorksContract()?.validPlannedEndDate()?.toString(),
                    managerId = importManagerId
                )
                enrichImportedProject(subproject, rows)
                subprojectsByCode[code] = subproject
            }
        }

        // Reload after creating subprojects so their generated IDs are used as
        // parents for any lot records from the same workbook.
        projects = projectService.getAllProjects()
        val partsByCode = projects.filter { it.projectType == ProjectType.SUBPROJECT_PART }
            .associateBy { it.siteNumber.trim().uppercase() }.toMutableMap()
        val partGroups = sourceRows.filter { it.partCode != null }.groupBy { it.partCode!!.uppercase() }.entries.toList()
        partGroups.forEachIndexed { index, (partCode, rows) ->
            checkCancelled(shouldCancel)
            if (index % 4 == 0) onProgress(54 + (index * 6 / partGroups.size.coerceAtLeast(1)), "synchronizing_projects")
            if (partCode !in partsByCode) {
                val source = rows.firstMeaningfulSource()
                val parent = subprojectsByCode[source.subprojectCode.uppercase()]
                    ?: throw IllegalArgumentException("Source row ${source.sourceId} references unknown subproject ${source.subprojectCode}.")
                val part = projectService.createProject(
                    name = source.subprojectNameUk?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: source.subprojectNameEn?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: source.partCode!!,
                    address = "",
                    region = parent.region.orEmpty(),
                    city = parent.city.orEmpty(),
                    sector = parent.sector.orEmpty(),
                    constructionType = parent.constructionType.orEmpty().ifBlank { source.inferredConstructionType() },
                    budgetPlanned = rows.importBudget(),
                    siteName = source.partCode!!,
                    siteNumber = source.partCode!!,
                    latitude = parent.latitude ?: source.regionDetails().latitude,
                    longitude = parent.longitude ?: source.regionDetails().longitude,
                    engineerConsultantContractAmount = null,
                    technicalSupervisionAmount = null,
                    projectType = "subproject_part",
                    parentProjectUuid = parent.uuid.toString(),
                    trancheNumber = parent.trancheNumber,
                    subprojectContractAmount = rows.importBudget(),
                    startDate = rows.primaryWorksContract()?.contractDate?.toString(),
                    contractSignedDate = rows.primaryWorksContract()?.contractDate?.toString(),
                    plannedEndDate = rows.primaryWorksContract()?.validPlannedEndDate()?.toString(),
                    managerId = importManagerId
                )
                enrichImportedProject(part, rows)
                partsByCode[partCode] = part
            }
        }
    }

    private fun List<ProcurementWorkbookSource.SourceRow>.firstMeaningfulSource() =
        firstOrNull { !it.subprojectNameUk.isNullOrBlank() || !it.subprojectNameEn.isNullOrBlank() } ?: first()

    private fun List<ProcurementWorkbookSource.SourceRow>.importBudget(): Long = asSequence()
        .flatMap { sequenceOf(it.contractAmountUah, it.estimatedEibUah) }
        .filterNotNull()
        .maxOrNull()
        ?.roundToLong()
        ?.coerceAtLeast(1L)
        ?: 1L

    private fun List<ProcurementWorkbookSource.SourceRow>.primaryWorksContract() =
        firstOrNull { it.sourceContractType.contains("works", true) || it.sourceContractType.contains("роботи", true) }

    private fun ProcurementWorkbookSource.SourceRow.effectiveContractEndDate() =
        actualisedContractEndDate ?: contractEndDate

    private fun ProcurementWorkbookSource.SourceRow.validPlannedEndDate() =
        effectiveContractEndDate()?.takeUnless { end -> contractDate?.isAfter(end) == true }

    /** Adds contract-specific information that is available in DB6.3 to a newly created record. */
    private fun enrichImportedProject(project: oms.umitaf.domain.Project, rows: List<ProcurementWorkbookSource.SourceRow>) {
        val works = rows.primaryWorksContract()
        val technicalSupervision = rows.firstOrNull { it.sourceContractType.contains("TS", true) }
        val engineerConsultant = rows.firstOrNull { it.sourceContractType.contains("CSC", true) }
        projectService.updateProject(project.uuid.toString(), UpdateProjectRequest(
            contractorName = (works?.contractorNameUkr ?: works?.contractorNameEng).limitedTo(255),
            constructionContractNumber = works?.contractReference.limitedTo(100),
            constructionContractSigningDate = works?.contractDate?.toString(),
            constructionStartDate = works?.contractDate?.toString(),
            projectedCompletionTime = works?.validPlannedEndDate()?.toString(),
            technicalSupervisionAmount = technicalSupervision?.contractAmountUah?.roundToLong(),
            technicalSupervisionName = (technicalSupervision?.contractorNameUkr ?: technicalSupervision?.contractorNameEng).limitedTo(255),
            technicalSupervisionContractNumber = technicalSupervision?.contractReference.limitedTo(100),
            technicalSupervisionContractDate = technicalSupervision?.contractDate?.toString(),
            technicalSupervisionStartDate = technicalSupervision?.contractDate?.toString(),
            technicalSupervisionPlannedEndDate = technicalSupervision?.validPlannedEndDate()?.toString(),
            engineerConsultantContractAmount = engineerConsultant?.contractAmountUah?.roundToLong(),
            engineerConsultantName = (engineerConsultant?.contractorNameUkr ?: engineerConsultant?.contractorNameEng).limitedTo(255),
            engineerConsultantContractNumber = engineerConsultant?.contractReference.limitedTo(100),
            engineerConsultantContractDate = engineerConsultant?.contractDate?.toString(),
            engineerConsultantStartDate = engineerConsultant?.contractDate?.toString(),
            engineerConsultantPlannedEndDate = engineerConsultant?.validPlannedEndDate()?.toString()
        ))
    }

    private fun ProcurementWorkbookSource.SourceRow.trancheNumber() = if (batchId == 9) 2 else 1

    private fun ProcurementWorkbookSource.SourceRow.inferredConstructionType(): String {
        val name = "${subprojectNameUk.orEmpty()} ${subprojectNameEn.orEmpty()}".lowercase()
        return when {
            "капіталь" in name || "capital repair" in name -> "capital_repair"
            "нове будівництво" in name || "new construction" in name -> "new_construction"
            else -> "reconstruction"
        }
    }

    private data class RegionDetails(val name: String, val centre: String, val latitude: Double, val longitude: Double)

    private fun ProcurementWorkbookSource.SourceRow.regionDetails(): RegionDetails = oblastDetails[oblastId.trim().uppercase()]
        ?: RegionDetails(oblastId.ifBlank { "Не визначено" }, "", 50.4501, 30.5234)

    private val oblastDetails = mapOf(
        "CH" to RegionDetails("Чернігівська", "Чернігів", 51.4982, 31.2893),
        "CK" to RegionDetails("Черкаська", "Черкаси", 49.4444, 32.0598),
        "CV" to RegionDetails("Чернівецька", "Чернівці", 48.2915, 25.9403),
        "DP" to RegionDetails("Дніпропетровська", "Дніпро", 48.4647, 35.0462),
        "IF" to RegionDetails("Івано-Франківська", "Івано-Франківськ", 48.9226, 24.7111),
        "KH" to RegionDetails("Харківська", "Харків", 49.9935, 36.2304),
        "KM" to RegionDetails("Хмельницька", "Хмельницький", 49.4229, 26.9871),
        "KR" to RegionDetails("Кіровоградська", "Кропивницький", 48.5079, 32.2623),
        "KS" to RegionDetails("Херсонська", "Херсон", 46.6354, 32.6169),
        "KV" to RegionDetails("Київська", "Київ", 50.4501, 30.5234),
        "LV" to RegionDetails("Львівська", "Львів", 49.8397, 24.0297),
        "MY" to RegionDetails("Миколаївська", "Миколаїв", 46.9750, 31.9946),
        "OD" to RegionDetails("Одеська", "Одеса", 46.4825, 30.7233),
        "PL" to RegionDetails("Полтавська", "Полтава", 49.5883, 34.5514),
        "RV" to RegionDetails("Рівненська", "Рівне", 50.6199, 26.2516),
        "SM" to RegionDetails("Сумська", "Суми", 50.9077, 34.7981),
        "VN" to RegionDetails("Вінницька", "Вінниця", 49.2328, 28.4810),
        "ZH" to RegionDetails("Житомирська", "Житомир", 50.2547, 28.6587),
        "ZK" to RegionDetails("Закарпатська", "Ужгород", 48.6208, 22.2879),
        "ZP" to RegionDetails("Запорізька", "Запоріжжя", 47.8388, 35.1396)
    )

    private fun String?.limitedTo(max: Int): String? = this?.trim()?.take(max)?.ifBlank { null }

}
