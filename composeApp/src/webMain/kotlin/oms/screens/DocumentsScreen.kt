package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import oms.data.ApiInspectionReport
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.localization.LocalizationManager

private data class SirDocumentRow(val projectName: String, val report: ApiInspectionReport)

@Composable
fun DocumentsScreen(canManageDocuments: Boolean = true) {
    val uriHandler = LocalUriHandler.current
    var sirFiles by remember { mutableStateOf<List<SirDocumentRow>>(emptyList()) }
    LaunchedEffect(Unit) {
        ProjectRepository.refresh()
        sirFiles = ProjectRepository.projects.flatMap { project ->
            runCatching { OmsApiClient.projectReports(project.id) }.getOrDefault(emptyList())
                .filter { it.summary?.startsWith("Imported SIR:") == true }
                .map { SirDocumentRow(project.name, it) }
        }.sortedByDescending { it.report.inspectionDate }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(LocalizationManager.t("documents_title"), style = MaterialTheme.typography.headlineMedium)
        Text("SIR source files", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sirFiles.isEmpty()) Text("No imported SIR files found.")
                sirFiles.forEach { row ->
                    val fileName = row.report.summary?.removePrefix("Imported SIR: ") ?: "SIR source file"
                    Text(fileName, style = MaterialTheme.typography.titleMedium)
                    Text("${row.projectName} • ${row.report.inspectionDate}")
                    Button(onClick = { uriHandler.openUri("http://localhost:8080/api/v1/inspection-reports/${row.report.uuid}/source-file") }) {
                        Text("Open SIR source file")
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
