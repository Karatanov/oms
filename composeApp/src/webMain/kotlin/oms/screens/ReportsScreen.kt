package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.data.ApiInspectionReport
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.localization.LocalizationManager

private data class ReportRow(val projectName: String, val report: ApiInspectionReport)

@Composable
fun ReportsScreen(onNewInspection: () -> Unit = {}) {
    var reports by remember { mutableStateOf<List<ReportRow>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        ProjectRepository.refresh()
        reports = ProjectRepository.projects.flatMap { project ->
            runCatching { OmsApiClient.projectReports(project.id) }.getOrDefault(emptyList())
                .map { ReportRow(project.name, it) }
        }.sortedByDescending { it.report.inspectionDate }
    }
    val visible = reports.filter { status == null || it.report.status == status }

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
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (visible.isEmpty()) Text("No inspection reports found.")
                visible.forEach { row ->
                    Column {
                        Text(row.report.summary ?: "Inspection report", style = MaterialTheme.typography.titleMedium)
                        Text("${row.projectName} • ${row.report.inspectionDate} • ${row.report.completionPct}% • ${row.report.status}")
                        Text(row.report.uuid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
