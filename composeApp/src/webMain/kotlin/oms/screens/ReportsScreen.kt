package oms.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import oms.components.FilterDropdown

@Composable
fun ReportsScreen(
    onNewInspection: () -> Unit = {}
) {

    var statusFilter by remember { mutableStateOf<InspectionReportStatus?>(null) }
    var sortDescending by remember { mutableStateOf(true) }

    val reports = remember {
        sampleInspectionReports()
    }

    val filteredReports = remember(statusFilter, sortDescending) {
        reports
            .filter { statusFilter == null || it.status == statusFilter }
            .let { list ->
                if (sortDescending) list.sortedByDescending { it.date } else list.sortedBy { it.date }
            }
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
                text = "Inspection Reports",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )

            Button(onClick = onNewInspection) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New Inspection")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterDropdown(
                label = "Status",
                options = InspectionReportStatus.entries,
                selected = statusFilter,
                onSelect = { statusFilter = it }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InspectionTableHeader(
                    sortDescending = sortDescending,
                    onSortDate = { sortDescending = !sortDescending }
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
    sortDescending: Boolean,
    onSortDate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Report ID", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.labelLarge)
        Text("Type", modifier = Modifier.width(160.dp), style = MaterialTheme.typography.labelLarge)
        Text("Inspector", modifier = Modifier.width(160.dp), style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = onSortDate, modifier = Modifier.width(120.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Date")
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (sortDescending) Icons.Default.Visibility else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text("Status", modifier = Modifier.width(160.dp), style = MaterialTheme.typography.labelLarge)
        Box(modifier = Modifier.width(160.dp)) {
            Text("Actions", style = MaterialTheme.typography.labelLarge)
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
        Text(report.inspector, modifier = Modifier.width(160.dp))
        Text(report.date, modifier = Modifier.width(120.dp))
        InspectionStatusBadge(report.status)
        Row(
            modifier = Modifier.width(160.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { onView(report) }) { Text("View") }
            if (report.status == InspectionReportStatus.DRAFT) {
                TextButton(onClick = { onEdit(report) }) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun InspectionStatusBadge(status: InspectionReportStatus) {
    val (label, color) = when (status) {
        InspectionReportStatus.DRAFT -> "Draft" to Color(0xFF9E9E9E)
        InspectionReportStatus.PENDING_REVIEW -> "Pending Review" to Color(0xFFF9A825)
        InspectionReportStatus.COMPLETED -> "Completed" to Color(0xFF2E7D32)
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.width(160.dp)
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
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