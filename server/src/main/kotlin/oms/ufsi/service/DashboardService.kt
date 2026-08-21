package oms.ufsi.service

import oms.ufsi.database.tables.*
import oms.ufsi.domain.InspectionReport
import oms.ufsi.domain.InspectionReportStatus
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.time.LocalDate

data class MonthlyActPayment(val month: String, val amount: Long)
data class DashboardData(val projectsTotal:Long,val projectsActive:Long,val projectsCompletedThisMonth:Long,val budgetPlanned:Long,val amountSpent:Long,val inspectionsTotal:Long,val pendingInspections:Long,val findingsTotal:Long,val recentInspections:List<InspectionReport>,val activities:List<ActivityEntry>,val monthlyActPayments:List<MonthlyActPayment>)

class DashboardService(private val auditLogService: AuditLogService) {
    fun get(allowedProjectIds: Set<Long>? = null) = transaction {
        val projects = ProjectTable.selectAll().toList().filter { allowedProjectIds == null || it[ProjectTable.id].value in allowedProjectIds }
        val projectIds = projects.map { it[ProjectTable.id].value }.toSet()
        val reports = InspectionReportTable.selectAll().toList().filter { it[InspectionReportTable.projectId].value in projectIds }
        val reportIds = reports.map { it[InspectionReportTable.id].value }.toSet()
        val actRecords = FinancialRecordTable.selectAll().filter {
            it[FinancialRecordTable.projectId].value in projectIds && it[FinancialRecordTable.recordType] == "act"
        }
        val spent = actRecords.sumOf { it[FinancialRecordTable.amount] }
        val monthlyActPayments = actRecords
            .groupBy { (it[FinancialRecordTable.paymentDate] ?: it[FinancialRecordTable.recordDate]).toString().take(7) }
            .map { (month, records) -> MonthlyActPayment(month, records.sumOf { it[FinancialRecordTable.amount] }) }
            .sortedBy { it.month }
        val findings = InspectionFindingTable.selectAll().count { it[InspectionFindingTable.inspectionReportId].value in reportIds }
        val activities = if (allowedProjectIds == null) auditLogService.recent() else emptyList()
        val currentMonth = LocalDate.now()
        DashboardData(projects.size.toLong(), projects.count { it[ProjectTable.status] == "active" }.toLong(), projects.count { it[ProjectTable.status] == "completed" && it[ProjectTable.endDate]?.let { date -> date.year == currentMonth.year && date.month == currentMonth.month } == true }.toLong(), projects.sumOf { it[ProjectTable.budgetPlanned] }, spent, reports.size.toLong(), reports.count { it[InspectionReportTable.status] == "pending_review" }.toLong(), findings.toLong(), reports.sortedByDescending { it[InspectionReportTable.inspectionDate] }.take(5).map { r -> InspectionReport(r[InspectionReportTable.id].value, UUID.fromString(r[InspectionReportTable.uuid]), r[InspectionReportTable.projectId].value, r[InspectionReportTable.inspectionDate], r[InspectionReportTable.summary], InspectionReportStatus.valueOf(r[InspectionReportTable.status].uppercase()), r[InspectionReportTable.rejectionReason], r[InspectionReportTable.createdBy].value) }, activities, monthlyActPayments)
    }
}
