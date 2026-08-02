package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import oms.data.ApiInspectionReport
import oms.data.ApiProjectDocument
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.components.SortableTableHeader
import oms.localization.LocalizationManager

private data class SirDocumentRow(val projectName: String, val report: ApiInspectionReport)
private data class ProjectDocumentRow(val projectUuid: String, val projectName: String, val document: ApiProjectDocument)
private enum class DocumentSort { Name, Project, Type, Size, Author }

@Composable
fun DocumentsScreen(canManageDocuments: Boolean = true) {
    val uriHandler = LocalUriHandler.current
    var sirFiles by remember { mutableStateOf<List<SirDocumentRow>>(emptyList()) }
    var projectFiles by remember { mutableStateOf<List<ProjectDocumentRow>>(emptyList()) }
    var sort by remember { mutableStateOf(DocumentSort.Name) }
    var ascending by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        ProjectRepository.refresh()
        sirFiles = ProjectRepository.projects.flatMap { project ->
            runCatching { OmsApiClient.projectReports(project.id) }.getOrDefault(emptyList())
                .filter { it.summary?.startsWith("Imported SIR:") == true }
                .map { SirDocumentRow(project.name, it) }
        }.sortedByDescending { it.report.inspectionDate }
        projectFiles = ProjectRepository.projects.flatMap { project ->
            runCatching { OmsApiClient.projectDocuments(project.id) }.getOrDefault(emptyList())
                .map { ProjectDocumentRow(project.id, project.name, it) }
        }
    }
    val visibleDocuments = projectFiles.sortedWith(compareBy<ProjectDocumentRow> {
        when (sort) {
            DocumentSort.Name -> it.document.fileName
            DocumentSort.Project -> it.projectName
            DocumentSort.Type -> it.document.docType
            DocumentSort.Size -> it.document.fileSizeBytes.toString().padStart(20, '0')
            DocumentSort.Author -> "admin"
        }
    }.let { if (ascending) it else it.reversed() })
    fun selectSort(column: DocumentSort) { if (sort == column) ascending = !ascending else { sort = column; ascending = true } }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(LocalizationManager.t("documents_title"), style = MaterialTheme.typography.headlineMedium)
        Text("Project documents", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DocumentTableHeader(sort, ascending, ::selectSort)
                HorizontalDivider()
                if (visibleDocuments.isEmpty()) Text("No project documents found.")
                visibleDocuments.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(row.document.fileName, Modifier.weight(1.35f))
                        Text(row.projectName, Modifier.weight(1f))
                        Text(row.document.docType, Modifier.width(85.dp))
                        Text(formatFileSize(row.document.fileSizeBytes), Modifier.width(90.dp))
                        Text("admin", Modifier.width(85.dp))
                        TextButton(onClick = { uriHandler.openUri("http://localhost:8080/api/v1/projects/${row.projectUuid}/documents/${row.document.uuid}/download") }, modifier = Modifier.width(85.dp)) { Text("Open") }
                    }
                    HorizontalDivider()
                }
            }
        }
        Text("SIR source files", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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

@Composable
private fun DocumentTableHeader(sort: DocumentSort, ascending: Boolean, onSort: (DocumentSort) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SortableTableHeader("File name", sort == DocumentSort.Name, ascending, { onSort(DocumentSort.Name) }, Modifier.weight(1.35f))
        SortableTableHeader("Project", sort == DocumentSort.Project, ascending, { onSort(DocumentSort.Project) }, Modifier.weight(1f))
        SortableTableHeader("Type", sort == DocumentSort.Type, ascending, { onSort(DocumentSort.Type) }, Modifier.width(85.dp))
        SortableTableHeader("Size", sort == DocumentSort.Size, ascending, { onSort(DocumentSort.Size) }, Modifier.width(90.dp))
        SortableTableHeader("Uploaded by", sort == DocumentSort.Author, ascending, { onSort(DocumentSort.Author) }, Modifier.width(85.dp))
        Spacer(Modifier.width(85.dp))
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
