package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oms.data.ApiInspectionReport
import oms.data.ApiProjectDocument
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.components.SortableTableHeader
import oms.components.DocumentTypeChip
import oms.components.TableActionIconButton
import oms.localization.LocalizationManager
import kotlin.js.JsName

@JsName("openProjectDocumentUpload")
external fun openProjectDocumentUpload(projectUuid: String, docType: String)

private data class SirDocumentRow(val projectName: String, val report: ApiInspectionReport)
private data class ProjectDocumentRow(val projectUuid: String, val projectName: String, val document: ApiProjectDocument)
private enum class DocumentSort { Name, Project, Type, Size, Author }
private enum class DocumentTypeFilter(val labelKey: String) {
    ALL("all"), CONTRACT("contract"), PROJECT("project_documents"), DESIGN("design"),
    ESTIMATE("estimate"), FINANCIAL("financial_doc"), PHOTO("photo"), OTHER("other");

    fun matches(type: String): Boolean = when (this) {
        ALL -> true
        CONTRACT -> type == "contract"
        PROJECT -> type == "project" || type == "subproject"
        DESIGN -> type == "design"
        ESTIMATE -> type == "estimate"
        FINANCIAL -> type == "invoice" || type == "act"
        PHOTO -> type == "photo"
        OTHER -> type == "other"
    }
}

@Composable
fun DocumentsScreen(canManageDocuments: Boolean = true) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var sirFiles by remember { mutableStateOf<List<SirDocumentRow>>(emptyList()) }
    var projectFiles by remember { mutableStateOf<List<ProjectDocumentRow>>(emptyList()) }
    var sort by remember { mutableStateOf(DocumentSort.Name) }
    var ascending by remember { mutableStateOf(true) }
    var typeFilter by remember { mutableStateOf(DocumentTypeFilter.ALL) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showUploadDialog by remember { mutableStateOf(false) }
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
    val visibleDocuments = remember(projectFiles, typeFilter, sort, ascending) {
        projectFiles
            .asSequence()
            .filter { typeFilter.matches(it.document.docType.lowercase()) }
            .sortedWith(compareBy<ProjectDocumentRow> {
                when (sort) {
                    DocumentSort.Name -> it.document.fileName
                    DocumentSort.Project -> it.projectName
                    DocumentSort.Type -> it.document.docType
                    DocumentSort.Size -> it.document.fileSizeBytes.toString().padStart(20, '0')
                    DocumentSort.Author -> "admin"
                }
            }.let { if (ascending) it else it.reversed() })
            .toList()
    }
    fun selectSort(column: DocumentSort) { if (sort == column) ascending = !ascending else { sort = column; ascending = true } }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(LocalizationManager.t("documents_title"), style = MaterialTheme.typography.headlineMedium)
            if (canManageDocuments) Button(onClick = { showUploadDialog = true }) { Text(LocalizationManager.t("upload_document")) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DocumentTypeFilter.entries, key = { it.name }) { filter ->
                FilterChip(
                    selected = typeFilter == filter,
                    onClick = { typeFilter = filter },
                    label = { Text(LocalizationManager.t(filter.labelKey)) }
                )
            }
        }
        Text(LocalizationManager.t("project_documents"), style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp).horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DocumentTableHeader(sort, ascending, ::selectSort)
                HorizontalDivider()
                if (visibleDocuments.isEmpty()) Text(LocalizationManager.t("no_project_documents"))
                visibleDocuments.forEach { row ->
                    Row(Modifier.width(1_200.dp).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(row.document.fileName, Modifier.weight(1.35f))
                        Text(row.projectName, Modifier.weight(1f))
                        Box(Modifier.width(120.dp)) { DocumentTypeChip(row.document.docType) }
                        Text(formatFileSize(row.document.fileSizeBytes), Modifier.width(90.dp))
                        Text("admin", Modifier.width(85.dp))
                        TableActionIconButton(LocalizationManager.t("open_document"), Icons.AutoMirrored.Filled.OpenInNew) { uriHandler.openUri("http://localhost:8080/api/v1/projects/${row.projectUuid}/documents/${row.document.uuid}/download") }
                        if (canManageDocuments) TableActionIconButton(LocalizationManager.t("delete"), Icons.Default.Delete) {
                            scope.launch {
                                if (OmsApiClient.deleteProjectDocument(row.projectUuid, row.document.uuid)) {
                                    projectFiles = projectFiles.filterNot { it.document.uuid == row.document.uuid }
                                } else errorMessage = "Could not delete document."
                            }
                        }
                        else Spacer(Modifier.width(48.dp))
                    }
                    HorizontalDivider()
                }
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(LocalizationManager.t("sir_source_files"), style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sirFiles.isEmpty()) Text("No imported SIR files found.")
                sirFiles.forEach { row ->
                    val fileName = row.report.summary?.removePrefix("Imported SIR: ") ?: "SIR source file"
                    Text(fileName, style = MaterialTheme.typography.titleMedium)
                    Text("${row.projectName} • ${row.report.inspectionDate}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TableActionIconButton("Open SIR source file", Icons.AutoMirrored.Filled.OpenInNew) { uriHandler.openUri("http://localhost:8080/api/v1/inspection-reports/${row.report.uuid}/source-file") }
                        if (canManageDocuments) TableActionIconButton("Delete SIR source file", Icons.Default.Delete) {
                            scope.launch {
                                if (OmsApiClient.deleteInspectionReport(row.report.uuid)) sirFiles = sirFiles.filterNot { it.report.uuid == row.report.uuid }
                                else errorMessage = "Could not delete source document."
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        if (showUploadDialog) ProjectDocumentUploadDialog(
            projects = ProjectRepository.projects,
            onDismiss = { showUploadDialog = false },
            onUpload = { projectUuid, type -> openProjectDocumentUpload(projectUuid, type); showUploadDialog = false }
        )
    }
}

@Composable
private fun ProjectDocumentUploadDialog(projects: List<oms.model.Project>, onDismiss: () -> Unit, onUpload: (String, String) -> Unit) {
    var projectUuid by remember { mutableStateOf(projects.firstOrNull()?.id) }
    var docType by remember { mutableStateOf("other") }
    var expanded by remember { mutableStateOf(false) }
    val selected = projects.firstOrNull { it.id == projectUuid }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(LocalizationManager.t("upload_document"), style = MaterialTheme.typography.titleLarge)
            Box { OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected?.name ?: LocalizationManager.t("select_project")) }
                DropdownMenu(expanded, { expanded = false }) { projects.forEach { p -> DropdownMenuItem({ Text(p.name) }, { projectUuid = p.id; expanded = false }) } } }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("contract", "project", "subproject", "design").forEach { type -> FilterChip(selected = docType == type, onClick = { docType = type }, label = { Text(type.replaceFirstChar(Char::uppercase)) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("estimate", "invoice", "act", "photo", "other").forEach { type -> FilterChip(selected = docType == type, onClick = { docType = type }, label = { Text(type.replaceFirstChar(Char::uppercase)) }) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                Button(onClick = { onUpload(projectUuid!!, docType) }, enabled = projectUuid != null) { Text(LocalizationManager.t("choose_file")) }
            }
        }
    }
}

@Composable
private fun DocumentTableHeader(sort: DocumentSort, ascending: Boolean, onSort: (DocumentSort) -> Unit) {
    Row(Modifier.width(1_200.dp).padding(vertical = 6.dp)) {
        SortableTableHeader(LocalizationManager.t("file_name"), sort == DocumentSort.Name, ascending, { onSort(DocumentSort.Name) }, Modifier.weight(1.35f))
        SortableTableHeader(LocalizationManager.t("project"), sort == DocumentSort.Project, ascending, { onSort(DocumentSort.Project) }, Modifier.weight(1f))
        SortableTableHeader(LocalizationManager.t("type"), sort == DocumentSort.Type, ascending, { onSort(DocumentSort.Type) }, Modifier.width(120.dp))
        SortableTableHeader(LocalizationManager.t("size"), sort == DocumentSort.Size, ascending, { onSort(DocumentSort.Size) }, Modifier.width(90.dp))
        SortableTableHeader(LocalizationManager.t("uploaded_by"), sort == DocumentSort.Author, ascending, { onSort(DocumentSort.Author) }, Modifier.width(85.dp))
        Spacer(Modifier.width(96.dp))
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
