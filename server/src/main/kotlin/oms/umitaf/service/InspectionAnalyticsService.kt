package oms.umitaf.service

import oms.umitaf.database.tables.InspectionFindingTable
import oms.umitaf.database.tables.InspectionReportTable
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

data class InspectionAnalyticsMetric(val label: String, val value: Long)

data class InspectionAnalyticsData(
    val monthlyInspectionCounts: List<InspectionAnalyticsMetric>,
    val monthlyEshsViolations: List<InspectionAnalyticsMetric>
)

/**
 * Lightweight analytics used by the inspection registry.
 *
 * It deliberately avoids the Dashboard service: inspection registry loading must
 * not read financial records, procurement, audit logs or exchange-rate data.
 */
class InspectionAnalyticsService {
    fun get(allowedProjectIds: Set<Long>? = null): InspectionAnalyticsData = transaction {
        val reports = when {
            allowedProjectIds == null -> InspectionReportTable.selectAll().toList()
            allowedProjectIds.isEmpty() -> emptyList()
            else -> InspectionReportTable.selectAll()
                .where { InspectionReportTable.projectId inList allowedProjectIds.toList() }
                .toList()
        }
        val reportIds = reports.map { it[InspectionReportTable.id].value }
        fun month(date: java.time.LocalDate) = date.toString().take(7)
        val reportMonthById = reports.associate { it[InspectionReportTable.id].value to month(it[InspectionReportTable.inspectionDate]) }

        val monthlyInspectionCounts = reports
            .groupBy { month(it[InspectionReportTable.inspectionDate]) }
            .map { (label, rows) -> InspectionAnalyticsMetric(label, rows.size.toLong()) }
            .sortedBy { it.label }

        val monthlyEshsViolations = if (reportIds.isEmpty()) emptyList() else {
            InspectionFindingTable.selectAll()
                .where { InspectionFindingTable.inspectionReportId inList reportIds }
                .toList()
                .asSequence()
                .filter { finding ->
                    val category = finding[InspectionFindingTable.category].lowercase()
                    category.contains("hse") || category.contains("eshs") || category.contains("health") ||
                        category.contains("safety") || category.contains("environment") || category.contains("social")
                }
                .mapNotNull { finding -> reportMonthById[finding[InspectionFindingTable.inspectionReportId].value] }
                .groupingBy { it }
                .eachCount()
                .map { (label, count) -> InspectionAnalyticsMetric(label, count.toLong()) }
                .sortedBy { it.label }
        }

        InspectionAnalyticsData(monthlyInspectionCounts, monthlyEshsViolations)
    }
}
