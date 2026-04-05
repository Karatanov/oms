package oms.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import oms.components.FilterDropdown
import oms.localization.LocalizationManager

@Composable
fun ReportsScreen(
    onNewInspection: () -> Unit = {}
) {
    var statusFilter by remember { mutableStateOf<InspectionReportStatus?>(null) }
    var sortColumn by remember { mutableStateOf(ReportSortColumn.DATE) }
    var ascending by remember { mutableStateOf(false) }

    val reports = remember { sampleInspectionReports() }

    val filteredReports = remember(statusFilter, sortColumn, ascending) {
        val filtered = reports.filter { statusFilter == null || it.status == statusFilter }

        val sorted = when (sortColumn) {
            ReportSortColumn.ID -> filtered.sortedBy { it.id }
            ReportSortColumn.TYPE -> filtered.sortedBy { it.type }
            ReportSortColumn.INSPECTOR -> filtered.sortedBy { it.inspector }
            ReportSortColumn.DATE -> filtered.sortedBy { it.date }
            ReportSortColumn.STATUS -> filtered.sortedBy { it.status.ordinal }
        }

        if (ascending) sorted else sorted.reversed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LocalizationManager.t("reports_title"),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )

            Button(onClick = onNewInspection) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(LocalizationManager.t("new_inspection"))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterDropdown(
                label = LocalizationManager.t("status"),
                options = InspectionReportStatus.entries,
                selected = statusFilter,
                onSelect = { statusFilter = it },
                itemLabel = { status ->
                    when (status) {
                        InspectionReportStatus.DRAFT -> LocalizationManager.t("draft")
                        InspectionReportStatus.PENDING_REVIEW -> LocalizationManager.t("pending_review")
                        InspectionReportStatus.COMPLETED -> LocalizationManager.t("completed")
                    }
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InspectionTableHeader(
                    currentSort = sortColumn,
                    ascending = ascending,
                    onSort = { column ->
                        if (sortColumn == column) {
                            ascending = !ascending
                        } else {
                            sortColumn = column
                            ascending = true
                        }
                    }
                )

                HorizontalDivider()

                filteredReports.forEach { report ->
                    InspectionReportRow(
                        report = report,
                        onView = { },
                        onEdit = { }
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectionTableHeader(
    currentSort: ReportSortColumn,
    ascending: Boolean,
    onSort: (ReportSortColumn) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SortHeaderCell(
            title = LocalizationManager.t("report_id"),
            column = ReportSortColumn.ID,
            currentSort = currentSort,
            ascending = ascending,
            onSort = onSort,
            width = 120.dp
        )

        SortHeaderCell(
            title = LocalizationManager.t("report_type"),
            column = ReportSortColumn.TYPE,
            currentSort = currentSort,
            ascending = ascending,
            onSort = onSort,
            width = 160.dp
        )

        SortHeaderCell(
            title = LocalizationManager.t("inspector"),
            column = ReportSortColumn.INSPECTOR,
            currentSort = currentSort,
            ascending = ascending,
            onSort = onSort,
            width = 180.dp
        )

        SortHeaderCell(
            title = LocalizationManager.t("date"),
            column = ReportSortColumn.DATE,
            currentSort = currentSort,
            ascending = ascending,
            onSort = onSort,
            width = 120.dp
        )

        SortHeaderCell(
            title = LocalizationManager.t("status"),
            column = ReportSortColumn.STATUS,
            currentSort = currentSort,
            ascending = ascending,
            onSort = onSort,
            width = 160.dp
        )

        Box(
            modifier = Modifier.width(220.dp)
        ) {
            Text(
                text = LocalizationManager.t("actions"),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun SortHeaderCell(
    title: String,
    column: ReportSortColumn,
    currentSort: ReportSortColumn,
    ascending: Boolean,
    onSort: (ReportSortColumn) -> Unit,
    width: androidx.compose.ui.unit.Dp
) {
    TextButton(
        onClick = { onSort(column) },
        modifier = Modifier.width(width),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title)
            if (currentSort == column) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (ascending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InspectionReportRow(
    report: InspectionReportUi,
    onView: (InspectionReportUi) -> Unit,
    onEdit: (InspectionReportUi) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onView(report) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(report.id, modifier = Modifier.width(120.dp))
        Text(report.type, modifier = Modifier.width(160.dp))
        Text(report.inspector, modifier = Modifier.width(180.dp))
        Text(report.date, modifier = Modifier.width(120.dp))

        Box(modifier = Modifier.width(160.dp)) {
            InspectionStatusBadge(report.status)
        }

        Row(
            modifier = Modifier.width(220.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { onView(report) }) {
                Text(LocalizationManager.t("view"))
            }

            if (report.status == InspectionReportStatus.DRAFT) {
                TextButton(
                    onClick = { onEdit(report) },
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = LocalizationManager.t("edit"),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }

    HorizontalDivider()
}

@Composable
private fun InspectionStatusBadge(status: InspectionReportStatus) {
    val (label, color) = when (status) {
        InspectionReportStatus.DRAFT -> LocalizationManager.t("draft") to Color(0xFF9E9E9E)
        InspectionReportStatus.PENDING_REVIEW -> LocalizationManager.t("pending_review") to Color(0xFFF9A825)
        InspectionReportStatus.COMPLETED -> LocalizationManager.t("completed") to Color(0xFF2E7D32)
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private enum class ReportSortColumn {
    ID,
    TYPE,
    INSPECTOR,
    DATE,
    STATUS
}

private enum class InspectionReportStatus {
    DRAFT,
    PENDING_REVIEW,
    COMPLETED
}

private data class InspectionReportUi(
    val id: String,
    val type: String,
    val inspector: String,
    val date: String,
    val status: InspectionReportStatus
)

private fun sampleInspectionReports(): List<InspectionReportUi> = listOf(
    InspectionReportUi("SIR-104", "Site Visit", "Olena Kovalenko", "2026-03-24", InspectionReportStatus.COMPLETED),
    InspectionReportUi("SIR-103", "Safety Check", "Ihor Petrenko", "2026-03-21", InspectionReportStatus.PENDING_REVIEW),
    InspectionReportUi("SIR-102", "Progress Audit", "Anna Shevchenko", "2026-03-18", InspectionReportStatus.DRAFT),
    InspectionReportUi("SIR-101", "Quality Review", "Taras Bondar", "2026-03-10", InspectionReportStatus.COMPLETED)
)