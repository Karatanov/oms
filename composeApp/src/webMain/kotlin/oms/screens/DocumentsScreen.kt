package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.components.SortableTableHeader
import oms.components.DocumentTypeChip
import oms.components.TableActionIconButton
import oms.components.ExpandableTableText
import oms.components.InlineOptionPicker
import oms.components.SearchableOptionPicker
import oms.components.WasmSafeOverlay
import oms.localization.LocalizationManager
import oms.model.localizedName
import kotlin.js.JsName

@JsName("openProjectDocumentUpload")
external fun openProjectDocumentUpload(projectUuid: String, docType: String, onComplete: (String) -> Unit)

private data class DocumentRow(
    val projectUuid: String,
    val projectName: String,
    val subprojectPartCode: String?,
    val fileName: String,
    val documentType: String,
    val date: String?,
    val fileSizeBytes: Long?,
    val uuid: String,
    val isSirSource: Boolean = false
)
private enum class DocumentSort { Name, Project, SubprojectPartCode, Type, Date, Size, Author }
private enum class DocumentTypeFilter(val labelKey: String) {
    ALL("all"), CONTRACT("contract"), DESIGN("design"),
    ESTIMATE("estimate"), FINANCIAL("financial_doc"), PHOTO("photo"), SIR("sir_source_files"), OTHER("other");

    fun matches(type: String, isSirSource: Boolean): Boolean = when (this) {
        ALL -> true
        SIR -> isSirSource
        CONTRACT -> type == "contract"
        DESIGN -> type == "design"
        ESTIMATE -> type == "estimate"
        FINANCIAL -> type == "invoice" || type == "act"
        PHOTO -> type == "photo"
        OTHER -> !isSirSource && type == "other"
    }
}

@Composable
fun DocumentsScreen(canManageDocuments: Boolean = true) {
    val deletion = oms.components.LocalDeleteConfirmation.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var documents by remember { mutableStateOf<List<DocumentRow>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(DocumentSort.Name) }
    var ascending by remember { mutableStateOf(true) }
    var typeFilter by remember { mutableStateOf(DocumentTypeFilter.ALL) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showUploadDialog by remember { mutableStateOf(false) }
    val pageScrollState = rememberScrollState()
    var reloadKey by remember { mutableStateOf(0) }
    var pageSize by remember { mutableStateOf(20) }
    var currentPage by remember { mutableStateOf(0) }
    LaunchedEffect(reloadKey) {
        loading = true; loadFailed = false
        try {
        val (reports, loadedDocuments) = coroutineScope {
            val loadReports = async { OmsApiClient.inspectionReports() }
            val loadDocuments = async { OmsApiClient.allProjectDocuments() }
            loadReports.await() to loadDocuments.await()
        }
        // Project data is used only to decorate actual document/report rows.
        // An empty Documents register must not wait for the project hierarchy.
        if (reports.isNotEmpty() || loadedDocuments.isNotEmpty()) ProjectRepository.refresh()
        val projectsById = ProjectRepository.projects.associateBy { it.id }
        fun projectContext(project: oms.model.Project): Pair<String, String?> {
            val ancestry = generateSequence(project) { current -> current.parentProjectUuid?.let(projectsById::get) }.toList().asReversed()
            val subproject = ancestry.firstOrNull { it.projectType == "subproject" }
            val partCode = ancestry.firstOrNull { it.projectType == "subproject_part" }?.siteNumber
            return (subproject?.localizedName() ?: project.localizedName()) to partCode
        }
        val projectDocumentRows = loadedDocuments.mapNotNull { item ->
            projectsById[item.projectUuid]?.let { project ->
                val (projectName, partCode) = projectContext(project)
                DocumentRow(project.id, projectName, partCode, item.document.fileName, item.document.docType, null, item.document.fileSizeBytes, item.document.uuid)
            }
        }
        val sirDocumentRows = reports.mapNotNull { item ->
            projectsById[item.projectUuid]
                ?.takeIf { item.report.summary?.startsWith("Imported SIR:") == true }
                ?.let { project ->
                    val (projectName, partCode) = projectContext(project)
                    DocumentRow(
                        projectUuid = project.id,
                        projectName = projectName,
                        subprojectPartCode = partCode,
                        fileName = item.report.summary?.removePrefix("Imported SIR: ") ?: LocalizationManager.t("source_file"),
                        documentType = "sir_source",
                        date = item.report.inspectionDate,
                        fileSizeBytes = null,
                        uuid = item.report.uuid,
                        isSirSource = true
                    )
                }
        }
        documents = projectDocumentRows + sirDocumentRows
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            loadFailed = true
        } finally { loading = false }
    }
    val visibleDocuments = remember(documents, search, typeFilter, sort, ascending) {
        documents
            .asSequence()
            .filter { typeFilter.matches(it.documentType.lowercase(), it.isSirSource) && (search.isBlank() || it.fileName.contains(search, true) || it.projectName.contains(search, true)) }
            .sortedWith(compareBy<DocumentRow> {
                when (sort) {
                    DocumentSort.Name -> it.fileName
                    DocumentSort.Project -> it.projectName
                    DocumentSort.SubprojectPartCode -> it.subprojectPartCode.orEmpty()
                    DocumentSort.Type -> it.documentType
                    DocumentSort.Date -> it.date.orEmpty()
                    DocumentSort.Size -> it.fileSizeBytes?.toString()?.padStart(20, '0').orEmpty()
                    DocumentSort.Author -> ""
                }
            }.let { if (ascending) it else it.reversed() })
            .toList()
    }
    LaunchedEffect(search, typeFilter, pageSize) { currentPage = 0 }
    val pageCount = ((visibleDocuments.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    if (currentPage >= pageCount) currentPage = pageCount - 1
    val pageDocuments = visibleDocuments.drop(currentPage * pageSize).take(pageSize)
    fun selectSort(column: DocumentSort) { if (sort == column) ascending = !ascending else { sort = column; ascending = true } }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(pageScrollState).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        oms.components.PageHeading(LocalizationManager.t("documents_title"), Icons.Default.FolderOpen) {
            if (canManageDocuments) Button(onClick = {
                showUploadDialog = true
            }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(LocalizationManager.t("upload_document"))
            }
        }
        OutlinedTextField(search, { search = it }, singleLine = true, label = { Text(LocalizationManager.t("documents_search")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
        if (loading) oms.components.ContentState(LocalizationManager.t("loading_records"), loading = true)
        if (loadFailed) oms.components.ContentState(LocalizationManager.t("load_records_error"), error = true, onRetry = { reloadKey++ })
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DocumentTypeFilter.entries, key = { it.name }) { filter ->
                FilterChip(
                    selected = typeFilter == filter,
                    onClick = { typeFilter = filter },
                    label = { Text(LocalizationManager.t(filter.labelKey)) }
                )
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            oms.components.ScrollableTable(
                Modifier.padding(16.dp),
                pageScrollState = pageScrollState,
                header = { DocumentTableHeader(sort, ascending, ::selectSort); HorizontalDivider() }
            ) {
                if (!loading && !loadFailed && visibleDocuments.isEmpty()) Text(LocalizationManager.t("no_documents"))
                pageDocuments.forEach { row ->
                    Row(Modifier.width(1_370.dp).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(row.fileName, Modifier.width(260.dp))
                        ExpandableTableText(row.projectName, Modifier.width(210.dp))
                        Text(row.subprojectPartCode ?: "—", Modifier.width(165.dp))
                        Box(Modifier.width(140.dp)) { DocumentTypeChip(row.documentType) }
                        Text(row.date ?: "—", Modifier.width(105.dp))
                        Text(row.fileSizeBytes?.let(::formatFileSize) ?: "—", Modifier.width(90.dp))
                        Text("—", Modifier.width(85.dp))
                        TableActionIconButton(if (row.isSirSource) LocalizationManager.t("open_source_file") else LocalizationManager.t("open_document"), Icons.AutoMirrored.Filled.OpenInNew) {
                            val path = if (row.isSirSource) "/inspection-reports/${row.uuid}/source-file" else "/projects/${row.projectUuid}/documents/${row.uuid}/download"
                            uriHandler.openUri(oms.data.omsApiUrl(path))
                        }
                        if (canManageDocuments) TableActionIconButton(LocalizationManager.t("delete"), Icons.Default.Delete) {
                            deletion.show(row.fileName + if (row.isSirSource) "\n" + LocalizationManager.t("sir_delete_warning") else "") { scope.launch {
                                val deleted = if (row.isSirSource) OmsApiClient.deleteInspectionReport(row.uuid)
                                else OmsApiClient.deleteProjectDocument(row.projectUuid, row.uuid)
                                if (deleted) {
                                    documents = documents.filterNot { it.uuid == row.uuid && it.isSirSource == row.isSirSource }
                                } else errorMessage = LocalizationManager.t("delete_document_error")
                            } }
                        }
                        else Spacer(Modifier.width(48.dp))
                    }
                    HorizontalDivider()
                }
            }
        }
        DocumentPagination(pageSize, currentPage, pageCount, visibleDocuments.size, { pageSize = it }, { currentPage = it })
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (showUploadDialog) WasmSafeOverlay(onDismiss = { showUploadDialog = false }) {
            ProjectDocumentUploadDialog(
            projects = ProjectRepository.projects,
            loadProjectsOnOpen = {
                ProjectRepository.refresh()
                ProjectRepository.projects
            },
            onDismiss = { showUploadDialog = false },
            onUpload = { projectUuid, type ->
                openProjectDocumentUpload(projectUuid, type) { uploadError ->
                    if (uploadError.isBlank()) reloadKey++
                    else errorMessage = LocalizationManager.t("upload_document_error").replace("{message}", uploadError)
                }
                showUploadDialog = false
            }
            )
        }
    }
}

@Composable
private fun ProjectDocumentUploadDialog(
    projects: List<oms.model.Project>,
    loadProjectsOnOpen: suspend () -> List<oms.model.Project>,
    onDismiss: () -> Unit,
    onUpload: (String, String) -> Unit
) {
    var availableProjects by remember { mutableStateOf(projects) }
    var rootUuid by remember { mutableStateOf<String?>(null) }
    var subprojectUuid by remember { mutableStateOf<String?>(null) }
    var partUuid by remember { mutableStateOf<String?>(null) }
    var docType by remember { mutableStateOf("other") }
    val roots = availableProjects.filter { it.projectType == "project" }
    val subprojects = availableProjects.filter { it.projectType == "subproject" && it.parentProjectUuid == rootUuid }
    val parts = availableProjects.filter { it.projectType == "subproject_part" && it.parentProjectUuid == subprojectUuid }
    val targetUuid = partUuid ?: subprojectUuid
    Card(Modifier.widthIn(max = 720.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.padding(20.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(LocalizationManager.t("upload_document"), style = MaterialTheme.typography.titleLarge)
            InlineOptionPicker(
                options = roots,
                selected = roots.firstOrNull { it.id == rootUuid },
                prompt = LocalizationManager.t("select_project"),
                onSelect = { rootUuid = it.id; subprojectUuid = null; partUuid = null },
                itemLabel = { it.localizedName() },
                loadOptionsOnOpen = {
                    val loaded = loadProjectsOnOpen()
                    availableProjects = loaded
                    loaded.filter { it.projectType == "project" }
                }
            )
            SearchableOptionPicker(
                options = subprojects,
                selected = subprojects.firstOrNull { it.id == subprojectUuid },
                label = LocalizationManager.t("select_subproject"),
                onSelect = { subprojectUuid = it.id; partUuid = null },
                itemLabel = { listOf(it.siteNumber, it.localizedName()).filter(String::isNotBlank).distinct().joinToString(" — ") },
                enabled = rootUuid != null && subprojects.isNotEmpty()
            )
            InlineOptionPicker(
                options = parts,
                selected = parts.firstOrNull { it.id == partUuid },
                prompt = LocalizationManager.t("select_subproject_part"),
                onSelect = { partUuid = it.id },
                itemLabel = { listOf(it.siteNumber, it.localizedName()).filter(String::isNotBlank).distinct().joinToString(" — ") },
                enabled = subprojectUuid != null && parts.isNotEmpty(),
                clearLabel = LocalizationManager.t("all"),
                onClear = { partUuid = null }
            )
            Text(LocalizationManager.t("type"), style = MaterialTheme.typography.labelLarge)
            InlineOptionPicker(
                options = listOf("contract", "design", "estimate", "invoice", "act", "photo", "other"),
                selected = docType,
                prompt = LocalizationManager.t("type"),
                onSelect = { docType = it },
                itemLabel = { type -> LocalizationManager.t(if (type == "invoice" || type == "act") "record_type_$type" else type) }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                Button(onClick = { onUpload(targetUuid!!, docType) }, enabled = targetUuid != null) { Text(LocalizationManager.t("choose_file")) }
            }
        }
    }
}

@Composable
private fun DocumentPagination(
    pageSize: Int,
    currentPage: Int,
    pageCount: Int,
    total: Int,
    onPageSize: (Int) -> Unit,
    onPage: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(LocalizationManager.t("rows_per_page"), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        InlineOptionPicker(listOf(20, 50, 100), pageSize, LocalizationManager.t("rows_per_page"), onPageSize, modifier = Modifier.width(100.dp), fillWidth = false)
        Spacer(Modifier.width(16.dp))
        TextButton(onClick = { onPage((currentPage - 1).coerceAtLeast(0)) }, enabled = currentPage > 0) { Text("‹") }
        Text(if (total == 0) "0 / 0" else "${currentPage * pageSize + 1}–${minOf((currentPage + 1) * pageSize, total)} / $total")
        TextButton(onClick = { onPage((currentPage + 1).coerceAtMost(pageCount - 1)) }, enabled = currentPage < pageCount - 1) { Text("›") }
    }
}

@Composable
private fun DocumentTableHeader(sort: DocumentSort, ascending: Boolean, onSort: (DocumentSort) -> Unit) {
    Row(Modifier.width(1_370.dp).padding(vertical = 6.dp)) {
        SortableTableHeader(LocalizationManager.t("file_name"), sort == DocumentSort.Name, ascending, { onSort(DocumentSort.Name) }, Modifier.width(260.dp))
        SortableTableHeader(LocalizationManager.t("subproject"), sort == DocumentSort.Project, ascending, { onSort(DocumentSort.Project) }, Modifier.width(210.dp))
        SortableTableHeader(LocalizationManager.t("subproject_part_code_label"), sort == DocumentSort.SubprojectPartCode, ascending, { onSort(DocumentSort.SubprojectPartCode) }, Modifier.width(165.dp))
        SortableTableHeader(LocalizationManager.t("type"), sort == DocumentSort.Type, ascending, { onSort(DocumentSort.Type) }, Modifier.width(140.dp))
        SortableTableHeader(LocalizationManager.t("date"), sort == DocumentSort.Date, ascending, { onSort(DocumentSort.Date) }, Modifier.width(105.dp))
        SortableTableHeader(LocalizationManager.t("size"), sort == DocumentSort.Size, ascending, { onSort(DocumentSort.Size) }, Modifier.width(90.dp))
        SortableTableHeader(LocalizationManager.t("uploaded_by_short"), sort == DocumentSort.Author, ascending, { onSort(DocumentSort.Author) }, Modifier.width(85.dp))
        Spacer(Modifier.width(96.dp))
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
