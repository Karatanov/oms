package oms.ufsi.service

import oms.ufsi.database.tables.*
import oms.ufsi.domain.InspectionReport
import oms.ufsi.domain.InspectionReportStatus
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.time.LocalDate

data class MonthlyActPayment(val month: String, val amountEurCents: Long)
data class DashboardSubprojectFunding(val projectUuid: String, val name: String, val region: String, val amount: Long)
data class DashboardSubprojectProgress(val projectUuid: String, val code: String, val name: String, val region: String, val completionPct: Double)
data class DashboardMetric(val label: String, val value: Long)
data class DashboardOverviewData(
    val recentInspections: List<InspectionReport>,
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

private fun isConstructionContractSigned(status: String) =
    status.contains("Договір укладено", ignoreCase = true) || status.contains("Contract signed", ignoreCase = true)
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
    fun getOverview(allowedProjectIds: Set<Long>? = null): DashboardOverviewData = transaction {
        val projects = ProjectTable.selectAll().toList()
            .filter { allowedProjectIds == null || it[ProjectTable.id].value in allowedProjectIds }
        val projectIds = projects.map { it[ProjectTable.id].value }.toSet()
        val reports = InspectionReportTable.selectAll().toList()
            .filter { it[InspectionReportTable.projectId].value in projectIds }
        val actRecords = FinancialRecordTable.selectAll().toList().filter {
            it[FinancialRecordTable.projectId].value in projectIds && it[FinancialRecordTable.recordType] == "act"
        }
        fun month(date: java.time.LocalDate) = date.toString().take(7)
        val monthlyActPayments = actRecords.filter { it[FinancialRecordTable.amountEurCents] != null }
            .groupBy { month(it[FinancialRecordTable.paymentDate] ?: it[FinancialRecordTable.recordDate]) }
            .map { (label, rows) -> MonthlyActPayment(label, rows.sumOf { it[FinancialRecordTable.amountEurCents] ?: 0L }) }
            .sortedBy { it.month }
        val projectsById = projects.associateBy { it[ProjectTable.id].value }
        val subprojects = projects.filter { it[ProjectTable.projectType] == "subproject" }
        val subprojectFunding = subprojects.map { row ->
            DashboardSubprojectFunding(
                row[ProjectTable.uuid], row[ProjectTable.name], row[ProjectTable.region].orEmpty(),
                row[ProjectTable.subprojectContractAmount] ?: row[ProjectTable.budgetPlanned]
            )
        }
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
            .mapValues { (_, rows) -> rows.sumOf { it[FinancialRecordTable.amount] } }
        val procurementRecords = if (allowedProjectIds == null) ProcurementRecordTable.selectAll().toList() else emptyList()
        val signedSubprojectCodes = procurementRecords
            .filter { isConstructionContractSigned(it[ProcurementRecordTable.purchaseStatus]) }
            .map { it[ProcurementRecordTable.subProjectId].trim().lowercase() }
            .filter(String::isNotBlank)
            .toSet()
        val subprojectProgress = subprojects.filter { row ->
            val code = row[ProjectTable.siteNumber].orEmpty().trim().lowercase()
            code in signedSubprojectCodes || row[ProjectTable.uuid].lowercase() in signedSubprojectCodes
        }.map { row ->
            val id = row[ProjectTable.id].value
            val contract = row[ProjectTable.subprojectContractAmount] ?: row[ProjectTable.budgetPlanned]
            DashboardSubprojectProgress(
                row[ProjectTable.uuid], row[ProjectTable.siteNumber].orEmpty(), row[ProjectTable.name], row[ProjectTable.region].orEmpty(),
                if (contract > 0) (actualBySubproject[id] ?: 0L) * 100.0 / contract else 0.0
            )
        }.sortedBy { it.name }
        val procurementCountByStatus = procurementRecords.groupBy { it[ProcurementRecordTable.purchaseStatus] }
            .mapValues { (_, rows) -> rows.map { it[ProcurementRecordTable.subProjectId] }.distinct().size.toLong() }
        val procurementStatusCounts = procurementStatusOrder.map { status -> DashboardMetric(status, procurementCountByStatus[status] ?: 0L) } +
            procurementCountByStatus.filterKeys { it !in procurementStatusOrder }.toSortedMap()
                .map { (status, count) -> DashboardMetric(status, count) }
        fun report(row: org.jetbrains.exposed.v1.core.ResultRow) = InspectionReport(
            row[InspectionReportTable.id].value, UUID.fromString(row[InspectionReportTable.uuid]), row[InspectionReportTable.projectId].value,
            row[InspectionReportTable.reportCode], row[InspectionReportTable.inspectionType], row[InspectionReportTable.inspectionDate],
            row[InspectionReportTable.summary], InspectionReportStatus.valueOf(row[InspectionReportTable.status].uppercase()),
            row[InspectionReportTable.rejectionReason], row[InspectionReportTable.latitude]?.toDouble(), row[InspectionReportTable.longitude]?.toDouble(), row[InspectionReportTable.createdBy].value
        )
        DashboardOverviewData(
            reports.sortedByDescending { it[InspectionReportTable.inspectionDate] }.take(5).map(::report),
            monthlyActPayments, subprojectFunding, subprojectProgress, procurementStatusCounts
        )
    }

    fun get(allowedProjectIds: Set<Long>? = null): DashboardData {
        return transaction {
        val projects = ProjectTable.selectAll().toList().filter { allowedProjectIds == null || it[ProjectTable.id].value in allowedProjectIds }
        val projectIds = projects.map { it[ProjectTable.id].value }.toSet()
        val reports = InspectionReportTable.selectAll().toList().filter { it[InspectionReportTable.projectId].value in projectIds }
        val reportIds = reports.map { it[InspectionReportTable.id].value }.toSet()
        val actRecords = FinancialRecordTable.selectAll().filter {
            it[FinancialRecordTable.projectId].value in projectIds && it[FinancialRecordTable.recordType] == "act"
        }
        val spent = actRecords.sumOf { it[FinancialRecordTable.amount] }
        val monthlyActPayments = actRecords.filter { it[FinancialRecordTable.amountEurCents] != null }
            .groupBy { (it[FinancialRecordTable.paymentDate] ?: it[FinancialRecordTable.recordDate]).toString().take(7) }
            .map { (month, records) -> MonthlyActPayment(month, records.sumOf { it[FinancialRecordTable.amountEurCents] ?: 0L }) }
            .sortedBy { it.month }
        val projectsById = projects.associateBy { it[ProjectTable.id].value }
        val subprojects = projects.filter { it[ProjectTable.projectType] == "subproject" }
        val subprojectFunding = subprojects.map { row ->
            DashboardSubprojectFunding(
                projectUuid = row[ProjectTable.uuid], name = row[ProjectTable.name], region = row[ProjectTable.region].orEmpty(),
                amount = row[ProjectTable.subprojectContractAmount] ?: row[ProjectTable.budgetPlanned]
            )
        }
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
            .mapValues { (_, records) -> records.sumOf { it[FinancialRecordTable.amount] } }
        val procurementRecords = if (allowedProjectIds == null) ProcurementRecordTable.selectAll().toList() else emptyList()
        val signedSubprojectCodes = procurementRecords
            .filter { isConstructionContractSigned(it[ProcurementRecordTable.purchaseStatus]) }
            .map { it[ProcurementRecordTable.subProjectId].trim().lowercase() }
            .filter(String::isNotBlank)
            .toSet()
        val subprojectProgress = subprojects.filter { row ->
            val code = row[ProjectTable.siteNumber].orEmpty().trim().lowercase()
            code in signedSubprojectCodes || row[ProjectTable.uuid].lowercase() in signedSubprojectCodes
        }.map { row ->
            val id = row[ProjectTable.id].value
            val contract = row[ProjectTable.subprojectContractAmount] ?: row[ProjectTable.budgetPlanned]
            DashboardSubprojectProgress(row[ProjectTable.uuid], row[ProjectTable.siteNumber].orEmpty(), row[ProjectTable.name], row[ProjectTable.region].orEmpty(), if (contract > 0) (actualBySubproject[id] ?: 0L) * 100.0 / contract else 0.0)
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
            .groupBy { month(it[FinancialRecordTable.paymentDate] ?: it[FinancialRecordTable.recordDate]) }
            .map { MonthlyActPayment(it.key, it.value.sumOf { row -> row[FinancialRecordTable.amountEurCents] ?: 0L }) }.sortedBy { it.month }
        // Procurement reference rows do not carry a project foreign key. Do not
        // expose their global aggregate to a manager whose dashboard is scoped.
        val procurementCountByStatus = procurementRecords.groupBy { it[ProcurementRecordTable.purchaseStatus] }
            .mapValues { (_, rows) -> rows.map { it[ProcurementRecordTable.subProjectId] }.distinct().size.toLong() }
        val procurementStatusCounts = procurementStatusOrder.map { status ->
            DashboardMetric(status, procurementCountByStatus[status] ?: 0L)
        } + procurementCountByStatus
            .filterKeys { it !in procurementStatusOrder }
            .toSortedMap()
            .map { (status, count) -> DashboardMetric(status, count) }
        val monthlySignedConstructionContracts = procurementRecords
            .filter { isConstructionContractSigned(it[ProcurementRecordTable.purchaseStatus]) }
            .mapNotNull { row -> row[ProcurementRecordTable.contractDate]?.let { date -> month(date) } }
            .groupingBy { it }.eachCount().map { DashboardMetric(it.key, it.value.toLong()) }.sortedBy { it.label }
        val findings = InspectionFindingTable.selectAll().count { it[InspectionFindingTable.inspectionReportId].value in reportIds }
        // The admin activity panel filters and sorts locally, so return a useful
        // recent window without introducing a separate request for every user.
        val activities = if (allowedProjectIds == null) auditLogService.recent(limit = 100) else emptyList()
        val currentMonth = LocalDate.now()
        DashboardData(projects.size.toLong(), projects.count { it[ProjectTable.status] == "active" }.toLong(), projects.count { it[ProjectTable.status] == "completed" && it[ProjectTable.endDate]?.let { date -> date.year == currentMonth.year && date.month == currentMonth.month } == true }.toLong(), projects.sumOf { it[ProjectTable.budgetPlanned] }, spent, reports.size.toLong(), reports.count { it[InspectionReportTable.status] == "pending_review" }.toLong(), findings.toLong(), reports.sortedByDescending { it[InspectionReportTable.inspectionDate] }.take(5).map { r -> InspectionReport(r[InspectionReportTable.id].value, UUID.fromString(r[InspectionReportTable.uuid]), r[InspectionReportTable.projectId].value, r[InspectionReportTable.reportCode], r[InspectionReportTable.inspectionType], r[InspectionReportTable.inspectionDate], r[InspectionReportTable.summary], InspectionReportStatus.valueOf(r[InspectionReportTable.status].uppercase()), r[InspectionReportTable.rejectionReason], r[InspectionReportTable.latitude]?.toDouble(), r[InspectionReportTable.longitude]?.toDouble(), r[InspectionReportTable.createdBy].value) }, activities, monthlyActPayments, subprojectFunding, subprojectProgress, procurementStatusCounts, monthlyInspectionCounts, monthlyEshsViolations, monthlyEquipmentPayments, monthlySignedConstructionContracts)
        }
    }
}
