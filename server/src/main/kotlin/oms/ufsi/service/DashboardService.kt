package oms.ufsi.service

import oms.ufsi.database.tables.*
import oms.ufsi.domain.InspectionReport
import oms.ufsi.domain.InspectionReportStatus
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

data class DashboardData(val projectsTotal:Long,val projectsActive:Long,val budgetPlanned:Long,val amountSpent:Long,val inspectionsTotal:Long,val findingsTotal:Long,val recentInspections:List<InspectionReport>)

class DashboardService {
    fun get() = transaction {
        val projects=ProjectTable.selectAll().toList()
        val reports=InspectionReportTable.selectAll().toList()
        val spent=FinancialRecordTable.selectAll().filter { it[FinancialRecordTable.recordType] in setOf("act","payment") }.sumOf { it[FinancialRecordTable.amount] }
        DashboardData(projects.size.toLong(),projects.count { it[ProjectTable.status]=="active" }.toLong(),projects.sumOf { it[ProjectTable.budgetPlanned] },spent,reports.size.toLong(),InspectionFindingTable.selectAll().count(),reports.sortedByDescending { it[InspectionReportTable.inspectionDate] }.take(5).map { r-> InspectionReport(r[InspectionReportTable.id].value,UUID.fromString(r[InspectionReportTable.uuid]),r[InspectionReportTable.projectId].value,r[InspectionReportTable.inspectionDate],r[InspectionReportTable.completionPct].toDouble(),r[InspectionReportTable.summary],InspectionReportStatus.valueOf(r[InspectionReportTable.status].uppercase()),r[InspectionReportTable.rejectionReason],r[InspectionReportTable.createdBy].value) })
    }
}
