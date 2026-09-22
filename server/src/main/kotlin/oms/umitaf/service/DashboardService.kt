package oms.umitaf.service

import oms.umitaf.database.tables.*
import oms.umitaf.domain.InspectionReport
import oms.umitaf.domain.InspectionReportStatus
import oms.umitaf.domain.ProjectAmount
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.time.LocalDate

data class MonthlyActPayment(val month: String, val amountEurCents: Long)
/**
 * The approved amount in both presentation currencies.  Values are taken from
 * the frozen project conversion, never from a live NBU request made while the
 * dashboard is loading.
 */
data class DashboardSubprojectFunding(
    val projectUuid: String,
    val name: String,
    val region: String,
    val amountUah: Double,
    val amountEur: Double
)
data class DashboardSubprojectProgress(val projectUuid: String, val code: String, val name: String, val nameEn: String?, val region: String, val completionPct: Double)
data class DashboardMetric(val label: String, val value: Long)
data class DashboardRecentInspection(val report: InspectionReport, val subprojectCode: String?)
data class DashboardOverviewData(
    val recentInspections: List<DashboardRecentInspection>,
    val monthlyActPayments: List<MonthlyActPayment>,
    val subprojectFunding: List<DashboardSubprojectFunding>,
    val subprojectProgress: List<DashboardSubprojectProgress>,
    val procurementStatusCounts: List<DashboardMetric>
)

private val procurementStatusOrder = listOf(
    "Не розпочато / Not Started",
    "Закупівля триває / Tender Ongoing",
    "Повідомлення про намір укласти договір / Contract award notice",
    "Договір укладено / Contract signed",
    "Відмінено / Cancelled",
    "Договір розірвано / Contract terminated"
)

/**
 * Source procurement spreadsheets contain a few technical values (for example
 * a batch id) in otherwise free-text cells.  A dashboard category must only
 * ever be one of the six statuses agreed for the UI, so normalize bilingual
 * values and ignore anything else rather than rendering a phantom bar.
 */
private fun canonicalProcurementStatus(value: String?): String? {
    val status = value?.trim()?.lowercase().orEmpty()
    return when {
        status.contains("не розпочато") || status.contains("not started") -> procurementStatusOrder[0]
        status.contains("закупівля триває") || status.contains("tender ongoing") -> procurementStatusOrder[1]
        status.contains("повідомлення про намір") || status.contains("contract award notice") -> procurementStatusOrder[2]
        status.contains("договір укладено") || status.contains("contract signed") -> procurementStatusOrder[3]
        status.contains("відмінено") || status.contains("cancelled") -> procurementStatusOrder[4]
        status.contains("договір розірвано") || status.contains("contract terminated") -> procurementStatusOrder[5]
        else -> null
    }
}

private fun procurementStatusMetrics(procurementRecords: List<org.jetbrains.exposed.v1.core.ResultRow>): List<DashboardMetric> {
    val countByStatus = procurementRecords
        .mapNotNull { row -> canonicalProcurementStatus(row[ProcurementRecordTable.purchaseStatus])?.let { it to row[ProcurementRecordTable.subProjectId] } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, subprojectIds) -> subprojectIds.distinct().size.toLong() }
    return procurementStatusOrder.map { status -> DashboardMetric(status, countByStatus[status] ?: 0L) }
}

private fun isConstructionContractSigned(status: String?) =
    status?.let { it.contains("Договір укладено", ignoreCase = true) || it.contains("Contract signed", ignoreCase = true) } == true

/** Source workbooks identify tranches as batches 8 and 9; user-created records
 * use the application values 1 and 2. Both pairs represent the same A/B cut. */
private fun Int.matchesTrancheFilter(filter: Int?): Boolean = filter == null || when (filter) {
    1 -> this == 1 || this == 8
    2 -> this == 2 || this == 9
    else -> false
}
data class DashboardData(
    val projectsTotal:Long, val projectsActive:Long, val projectsCompletedThisMonth:Long, val budgetPlanned:Long,
    val amountSpent:Long, val inspectionsTotal:Long, val pendingInspections:Long, val findingsTotal:Long,
    val recentInspections:List<InspectionReport>, val activities:List<ActivityEntry>, val monthlyActPayments:List<MonthlyActPayment>,
    val subprojectFunding: List<DashboardSubprojectFunding>, val subprojectProgress: List<DashboardSubprojectProgress>,
    val procurementStatusCounts: List<DashboardMetric>, val monthlyInspectionCounts: List<DashboardMetric>,
    val monthlyEshsViolations: List<DashboardMetric>, val monthlyEquipmentPayments: List<MonthlyActPayment>,
    val monthlySignedConstructionContracts: List<DashboardMetric>
)

class DashboardService(
    private val auditLogService: AuditLogService
) {
    /**
     * Data used only by the visual Dashboard.  Keep it independent from the
     * broader administrative Dashboard payload so opening the workspace does
     * not wait for audit history, inspection findings or unused financial cuts.
     */
    fun getOverview(allowedProjectIds: Set<Long>? = null, trancheNumber: Int? = null): DashboardOverviewData = transaction {
        val projects = ProjectTable.selectAll().toList()
            .filter {
                (allowedProjectIds == null || it[ProjectTable.id].value in allowedProjectIds) &&
                    it[ProjectTable.trancheNumber].matchesTrancheFilter(trancheNumber)
            }
        val projectIds = projects.map { it[ProjectTable.id].value }.toSet()
        val reports = InspectionReportTable.selectAll().toList()
            .filter { it[InspectionReportTable.projectId].value in projectIds }
        val actRecords = FinancialRecordTable.selectAll().toList().filter {
            it[FinancialRecordTable.projectId].value in projectIds &&
                it[FinancialRecordTable.recordType] == "act"
        }
        val paymentRecords = FinancialRecordTable.selectAll().toList().filter {
            it[FinancialRecordTable.projectId].value in projectIds &&
                it[FinancialRecordTable.recordType] in setOf("payment", "advance")
        }
        fun month(date: java.time.LocalDate) = date.toString().take(7)
        val monthlyActPayments = paymentRecords.filter { it[FinancialRecordTable.amountEurCents] != null }
            .groupBy { month(it[FinancialRecordTable.recordDate]) }
            .map { (label, rows) -> MonthlyActPayment(label, rows.sumOf { it[FinancialRecordTable.amountEurCents] ?: 0L }) }
            .sortedBy { it.month }
        val projectsById = projects.associateBy { it[ProjectTable.id].value }
        val nameEnByProjectId = ProjectMonitoringDetailTable.selectAll().associate {
            it[ProjectMonitoringDetailTable.projectId].value to it[ProjectMonitoringDetailTable.nameEn]
        }
        val subprojects = projects.filter { it[ProjectTable.projectType] == "subproject" }
        val amountsByProject = ProjectAmountTable.selectAll()
            .groupBy { it[ProjectAmountTable.projectId].value }
            .mapValues { (_, rows) -> rows.associate { it[ProjectAmountTable.kind] to ProjectAmount(
                it[ProjectAmountTable.amount], it[ProjectAmountTable.currency], it[ProjectAmountTable.convertedAmount],
                it[ProjectAmountTable.uahPerEur], it[ProjectAmountTable.rateDate], it[ProjectAmountTable.conversionEdited]
            ) } }
        val subprojectFunding = subprojects.map { row -> row.toFunding(amountsByProject[row[ProjectTable.id].value]) }
        fun subprojectFor(projectId: Long): Long? {
            var current = projectId
            while (true) {
                val project = projectsById[current] ?: return null
                if (project[ProjectTable.projectType] == "subproject") return current
                current = project[ProjectTable.parentProjectId]?.value ?: return null
            }
        }
        val actualBySubproject = actRecords.groupBy { subprojectFor(it[FinancialRecordTable.projectId].value) }
            .filterKeys { it != null }
            .mapValues { (_, rows) -> rows.sumOf { it[FinancialRecordTable.amount] }.toDouble() }
        val procurementProjectCodes = projects.mapNotNull { it[ProjectTable.siteNumber]?.trim()?.lowercase()?.takeIf(String::isNotBlank) }.toSet() +
            projects.map { it[ProjectTable.uuid].lowercase() }.toSet()
        val procurementRecords = if (allowedProjectIds == null) ProcurementRecordTable.selectAll().toList().filter { record ->
            trancheNumber == null || record[ProcurementRecordTable.subProjectId].trim().lowercase() in procurementProjectCodes
        } else emptyList()
        // Progress must cover every subproject.  A missing or unsigned
        // construction contract simply results in 0%, rather than hiding its
        // region from the dashboard filter.
        val subprojectProgress = subprojects.map { row ->
            val id = row[ProjectTable.id].value
            val contract = row[ProjectTable.subprojectContractAmount] ?: row[ProjectTable.budgetPlanned]
            DashboardSubprojectProgress(
                row[ProjectTable.uuid], row[ProjectTable.siteNumber].orEmpty(), row[ProjectTable.name], nameEnByProjectId[id], row[ProjectTable.region].orEmpty(),
                if (contract > 0) (actualBySubproject[id] ?: 0.0) * 100.0 / contract else 0.0
            )
        }.sortedBy { it.name }
        val procurementStatusCounts = procurementStatusMetrics(procurementRecords)
        fun report(row: org.jetbrains.exposed.v1.core.ResultRow) = InspectionReport(
            row[InspectionReportTable.id].value, UUID.fromString(row[InspectionReportTable.uuid]), row[InspectionReportTable.projectId].value,
            row[InspectionReportTable.reportCode], row[InspectionReportTable.inspectionType], row[InspectionReportTable.inspectionDate],
            row[InspectionReportTable.summary], InspectionReportStatus.valueOf(row[InspectionReportTable.status].uppercase()),
            row[InspectionReportTable.rejectionReason], row[InspectionReportTable.latitude]?.toDouble(), row[InspectionReportTable.longitude]?.toDouble(), row[InspectionReportTable.createdBy].value
        )
        DashboardOverviewData(
            reports.sortedByDescending { it[InspectionReportTable.inspectionDate] }.take(5).map { row ->
                val inspection = report(row)
                DashboardRecentInspection(
                    inspection,
                    subprojectFor(inspection.projectId)?.let { projectsById[it]?.get(ProjectTable.siteNumber) }
                )
            },
            monthlyActPayments, subprojectFunding, subprojectProgress, procurementStatusCounts
        )
    }

    fun get(allowedProjectIds: Set<Long>? = null, trancheNumber: Int? = null): DashboardData {
        return transaction {
        val projects = ProjectTable.selectAll().toList().filter {
            (allowedProjectIds == null || it[ProjectTable.id].value in allowedProjectIds) &&
                it[ProjectTable.trancheNumber].matchesTrancheFilter(trancheNumber)
        }
        val projectIds = projects.map { it[ProjectTable.id].value }.toSet()
        val reports = InspectionReportTable.selectAll().toList().filter { it[InspectionReportTable.projectId].value in projectIds }
        val reportIds = reports.map { it[InspectionReportTable.id].value }.toSet()
        val actRecords = FinancialRecordTable.selectAll().filter {
            it[FinancialRecordTable.projectId].value in projectIds && it[FinancialRecordTable.recordType] == "act"
        }
        val paymentRecords = FinancialRecordTable.selectAll().filter {
            it[FinancialRecordTable.projectId].value in projectIds &&
                it[FinancialRecordTable.recordType] in setOf("payment", "advance")
        }
        val spent = actRecords.sumOf { it[FinancialRecordTable.amount] }.toDouble()
        val monthlyActPayments = paymentRecords.filter { it[FinancialRecordTable.amountEurCents] != null }
            .groupBy { it[FinancialRecordTable.recordDate].toString().take(7) }
            .map { (month, records) -> MonthlyActPayment(month, records.sumOf { it[FinancialRecordTable.amountEurCents] ?: 0L }) }
            .sortedBy { it.month }
        val projectsById = projects.associateBy { it[ProjectTable.id].value }
        val nameEnByProjectId = ProjectMonitoringDetailTable.selectAll().associate {
            it[ProjectMonitoringDetailTable.projectId].value to it[ProjectMonitoringDetailTable.nameEn]
        }
        val subprojects = projects.filter { it[ProjectTable.projectType] == "subproject" }
        val amountsByProject = ProjectAmountTable.selectAll()
            .groupBy { it[ProjectAmountTable.projectId].value }
            .mapValues { (_, rows) -> rows.associate { it[ProjectAmountTable.kind] to ProjectAmount(
                it[ProjectAmountTable.amount], it[ProjectAmountTable.currency], it[ProjectAmountTable.convertedAmount],
                it[ProjectAmountTable.uahPerEur], it[ProjectAmountTable.rateDate], it[ProjectAmountTable.conversionEdited]
            ) } }
        val subprojectFunding = subprojects.map { row -> row.toFunding(amountsByProject[row[ProjectTable.id].value]) }
        fun subprojectFor(projectId: Long): Long? {
            var current = projectId
            while (true) {
                val project = projectsById[current] ?: return null
                if (project[ProjectTable.projectType] == "subproject") return current
                current = project[ProjectTable.parentProjectId]?.value ?: return null
            }
        }
        val actualBySubproject = actRecords.groupBy { subprojectFor(it[FinancialRecordTable.projectId].value) }
            .filterKeys { it != null }
            .mapValues { (_, records) -> records.sumOf { it[FinancialRecordTable.amount] }.toDouble() }
        val procurementProjectCodes = projects.mapNotNull { it[ProjectTable.siteNumber]?.trim()?.lowercase()?.takeIf(String::isNotBlank) }.toSet() +
            projects.map { it[ProjectTable.uuid].lowercase() }.toSet()
        val procurementRecords = if (allowedProjectIds == null) ProcurementRecordTable.selectAll().toList().filter { record ->
            trancheNumber == null || record[ProcurementRecordTable.subProjectId].trim().lowercase() in procurementProjectCodes
        } else emptyList()
        val subprojectProgress = subprojects.map { row ->
            val id = row[ProjectTable.id].value
            val contract = row[ProjectTable.subprojectContractAmount] ?: row[ProjectTable.budgetPlanned]
            DashboardSubprojectProgress(row[ProjectTable.uuid], row[ProjectTable.siteNumber].orEmpty(), row[ProjectTable.name], nameEnByProjectId[id], row[ProjectTable.region].orEmpty(), if (contract > 0) (actualBySubproject[id] ?: 0.0) * 100.0 / contract else 0.0)
        }.sortedBy { it.name }
        fun month(date: java.time.LocalDate) = date.toString().take(7)
        val monthlyInspectionCounts = reports.groupBy { month(it[InspectionReportTable.inspectionDate]) }
            .map { DashboardMetric(it.key, it.value.size.toLong()) }.sortedBy { it.label }
        val reportMonthById = reports.associate { it[InspectionReportTable.id].value to month(it[InspectionReportTable.inspectionDate]) }
        val monthlyEshsViolations = InspectionFindingTable.selectAll().toList()
            .filter { it[InspectionFindingTable.inspectionReportId].value in reportIds }
            .filter { finding ->
                val category = finding[InspectionFindingTable.category].lowercase()
                category.contains("hse") || category.contains("eshs") || category.contains("health") || category.contains("safety") || category.contains("environment") || category.contains("social")
            }
            .groupBy { reportMonthById[it[InspectionFindingTable.inspectionReportId].value] }
            .filterKeys { it != null }
            .map { DashboardMetric(it.key!!, it.value.size.toLong()) }.sortedBy { it.label }
        val monthlyEquipmentPayments = FinancialRecordTable.selectAll().toList()
            .filter { it[FinancialRecordTable.projectId].value in projectIds && it[FinancialRecordTable.paymentPurpose] == "equipment" && it[FinancialRecordTable.recordType] in setOf("payment", "advance") }
            .filter { it[FinancialRecordTable.amountEurCents] != null }
            .groupBy { month(it[FinancialRecordTable.recordDate]) }
            .map { MonthlyActPayment(it.key, it.value.sumOf { row -> row[FinancialRecordTable.amountEurCents] ?: 0L }) }.sortedBy { it.month }
        // Procurement reference rows do not carry a project foreign key. Do not
        // expose their global aggregate to a manager whose dashboard is scoped.
        val procurementStatusCounts = procurementStatusMetrics(procurementRecords)
        val monthlySignedConstructionContracts = procurementRecords
            .filter { isConstructionContractSigned(it[ProcurementRecordTable.purchaseStatus]) }
            .mapNotNull { row -> (row[ProcurementRecordTable.contractDate] ?: row[ProcurementRecordTable.estimatedContractDate])?.let(::month) }
            .groupingBy { it }.eachCount().map { DashboardMetric(it.key, it.value.toLong()) }.sortedBy { it.label }
        val findings = InspectionFindingTable.selectAll().count { it[InspectionFindingTable.inspectionReportId].value in reportIds }
        // The admin activity panel filters and sorts locally, so return a useful
        // recent window without introducing a separate request for every user.
        val activities = if (allowedProjectIds == null) auditLogService.recent(limit = 100) else emptyList()
        val currentMonth = LocalDate.now()
        DashboardData(projects.size.toLong(), projects.count { it[ProjectTable.status] == "active" }.toLong(), projects.count { it[ProjectTable.status] == "completed" && it[ProjectTable.endDate]?.let { date -> date.year == currentMonth.year && date.month == currentMonth.month } == true }.toLong(), projects.sumOf { it[ProjectTable.budgetPlanned] }, spent.toLong(), reports.size.toLong(), reports.count { it[InspectionReportTable.status] == "pending_review" }.toLong(), findings.toLong(), reports.sortedByDescending { it[InspectionReportTable.inspectionDate] }.take(5).map { r -> InspectionReport(r[InspectionReportTable.id].value, UUID.fromString(r[InspectionReportTable.uuid]), r[InspectionReportTable.projectId].value, r[InspectionReportTable.reportCode], r[InspectionReportTable.inspectionType], r[InspectionReportTable.inspectionDate], r[InspectionReportTable.summary], InspectionReportStatus.valueOf(r[InspectionReportTable.status].uppercase()), r[InspectionReportTable.rejectionReason], r[InspectionReportTable.latitude]?.toDouble(), r[InspectionReportTable.longitude]?.toDouble(), r[InspectionReportTable.createdBy].value) }, activities, monthlyActPayments, subprojectFunding, subprojectProgress, procurementStatusCounts, monthlyInspectionCounts, monthlyEshsViolations, monthlyEquipmentPayments, monthlySignedConstructionContracts)
        }
    }
}

private fun org.jetbrains.exposed.v1.core.ResultRow.toFunding(
    amounts: Map<String, ProjectAmount>?
): DashboardSubprojectFunding {
    // Preserve the existing chart semantics: a construction contract is used
    // when it exists; otherwise the approved project budget is shown.
    val amount = amounts?.get("construction") ?: amounts?.get("budget")
    val legacyUah = this[ProjectTable.subprojectContractAmount] ?: this[ProjectTable.budgetPlanned]
    val uah = amount?.uah?.toDouble() ?: legacyUah.toDouble()
    // Old records without a frozen conversion remain usable in UAH.  The
    // seeded and newly saved records always have this value.
    val eur = amount?.let { if (it.currency == "EUR") it.amount else it.convertedAmount }?.toDouble() ?: 0.0
    return DashboardSubprojectFunding(
        this[ProjectTable.uuid], this[ProjectTable.name], this[ProjectTable.region].orEmpty(), uah, eur
    )
}
