package oms.urp3.service

import oms.urp3.database.tables.ProjectMonitoringDetailTable
import oms.urp3.database.tables.ProjectTable
import oms.urp3.database.tables.UserTable
import oms.urp3.domain.ProjectType
import oms.urp3.repository.UserRepository
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Imports the approved monitoring workbook as one coherent registry snapshot.
 *
 * The project rows themselves are deliberately not deleted: financial records,
 * SIR reports and documents refer to them by foreign key.  Instead the source
 * attributes shown in the Subprojects registry are replaced atomically and
 * newly introduced subprojects/lots are created in the same transaction.
 */
class SubprojectRegistryRefreshService(
    private val userRepository: UserRepository,
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

    private class RefreshJob(val id: String, val workbook: ByteArray) {
        @Volatile var status = "RUNNING"
        @Volatile var progress = 5
        @Volatile var phase = "opening_workbook"
        @Volatile var importedCount: Int? = null
        @Volatile var error: String? = null
        @Volatile var cancelRequested = false
    }

    private val lock = Any()
    @Volatile private var activeRefresh: RefreshJob? = null

    fun startWorkbookRefresh(workbook: ByteArray): RefreshStatus = synchronized(lock) {
        require(workbook.isNotEmpty()) { "Select a subproject monitoring workbook." }
        require(workbook.size <= MAX_WORKBOOK_BYTES) { "The subproject monitoring workbook must not exceed 20 MB." }
        activeRefresh?.takeIf { it.status in setOf("RUNNING", "CANCELLING") }
            ?.let { throw IllegalStateException("A subproject registry refresh is already running.") }
        val job = RefreshJob(UUID.randomUUID().toString(), workbook)
        activeRefresh = job
        Thread({ runRefresh(job) }, "subproject-registry-refresh-${job.id.take(8)}").apply {
            isDaemon = true
            start()
        }
        job.snapshot()
    }

    fun refresh(id: String): RefreshStatus? = activeRefresh?.takeIf { it.id == id }?.snapshot()

    fun cancel(id: String): RefreshStatus? = synchronized(lock) {
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
            val rows = MonitoringWorkbookSource.parse(job.workbook) { progress, phase ->
                job.progress = progress
                job.phase = phase
            }
            checkCancelled(job)
            require(rows.isNotEmpty()) { "The monitoring workbook contains no subproject records." }
            job.progress = 65
            job.phase = "validating"
            val imported = replaceRegistry(rows, job)
            checkCancelled(job)
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
            job.error = error.message ?: "The subproject registry could not be refreshed."
        }
    }

    /** All parsing/validation happens before this one transaction begins. */
    private fun replaceRegistry(rows: List<MonitoringWorkbookSource.Row>, job: RefreshJob): Int {
        checkCancelled(job)
        job.progress = 82
        job.phase = "saving"
        val fallbackAdministratorId = userRepository.findAll().firstOrNull {
            it.role.code.equals("admin", true) && it.status.equals("active", true) && !it.isArchived
        }?.id
        val imported = transaction {
            val programme = ProjectTable.selectAll().firstOrNull {
                it[ProjectTable.projectType].equals("project", true) &&
                    (it[ProjectTable.siteNumber].equals(PROGRAMME_CODE, true) ||
                        it[ProjectTable.name].equals("Ukraine Recovery Programme III", true))
            } ?: throw IllegalStateException("The Ukraine Recovery Programme III parent project is missing.")
            val managerId = programme[ProjectTable.managerId]
                ?: fallbackAdministratorId?.let { EntityID(it, UserTable) }
                ?: throw IllegalStateException("No active administrator is available to own imported subprojects.")

            val projects = ProjectTable.selectAll().toList()
            val byCode = projects.associateBy { it[ProjectTable.siteNumber].trim().uppercase() }.toMutableMap()
            val monitoringByLot = ProjectMonitoringDetailTable.selectAll()
                .associateBy { it[ProjectMonitoringDetailTable.sourceLotId].trim().uppercase() }
            val monitoringByProjectId = ProjectMonitoringDetailTable.selectAll()
                .associateBy { it[ProjectMonitoringDetailTable.projectId].value }

            fun saveProject(
                code: String,
                type: ProjectType,
                parentId: Long?,
                row: MonitoringWorkbookSource.Row
            ): Long {
                val existing = byCode[code.uppercase()]
                val tranche = row.trancheNumber()
                val budget = row.number("fundingTotal").coerceAtLeast(0.0).toLong()
                val contract = row.number("contractedTotal").takeIf { it > 0 }?.toLong()
                val contractDate = row.date("contractDate")
                val contractEnd = row.date("contractEndDate")
                val name = row.value("name").ifBlank { row.value("nameEn") }.ifBlank { code }
                if (existing == null) {
                    val id = ProjectTable.insertAndGetId {
                        it[ProjectTable.uuid] = UUID.randomUUID().toString()
                        it[ProjectTable.projectType] = type.name.lowercase()
                        it[ProjectTable.trancheNumber] = tranche
                        it[ProjectTable.parentProjectId] = parentId?.let { parent -> EntityID(parent, ProjectTable) }
                        it[ProjectTable.name] = name
                        it[ProjectTable.siteName] = code
                        it[ProjectTable.siteNumber] = code
                        it[ProjectTable.address] = null
                        it[ProjectTable.region] = row.region()
                        it[ProjectTable.city] = null
                        it[ProjectTable.latitude] = null
                        it[ProjectTable.longitude] = null
                        it[ProjectTable.status] = "active"
                        it[ProjectTable.sector] = row.value("priority")
                        it[ProjectTable.constructionType] = row.constructionType()
                        it[ProjectTable.budgetPlanned] = budget
                        it[ProjectTable.engineerConsultantContractAmount] = null
                        it[ProjectTable.technicalSupervisionAmount] = null
                        it[ProjectTable.subprojectContractAmount] = contract
                        it[ProjectTable.startDate] = contractDate
                        it[ProjectTable.contractSignedDate] = contractDate
                        it[ProjectTable.plannedEndDate] = contractEnd
                        it[ProjectTable.currency] = "EUR"
                        it[ProjectTable.contractorName] = row.value("contractor").ifBlank { null }
                        it[ProjectTable.managerId] = managerId
                    }
                    byCode[code.uppercase()] = ProjectTable.selectAll().where { ProjectTable.id eq id }.first()
                    id.value
                } else if (!row.isAllocationSnapshot) {
                    val id = existing[ProjectTable.id].value
                    ProjectTable.update({ ProjectTable.id eq id }) {
                        it[ProjectTable.name] = name
                        it[ProjectTable.trancheNumber] = tranche
                        it[ProjectTable.region] = row.region()
                        it[ProjectTable.sector] = row.value("priority")
                        it[ProjectTable.constructionType] = row.constructionType()
                        it[ProjectTable.budgetPlanned] = budget
                        it[ProjectTable.subprojectContractAmount] = contract
                        it[ProjectTable.contractSignedDate] = contractDate
                        it[ProjectTable.plannedEndDate] = contractEnd
                        it[ProjectTable.contractorName] = row.value("contractor").ifBlank { null }
                        it[ProjectTable.status] = "active"
                        it[ProjectTable.isArchived] = false
                    }
                    id
                } else id
            }

            val subprojectRows = rows.groupBy { it.subprojectCode.uppercase() }
            val subprojectIds = subprojectRows.mapValues { (code, group) ->
                val representative = group.firstOrNull { it.lotCode.equals(code, true) } ?: group.first()
                saveProject(code, ProjectType.SUBPROJECT, programme[ProjectTable.id].value, representative)
            }

            rows.forEachIndexed { index, row ->
                if (index % 10 == 0) checkCancelled(job)
                val code = row.lotCode
                val parentId = subprojectIds[row.subprojectCode.uppercase()]
                    ?: error("Missing imported parent ${row.subprojectCode}.")
                val projectId = if (code.equals(row.subprojectCode, true)) parentId else
                    saveProject(code, ProjectType.SUBPROJECT_PART, parentId, row)
                val old = monitoringByLot[code.uppercase()] ?: monitoringByProjectId[projectId]
                // Allocation workbooks intentionally contain fewer columns than
                // DB Table.  Merge them into the previous source payload so
                // a refresh never erases existing contracts, links or work
                // progress merely because that source does not publish them.
                val previousValues = old?.get(ProjectMonitoringDetailTable.registryJson)
                    ?.let { runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap()) }
                    .orEmpty()
                val values = (previousValues + row.values.filterValues(String::isNotBlank)) +
                    mapOf("sourceWorkbook" to row.sourceWorkbook)
                val mergedRow = row.copy(values = values)
                if (old == null) {
                    ProjectMonitoringDetailTable.insert {
                        it[ProjectMonitoringDetailTable.projectId] = EntityID(projectId, ProjectTable)
                        applyMonitoring(mergedRow, values)
                    }
                } else {
                    ProjectMonitoringDetailTable.update({ ProjectMonitoringDetailTable.projectId eq projectId }) {
                        applyMonitoring(mergedRow, values)
                    }
                }
            }
            rows.size
        }
        projectService.invalidateRegistryCache()
        return imported
    }

    private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.applyMonitoring(
        row: MonitoringWorkbookSource.Row,
        values: Map<String, String>
    ) {
        this[ProjectMonitoringDetailTable.sourceBatchId] = row.trancheNumber()
        this[ProjectMonitoringDetailTable.sourceSubprojectId] = row.subprojectCode
        this[ProjectMonitoringDetailTable.sourceLotId] = row.lotCode
        this[ProjectMonitoringDetailTable.nameEn] = row.value("nameEn").ifBlank { null }
        this[ProjectMonitoringDetailTable.oblastCode] = row.region().ifBlank { null }
        this[ProjectMonitoringDetailTable.municipalityNameUk] = row.value("community").ifBlank { null }
        this[ProjectMonitoringDetailTable.municipalityNameEn] = row.value("communityEn").ifBlank { null }
        this[ProjectMonitoringDetailTable.priorityAreaSource] = row.value("priority").ifBlank { null }
        this[ProjectMonitoringDetailTable.projectManagerNameUk] = row.value("implementer").ifBlank { null }
        this[ProjectMonitoringDetailTable.projectManagerNameEn] = row.value("implementerEn").ifBlank { null }
        this[ProjectMonitoringDetailTable.projectManagerOrgId] = null
        this[ProjectMonitoringDetailTable.beneficiaryNameUk] = row.value("beneficiary").ifBlank { null }
        this[ProjectMonitoringDetailTable.beneficiaryNameEn] = row.value("beneficiaryEn").ifBlank { null }
        this[ProjectMonitoringDetailTable.beneficiaryOrgId] = null
        this[ProjectMonitoringDetailTable.dreamProjectId] = null
        this[ProjectMonitoringDetailTable.dreamProjectUrl] = row.value("dreamUrl").ifBlank { null }
        this[ProjectMonitoringDetailTable.applicationId] = null
        this[ProjectMonitoringDetailTable.dreamApplicationId] = null
        this[ProjectMonitoringDetailTable.constructionProcurementStatus] = null
        this[ProjectMonitoringDetailTable.constructionWorkStatus] = row.value("workStatus").ifBlank { null }
        this[ProjectMonitoringDetailTable.contractorNameEn] = row.value("contractorEn").ifBlank { null }
        this[ProjectMonitoringDetailTable.dedUrl] = row.value("dedUrl").ifBlank { null }
        this[ProjectMonitoringDetailTable.estimateUrl] = row.value("estimateUrl").ifBlank { null }
        this[ProjectMonitoringDetailTable.expertiseUrl] = row.value("expertiseUrl").ifBlank { null }
        this[ProjectMonitoringDetailTable.permitUrl] = row.value("permitUrl").ifBlank { null }
        this[ProjectMonitoringDetailTable.constructionActNumber] = row.value("actNumber").ifBlank { null }
        this[ProjectMonitoringDetailTable.constructionActDate] = row.value("actDate").ifBlank { null }
        this[ProjectMonitoringDetailTable.completedWorkPct] = row.value("completedWorkPct").ifBlank { null }
        this[ProjectMonitoringDetailTable.financingUsedPct] = row.value("financingUsedPct").ifBlank { null }
        this[ProjectMonitoringDetailTable.paidFinancingPct] = row.value("paidFinancingPct").ifBlank { null }
        this[ProjectMonitoringDetailTable.constructionWorkSummary] = row.value("workSummary").ifBlank { null }
        this[ProjectMonitoringDetailTable.constructionWorkComments] = row.value("workComments").ifBlank { null }
        this[ProjectMonitoringDetailTable.registryJson] = Json.encodeToString(values)
        this[ProjectMonitoringDetailTable.sourceRows] = row.sourceRows
        this[ProjectMonitoringDetailTable.sourceWorkbook] = row.sourceWorkbook
    }

    private fun RefreshJob.snapshot() = RefreshStatus(id, status, progress, phase, importedCount, error)
    private fun checkCancelled(job: RefreshJob) { if (job.cancelRequested) throw RefreshCancelled() }
    private class RefreshCancelled : RuntimeException()

    companion object {
        private const val PROGRAMME_CODE = "URP-III"
        private const val MAX_WORKBOOK_BYTES = 20_000_000
    }
}

/** Parser for the published DB_Monitoring table workbook. */
private object MonitoringWorkbookSource {
    private const val HEADER_ROW = 12
    private const val FIRST_DATA_ROW = 16
    private val formatter = DataFormatter()

    data class Row(
        val subprojectCode: String,
        val lotCode: String,
        val values: Map<String, String>,
        val sourceRows: String,
        val sourceWorkbook: String = "DB_Monitoring table.xlsx",
        val isAllocationSnapshot: Boolean = false
    ) {
        fun value(key: String) = values[key].orEmpty()
        fun number(key: String) = value(key).replace(Regex("\\s"), "").replace(',', '.').toDoubleOrNull() ?: 0.0
        fun date(key: String) = value(key).let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
        fun trancheNumber() = if (value("pool").trim().let { it == "9" || it.equals("B", true) }) 2 else 1
        fun region(): String = value("region").replace(Regex("\\s+(область|oblast)$", RegexOption.IGNORE_CASE), "").trim()
        fun constructionType(): String = when (value("constructionType").lowercase()) {
            "new construction", "нове будівництво" -> "new_construction"
            "capital repair", "капітальний ремонт" -> "capital_repair"
            else -> "reconstruction"
        }
    }

    fun parse(workbook: ByteArray, progress: (Int, String) -> Unit): List<Row> {
        progress(12, "opening_workbook")
        WorkbookFactory.create(ByteArrayInputStream(workbook)).use { book ->
            val sheet = book.getSheet("DB Table")
            if (sheet == null) return parseAllocationWorkbook(book, progress)
            progress(18, "reading_headers")
            val header = sheet.getRow(HEADER_ROW - 1) ?: throw IllegalArgumentException("The monitoring workbook does not contain its header row.")
            fun column(title: String): Int = (0 until header.lastCellNum).firstOrNull { index ->
                text(header.getCell(index)).equals(title, true)
            } ?: throw IllegalArgumentException("The monitoring workbook is missing the '$title' column.")
            val columns = mapOf(
                "pool" to 1, "region" to 2, "subprojectCode" to column("Sub-Project ID"), "lotCode" to column("Sub-Project Lot ID"),
                "name" to 8, "nameEn" to 9, "community" to 10, "communityEn" to 11, "priority" to 14,
                "constructionType" to 15, "implementer" to 16, "implementerEn" to 17, "beneficiary" to 19, "beneficiaryEn" to 20,
                "fundingTotal" to 24, "fundingWorks" to 25, "fundingTs" to 26, "fundingCsc" to 27, "eibCost" to 28, "localCost" to 29,
                "contractedTotal" to 36, "contractedEib" to 37, "contractedLocal" to 38, "approvedFundingPct" to 39,
                "contractor" to 47, "contractorEn" to 48, "contractDate" to 50, "contractEndDate" to 51, "contractDuration" to 52,
                "constructionAmount" to 53, "constructionEib" to 54, "constructionLocal" to 55, "paidConstruction" to 60,
                "dedUrl" to 61, "estimateUrl" to 62, "expertiseUrl" to 63, "permitUrl" to 67, "workStatus" to 77,
                "actNumber" to 78, "actDate" to 79, "completedWorkPct" to 80, "financingUsedPct" to 81, "paidFinancingPct" to 82,
                "workSummary" to 83, "workComments" to 84
            )
            val grouped = linkedMapOf<String, MutableList<Pair<Int, Map<String, String>>>>()
            for (index in FIRST_DATA_ROW - 1..sheet.lastRowNum) {
                val sourceRow = sheet.getRow(index) ?: continue
                val subproject = text(sourceRow.getCell(columns.getValue("subprojectCode"))).trim()
                val lot = text(sourceRow.getCell(columns.getValue("lotCode"))).trim().ifBlank { subproject }
                if (subproject.isBlank() || lot.isBlank()) continue
                val values = columns.mapValues { (_, cell) -> value(sourceRow.getCell(cell)) }
                grouped.getOrPut(lot.uppercase()) { mutableListOf() } += (index + 1) to values
                if (index % 25 == 0) progress(20 + (index * 38 / sheet.lastRowNum.coerceAtLeast(1)), "reading_rows")
            }
            return grouped.values.map { rows ->
                val first = rows.first().second
                val last = rows.last().second
                val merged = columns.keys.associateWith { key ->
                    if (key in FINANCIAL_OR_PROGRESS_FIELDS) last[key].orEmpty().ifBlank { first[key].orEmpty() }
                    else rows.asSequence().map { it.second[key].orEmpty() }.firstOrNull(String::isNotBlank).orEmpty()
                }.toMutableMap()
                val firstRow = rows.first().first
                val lastRow = rows.last().first
                Row(first.getValue("subprojectCode").trim(), first.getValue("lotCode").trim().ifBlank { first.getValue("subprojectCode").trim() }, merged, "$firstRow-$lastRow")
            }.also { require(it.isNotEmpty()) { "The monitoring workbook contains no subproject rows." } }
        }
    }

    /**
     * The approved Allocation workbook has Batch 8/9 sheets rather than the
     * DB Table sheet.  It is an intentionally narrower source, so its rows
     * are marked as snapshots and merge with existing registry properties.
     */
    private fun parseAllocationWorkbook(
        book: org.apache.poi.ss.usermodel.Workbook,
        progress: (Int, String) -> Unit
    ): List<Row> {
        val allocationSheets = (0 until book.numberOfSheets)
            .map(book::getSheetAt)
            .filter { sheet ->
                sheet.sheetName.contains("allocation", ignoreCase = true) &&
                    (0..minOf(12, sheet.lastRowNum)).any { rowIndex ->
                        sheet.getRow(rowIndex)?.let { row ->
                            (0 until row.lastCellNum).any { column -> text(row.getCell(column)).equals("Sub-Project ID", true) }
                        } ?: false
                    }
            }
        require(allocationSheets.isNotEmpty()) {
            "The workbook must contain either the 'DB Table' sheet or an Allocation Table with 'Sub-Project ID'."
        }
        val rows = mutableListOf<Row>()
        allocationSheets.forEachIndexed { sheetIndex, sheet ->
            val headerIndex = (0..minOf(15, sheet.lastRowNum)).firstOrNull { index ->
                val row = sheet.getRow(index) ?: return@firstOrNull false
                (0 until row.lastCellNum).any { column -> text(row.getCell(column)).equals("Sub-Project ID", true) } &&
                    (0 until row.lastCellNum).any { column -> text(row.getCell(column)).equals("Batch ID", true) }
            } ?: return@forEachIndexed
            val header = sheet.getRow(headerIndex) ?: return@forEachIndexed
            val columns = (0 until header.lastCellNum).associate { index ->
                text(header.getCell(index)).trim().lowercase().replace(Regex("\\s+"), " ") to index
            }
            fun column(title: String) = columns[title.lowercase().replace(Regex("\\s+"), " ")]
            val subprojectColumn = column("Sub-Project ID") ?: return@forEachIndexed
            val batchColumn = column("Batch ID") ?: return@forEachIndexed
            val workRows = (headerIndex + 1..sheet.lastRowNum).mapNotNull { index ->
                val row = sheet.getRow(index) ?: return@mapNotNull null
                val code = value(row.getCell(subprojectColumn)).trim()
                if (!code.matches(Regex("[A-Za-z]{2}\\d{2}_\\d{2}(?:#.+)?"))) return@mapNotNull null
                val field = { title: String -> column(title)?.let { value(row.getCell(it)).trim() }.orEmpty() }
                val batch = value(row.getCell(batchColumn)).trim()
                val values = mapOf(
                    "pool" to batch,
                    "region" to field("Oblast ID"),
                    "name" to field("Scheme Name ukr"),
                    "nameEn" to field("Scheme Name eng"),
                    "community" to field("Local Municipality"),
                    "priority" to field("Responsible Sector"),
                    "fundingTotal" to field("Total Investment Cost, UAH"),
                    "eibCost" to field("Total Investment Cost, UAH (excl. local co-financing)"),
                    "localCost" to field("Local co-financing, UAH"),
                    "beneficiaryEn" to field("Promotor / Final Beneficiary"),
                    "beneficiary" to field("FB Name")
                )
                Row(
                    subprojectCode = code.substringBefore('#'),
                    lotCode = code,
                    values = values,
                    sourceRows = "${sheet.sheetName}:${index + 1}",
                    sourceWorkbook = "UR III AT&PP Allocation Table.xlsx",
                    isAllocationSnapshot = true
                )
            }
            rows += workRows
            progress(20 + ((sheetIndex + 1) * 35 / allocationSheets.size), "reading_rows")
        }
        return rows.distinctBy { it.lotCode.uppercase() }.also {
            require(it.isNotEmpty()) { "The Allocation Table contains no subproject records." }
        }
    }

    /** The approved workbook uses cross-sheet formulas; retain Excel's cached value. */
    private fun text(cell: Cell?): String = cell?.let {
        if (it.cellType == CellType.FORMULA) cachedText(it) else formatter.formatCellValue(it)
    }?.trim().orEmpty()
    private fun value(cell: Cell?): String = when {
        cell == null -> ""
        cell.cellType == CellType.FORMULA && cell.cachedFormulaResultType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell) ->
            DateUtil.getLocalDateTime(cell.numericCellValue).toLocalDate().toString()
        DateUtil.isCellDateFormatted(cell) -> cell.localDateTimeCellValue.toLocalDate().toString()
        cell.hyperlink?.address?.isNotBlank() == true -> cell.hyperlink.address.trim()
        else -> text(cell)
    }

    private fun cachedText(cell: Cell): String = when (cell.cachedFormulaResultType) {
        CellType.STRING -> cell.stringCellValue
        CellType.NUMERIC -> formatter.formatRawCellContents(
            cell.numericCellValue, cell.cellStyle.dataFormat.toInt(), cell.cellStyle.dataFormatString
        )
        CellType.BOOLEAN -> cell.booleanCellValue.toString()
        else -> ""
    }

    private val FINANCIAL_OR_PROGRESS_FIELDS = setOf(
        "fundingTotal", "fundingWorks", "fundingTs", "fundingCsc", "eibCost", "localCost", "contractedTotal", "contractedEib", "contractedLocal",
        "approvedFundingPct", "contractor", "contractorEn", "contractDate", "contractEndDate", "contractDuration", "constructionAmount", "constructionEib",
        "constructionLocal", "paidConstruction", "workStatus", "actNumber", "actDate", "completedWorkPct", "financingUsedPct", "paidFinancingPct", "workSummary", "workComments"
    )
}
