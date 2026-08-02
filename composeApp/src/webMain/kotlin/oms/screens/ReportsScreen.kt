package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oms.data.ApiInspectionReport
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.components.SortableTableHeader
import oms.localization.LocalizationManager

private data class ReportRow(val projectUuid: String, val projectName: String, val report: ApiInspectionReport)
private enum class ReportSort { Date, Report, Progress, Status, Author }

@Composable
fun ReportsScreen(onNewInspection: () -> Unit = {}) {
    var reports by remember { mutableStateOf<List<ReportRow>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var sort by remember { mutableStateOf(ReportSort.Date) }
    var ascending by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        ProjectRepository.refresh()
        reports = ProjectRepository.projects.flatMap { project ->
            runCatching { OmsApiClient.projectReports(project.id) }.getOrDefault(emptyList())
                .map { ReportRow(project.id, project.name, it) }
        }.sortedByDescending { it.report.inspectionDate }
    }
    val visible = reports.filter { status == null || it.report.status == status }.sortedWith(
        compareBy<ReportRow> {
            when (sort) {
                ReportSort.Date -> it.report.inspectionDate
                ReportSort.Report -> "${it.report.summary.orEmpty()} ${it.projectName}"
                ReportSort.Progress -> it.report.completionPct.toString().padStart(6, '0')
                ReportSort.Status -> it.report.status
                ReportSort.Author -> "admin"
            }
        }.let { if (ascending) it else it.reversed() }
    )
    fun selectSort(column: ReportSort) { if (sort == column) ascending = !ascending else { sort = column; ascending = true } }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(LocalizationManager.t("reports_title"), style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onNewInspection) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(LocalizationManager.t("new_inspection")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null, "draft", "pending_review", "completed").forEach { value ->
                FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value ?: LocalizationManager.t("all")) })
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportTableHeader(sort, ascending, ::selectSort)
                HorizontalDivider()
                if (visible.isEmpty()) Text("No inspection reports found.")
                visible.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(row.report.inspectionDate, Modifier.width(105.dp))
                        Column(Modifier.weight(1.35f)) {
                            Text(row.report.summary ?: "Inspection report", style = MaterialTheme.typography.bodyMedium)
                            Text(row.projectName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${row.report.completionPct}%", Modifier.width(85.dp))
                        Text(row.report.status.replace('_', ' '), Modifier.width(130.dp))
                        Text("admin", Modifier.width(100.dp))
                        Text(row.report.uuid.take(8), Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            scope.launch {
                                if (OmsApiClient.deleteInspectionReport(row.report.uuid)) reports = reports.filterNot { it.report.uuid == row.report.uuid }
                                else errorMessage = "Could not delete report."
                            }
                        }) { Text("Delete") }
                    }
                    HorizontalDivider()
                }
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ReportTableHeader(sort: ReportSort, ascending: Boolean, onSort: (ReportSort) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SortableTableHeader("Date", sort == ReportSort.Date, ascending, { onSort(ReportSort.Date) }, Modifier.width(105.dp))
        SortableTableHeader("Report / project", sort == ReportSort.Report, ascending, { onSort(ReportSort.Report) }, Modifier.weight(1.35f))
        SortableTableHeader("Progress", sort == ReportSort.Progress, ascending, { onSort(ReportSort.Progress) }, Modifier.width(85.dp))
        SortableTableHeader("Status", sort == ReportSort.Status, ascending, { onSort(ReportSort.Status) }, Modifier.width(130.dp))
        SortableTableHeader("Uploaded by", sort == ReportSort.Author, ascending, { onSort(ReportSort.Author) }, Modifier.width(100.dp))
        Text("ID", Modifier.width(80.dp), style = MaterialTheme.typography.labelLarge)
    }
}
