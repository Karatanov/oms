package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlin.js.JsName
import kotlin.time.TimeSource
import oms.components.StatusChip
import oms.components.TableHeader
import oms.components.ProjectTableColumns
import oms.components.TableActionIconButton
import oms.components.ExpandableTableText
import oms.components.OmsBadge
import oms.components.safeTextInput
import oms.data.ProjectRepository
import oms.localization.LocalizationManager
import oms.model.Project
import oms.model.ProjectStatus
import oms.model.localizedName
import oms.model.localizedCity
import oms.model.localizedBeneficiary
import oms.components.localizedUkraineRegion
import oms.components.canonicalUkraineRegion
import oms.theme.Primary
import oms.components.toOmsDate
import oms.components.ConstructionTypeChip
import oms.components.constructionTypes
import oms.components.constructionTypeLabel
import oms.components.InlineOptionPicker
import oms.components.SectorChip
import oms.components.sectors
import oms.components.sectorLabel
import oms.data.ApiUser
import kotlinx.browser.window

@JsName("openSubprojectMonitoringWorkbookUpload")
external fun openSubprojectMonitoringWorkbookUpload(onComplete: (String, String) -> Unit)

private const val subprojectMonitoringWorkbookUrl =
    "https://docs.google.com/spreadsheets/d/1-RQLaRuWT7_VYYXdxJM3cfTOUqVuGSZYuWc03heVvfw/edit?gid=1116614736#gid=1116614736"
private const val allocationWorkbookUrl =
    "https://docs.google.com/spreadsheets/d/1QVniQDv_2iFBQr1NqsuXs6GM6rrQPpDf/edit?gid=1331578786#gid=1331578786"

// ... existing code ...

private const val PROGRAMME_ROOT_CODE = "URP-III"

/** The programme remains the database parent; it is a page heading, not a row. */
private fun Project.isProgrammeRoot(): Boolean =
    projectType.equals("project", ignoreCase = true) &&
        (siteNumber.equals(PROGRAMME_ROOT_CODE, ignoreCase = true) ||
            name.equals("Ukraine Recovery III", ignoreCase = true) ||
            name.equals("Ukraine Recovery Programme III", ignoreCase = true))

@Composable
fun ProjectsScreen(
    onOpenProject: (Project) -> Unit = {},
    onCreateProject: () -> Unit = {},
    onEditProject: (Project) -> Unit = {},
    canManageProjects: Boolean = false,
    canRefreshSubprojectRegistry: Boolean = false,
    canPermanentlyDeleteProjects: Boolean = false,
    canBulkReassign: Boolean = false,
    requestedRegionFilter: String? = null,
    onRequestedRegionFilterConsumed: () -> Unit = {}
) {

    val deletion = oms.components.LocalDeleteConfirmation.current
    var searchText by remember { mutableStateOf("") }
    var regionFilter by remember { mutableStateOf<String?>(null) }
    // The registry opens on active subprojects. "Archived" is the ordinary
    // project status, not a second technical archive state.
    var statusFilter by remember { mutableStateOf<ProjectStatus?>(ProjectStatus.ACTIVE) }
    var trancheFilter by remember { mutableStateOf<Int?>(null) }
    var constructionTypeFilter by remember { mutableStateOf<String?>(null) }
    var sectorFilter by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedProjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var managers by remember { mutableStateOf<List<ApiUser>>(emptyList()) }
    var managersLoaded by remember { mutableStateOf(false) }
    var showReassign by remember { mutableStateOf(false) }
    var registryRefreshConfirmationOpen by remember { mutableStateOf(false) }
    var registryRefreshJob by remember { mutableStateOf<oms.data.ApiSubprojectRegistryRefresh?>(null) }
    var registryRefreshElapsedSeconds by remember { mutableStateOf(0) }
    var registryRefreshMessage by remember { mutableStateOf<String?>(null) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val pageScrollState = rememberScrollState()
    fun scrollPageBy(delta: Float) = scope.launch { pageScrollState.animateScrollBy(delta) }

    LaunchedEffect(requestedRegionFilter) {
        requestedRegionFilter?.let {
            regionFilter = canonicalUkraineRegion(it)
            onRequestedRegionFilterConsumed()
        }
    }

    LaunchedEffect(showReassign) {
        if (showReassign) {
            pageScrollState.animateScrollTo(pageScrollState.maxValue)
            // Users are only needed for the bulk-reassign tool, not for the
            // normal project registry and its filters.
            if (!managersLoaded && canBulkReassign) {
                managers = runCatching { oms.data.OmsApiClient.users() }
                    .getOrDefault(emptyList())
                    .filter { it.role.code == "PROJECT_MANAGER" && it.status == "active" }
                managersLoaded = true
            }
        }
    }

    LaunchedEffect(registryRefreshJob?.id) {
        val id = registryRefreshJob?.id ?: return@LaunchedEffect
        registryRefreshElapsedSeconds = 0
        val startedAt = TimeSource.Monotonic.markNow()
        while (registryRefreshJob?.status in setOf("RUNNING", "CANCELLING")) {
            delay(400)
            registryRefreshElapsedSeconds = startedAt.elapsedNow().inWholeSeconds.toInt()
            runCatching { oms.data.OmsApiClient.subprojectRegistryRefresh(id) }
                .onSuccess { updated ->
                    registryRefreshJob = updated
                    when (updated.status) {
                        "COMPLETED" -> {
                            ProjectRepository.refresh(force = true)
                            registryRefreshMessage = LocalizationManager.t("subproject_registry_refresh_success")
                                .replace("{count}", updated.importedCount?.toString().orEmpty())
                        }
                        "FAILED" -> registryRefreshMessage = LocalizationManager.t("subproject_registry_refresh_error")
                            .replace("{message}", updated.error.orEmpty())
                        "CANCELLED" -> registryRefreshMessage = LocalizationManager.t("subproject_registry_refresh_cancelled")
                    }
                }
                .onFailure { failure ->
                    registryRefreshJob = registryRefreshJob?.copy(status = "FAILED", error = failure.message)
                    registryRefreshMessage = LocalizationManager.t("subproject_registry_refresh_error")
                        .replace("{message}", failure.message.orEmpty())
                }
        }
    }

    var initialLoad by remember { mutableStateOf(true) }
    // The registry is maintained through server-side workbook imports.  Do not
    // reuse the process-wide snapshot when this screen is opened: otherwise a
    // user who navigated here before an import can keep seeing the previous
    // registry until a complete browser reload.
    LaunchedEffect(Unit) { ProjectRepository.refresh(force = true); initialLoad = false }
    oms.navigation.ReportRouteReadiness(initialLoad, ProjectRepository.errorMessage != null)
    val projects = ProjectRepository.projects
    val programme = projects.firstOrNull(Project::isProgrammeRoot)

    val filteredProjects = remember(projects, searchText, regionFilter, statusFilter, trancheFilter, constructionTypeFilter, sectorFilter) {
        fun matches(project: Project) =
            (searchText.isBlank() ||
                project.localizedName().contains(searchText, true) ||
                project.siteNumber.contains(searchText, true) ||
                localizedUkraineRegion(project.region).contains(searchText, true) ||
                project.localizedCity().contains(searchText, true)) &&
                (regionFilter == null || canonicalUkraineRegion(project.region) == regionFilter) &&
                (statusFilter == null || project.status == statusFilter) &&
                project.matchesTranche(trancheFilter) &&
                (constructionTypeFilter == null || project.constructionType.equals(constructionTypeFilter, ignoreCase = true)) &&
                (sectorFilter == null || project.sector.equals(sectorFilter, ignoreCase = true))
        val matchingProjects = projects.filter(::matches).filterNot(Project::isProgrammeRoot)
        // A lifecycle filter must be exact.  Previously we reinserted every
        // parent solely to preserve the visual tree, so choosing “Active”
        // still showed planned parents.  With an explicit status selected,
        // matching children become visible roots instead.
        if (statusFilter != null) return@remember matchingProjects
        val projectsById = projects.associateBy { it.id }
        val visibleIds = matchingProjects
            .flatMap { project ->
                generateSequence(project) { current -> current.parentProjectUuid?.let(projectsById::get) }.toList()
            }
            .mapTo(mutableSetOf()) { it.id }
        projects.filter { it.id in visibleIds && !it.isProgrammeRoot() }
    }
    val exportFilterSummary = buildList {
        if (searchText.isNotBlank()) add("${LocalizationManager.t("search_project")}: $searchText")
        regionFilter?.let { add("${LocalizationManager.t("region")}: ${localizedUkraineRegion(it)}") }
        statusFilter?.let { add("${LocalizationManager.t("status")}: ${LocalizationManager.t("project_status_${it.name.lowercase()}")}") }
        trancheFilter?.let { add("${LocalizationManager.t("tranche")}: ${it.trancheLabel()}") }
        constructionTypeFilter?.let { add("${LocalizationManager.t("construction_type")}: ${it.constructionTypeLabel()}") }
        sectorFilter?.let { add("${LocalizationManager.t("sector")}: ${it.sectorLabel()}") }
    }.joinToString("; ")

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onPrimary = Color.White)) {
    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { scrollPageBy(-420f); true }
                    Key.DirectionDown -> { scrollPageBy(420f); true }
                    Key.PageUp -> { scrollPageBy(-720f); true }
                    Key.PageDown -> { scrollPageBy(720f); true }
                    Key.MoveHome -> { scope.launch { pageScrollState.animateScrollTo(0) }; true }
                    Key.MoveEnd -> { scope.launch { pageScrollState.animateScrollTo(pageScrollState.maxValue) }; true }
                    else -> false
                }
            }
            .verticalScroll(pageScrollState)
            .padding(oms.layout.workspacePadding(desktop = 16.dp))
    ) {

        // The programme is contextual information, not a table row.  Keep it
        // centred independently of the page title and the create action.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 820.dp) Box(Modifier.fillMaxWidth().heightIn(min = 42.dp)) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                    Text(LocalizationManager.t("projects_title"), style = MaterialTheme.typography.headlineMedium)
                }
                programme?.let { root ->
                    Text(
                        root.localizedName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.Center).widthIn(max = 360.dp)
                    )
                }
                if (canManageProjects || canRefreshSubprojectRegistry) Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canRefreshSubprojectRegistry) OutlinedButton(
                        enabled = registryRefreshJob?.status !in setOf("RUNNING", "CANCELLING"),
                        onClick = { registryRefreshMessage = null; registryRefreshConfirmationOpen = true }
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text(LocalizationManager.t("refresh_subproject_registry"))
                    }
                    if (canManageProjects) Button(onClick = onCreateProject) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(LocalizationManager.t("create_project"))
                    }
                }
            } else oms.components.PageHeading(LocalizationManager.t("projects_title"), Icons.Default.FolderOpen) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    programme?.let { root -> Text(root.localizedName(), maxLines = 2, modifier = Modifier.widthIn(max = 260.dp)) }
                    if (canManageProjects) Button(onClick = onCreateProject) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(LocalizationManager.t("create_project"))
                    }
                    if (canRefreshSubprojectRegistry) OutlinedButton(
                        enabled = registryRefreshJob?.status !in setOf("RUNNING", "CANCELLING"),
                        onClick = { registryRefreshMessage = null; registryRefreshConfirmationOpen = true }
                    ) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(LocalizationManager.t("refresh_subproject_registry")) }
                }
            }
        }

        registryRefreshMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))
        if (ProjectRepository.loading) oms.components.ContentState(LocalizationManager.t("loading_records"), loading = true)
        ProjectRepository.errorMessage?.let { oms.components.ContentState(it, error = true, onRetry = { scope.launch { ProjectRepository.refresh(force = true) } }) }

        if (canManageProjects && selectedProjectIds.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(LocalizationManager.t("selected_projects").replace("{count}", selectedProjectIds.size.toString()))
                    OutlinedButton(onClick = { selectedProjectIds = emptySet() }) { Text(LocalizationManager.t("clear_selection")) }
                    OutlinedButton(onClick = { oms.data.openOmsDownload(oms.data.OmsApiClient.projectExportUrl(selectedProjectIds)) }) { Text("XLSX") }
                    if (canBulkReassign) Button(onClick = { showReassign = true }) { Text(LocalizationManager.t("reassign")) }
                    Button(onClick = {
                        scope.launch {
                            runCatching { oms.data.OmsApiClient.bulkUpdateProjectStatus(selectedProjectIds.toList(), "suspended") }
                                .onSuccess { ProjectRepository.refresh(force = true); selectedProjectIds = emptySet() }
                                .onFailure { errorMessage = it.message ?: LocalizationManager.t("error_suspend_projects") }
                        }
                    }) { Text(LocalizationManager.t("project_status_suspended")) }
                    Button(onClick = {
                        scope.launch {
                            runCatching { oms.data.OmsApiClient.bulkUpdateProjectStatus(selectedProjectIds.toList(), "archived") }
                                .onSuccess { ProjectRepository.refresh(force = true); selectedProjectIds = emptySet() }
                                .onFailure { errorMessage = it.message ?: LocalizationManager.t("error_archive_projects") }
                        }
                    }) { Text(LocalizationManager.t("project_status_archived")) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        ProjectsTable(
            projects = filteredProjects,
            searchText = searchText,
            onSearchTextChange = { searchText = it.safeTextInput(200) },
            pageScrollState = pageScrollState,
            filters = {
                ProjectsFilters(regionFilter, {
                    regionFilter = it
                    // The default Active state concealed entire regions whose
                    // subprojects are currently planned.  Selecting a region
                    // therefore deliberately broadens the lifecycle filter;
                    // the user can immediately choose a specific status again.
                    if (it != null) statusFilter = null
                }, statusFilter, { statusFilter = it },
                    trancheFilter, { trancheFilter = it },
                    constructionTypeFilter, { constructionTypeFilter = it }, sectorFilter, { sectorFilter = it },
                    canReset = searchText.isNotBlank() || regionFilter != null || statusFilter != ProjectStatus.ACTIVE || trancheFilter != null || constructionTypeFilter != null || sectorFilter != null,
                    onReset = { searchText = ""; regionFilter = null; statusFilter = ProjectStatus.ACTIVE; trancheFilter = null; constructionTypeFilter = null; sectorFilter = null })
            },
            onOpenProject = onOpenProject,
            onEditProject = onEditProject,
            onDeleteProject = { project ->
                deletion.showWithArchive(
                    name = project.localizedName(),
                    deleteAction = {
                        scope.launch {
                            val completed = oms.data.OmsApiClient.permanentlyDeleteProject(project.id)
                            if (completed) ProjectRepository.refresh(force = true)
                            else errorMessage = LocalizationManager.t("permanent_delete_failed")
                        }
                    },
                    archiveAction = {
                        scope.launch {
                            if (oms.data.OmsApiClient.archiveProject(project.id)) ProjectRepository.refresh(force = true)
                            else errorMessage = LocalizationManager.t("error_archive_projects")
                        }
                    }
                )
            },
            canManageProjects = canManageProjects,
            canPermanentlyDeleteProjects = canPermanentlyDeleteProjects,
            onExpandRow = { rowTop -> scope.launch { pageScrollState.animateScrollTo((pageScrollState.value + rowTop - 16f).toInt().coerceAtLeast(0)) } },
            selectedProjectIds = selectedProjectIds,
            onSelectionChange = { id, selected -> selectedProjectIds = if (selected) selectedProjectIds + id else selectedProjectIds - id },
            onExportPdf = if (canManageProjects) { orderedIds ->
                val language = if (oms.localization.LocalizationManager.currentLanguage == oms.localization.Language.EN) "en" else "uk"
                oms.data.OmsApiClient.downloadProjectTablePdf(orderedIds, language, exportFilterSummary)
            } else null
        )

        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (showReassign) {
            var selectedManager by remember { mutableStateOf(managers.firstOrNull()) }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(LocalizationManager.t("reassign_projects"), style = MaterialTheme.typography.titleMedium)
                    InlineOptionPicker(managers, selectedManager, LocalizationManager.t("role_project_manager"), { selectedManager = it }, { "${it.firstName} ${it.lastName} (${it.username})" })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        OutlinedButton(onClick = { showReassign = false }) { Text(LocalizationManager.t("cancel")) }
                        Button(onClick = {
                            selectedManager?.let { manager ->
                                scope.launch {
                                    runCatching { oms.data.OmsApiClient.bulkReassignProjects(selectedProjectIds.toList(), manager.id) }
                                        .onSuccess { ProjectRepository.refresh(force = true); selectedProjectIds = emptySet(); showReassign = false }
                                        .onFailure { errorMessage = it.message ?: LocalizationManager.t("reassign_error") }
                                }
                            }
                        }, enabled = selectedManager != null) { Text(LocalizationManager.t("reassign")) }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

    }
    if (registryRefreshConfirmationOpen) oms.components.WasmSafeOverlay(onDismiss = { registryRefreshConfirmationOpen = false }) {
        Card(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(LocalizationManager.t("subproject_registry_refresh_confirm_title"), style = MaterialTheme.typography.titleLarge)
                Text(LocalizationManager.t("subproject_registry_refresh_confirm_message"), style = MaterialTheme.typography.bodyMedium)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(LocalizationManager.t("subproject_registry_refresh_instruction_step_1"), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        subprojectMonitoringWorkbookUrl,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { uriHandler.openUri(subprojectMonitoringWorkbookUrl) }
                    )
                    Text(LocalizationManager.t("subproject_registry_refresh_allocation_source"), style = MaterialTheme.typography.bodySmall)
                    Text(
                        allocationWorkbookUrl,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { uriHandler.openUri(allocationWorkbookUrl) }
                    )
                    Text(LocalizationManager.t("subproject_registry_refresh_instruction_step_2"), style = MaterialTheme.typography.bodyMedium)
                    Text(LocalizationManager.t("subproject_registry_refresh_instruction_step_3"), style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    OutlinedButton(onClick = { registryRefreshConfirmationOpen = false }) { Text(LocalizationManager.t("cancel")) }
                    Button(onClick = {
                        registryRefreshConfirmationOpen = false
                        openSubprojectMonitoringWorkbookUpload { uploadError, refreshJson ->
                            if (uploadError.isNotBlank()) {
                                registryRefreshMessage = LocalizationManager.t("subproject_registry_refresh_error").replace("{message}", uploadError)
                            } else runCatching {
                                Json { ignoreUnknownKeys = true }.decodeFromString<oms.data.ApiSubprojectRegistryRefresh>(refreshJson)
                            }.onSuccess { registryRefreshJob = it }
                                .onFailure { registryRefreshMessage = LocalizationManager.t("subproject_registry_refresh_error").replace("{message}", it.message.orEmpty()) }
                        }
                    }) { Text(LocalizationManager.t("subproject_registry_refresh_continue")) }
                }
            }
        }
    }
    registryRefreshJob?.takeIf { it.status in setOf("RUNNING", "CANCELLING") }?.let { job ->
        oms.components.WasmSafeOverlay(onDismiss = {}) {
            Card(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(LocalizationManager.t("subproject_registry_refresh_progress_title"), style = MaterialTheme.typography.titleLarge)
                    Text(subprojectRegistryRefreshPhaseLabel(job.phase), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { (job.progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${job.progress}% · ${formatSubprojectRefreshElapsed(registryRefreshElapsedSeconds)}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        enabled = job.status == "RUNNING",
                        onClick = { scope.launch { runCatching { oms.data.OmsApiClient.cancelSubprojectRegistryRefresh(job.id) }.onSuccess { registryRefreshJob = it } } },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(LocalizationManager.t("subproject_registry_refresh_cancel")) }
                }
            }
        }
    }
    }
    }
}

// ... existing code ...

private fun formatSubprojectRefreshElapsed(seconds: Int): String =
    "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"

private fun subprojectRegistryRefreshPhaseLabel(phase: String): String = when (phase) {
    "opening_workbook" -> LocalizationManager.t("subproject_registry_refresh_phase_opening_workbook")
    "reading_headers" -> LocalizationManager.t("subproject_registry_refresh_phase_reading_headers")
    "reading_rows" -> LocalizationManager.t("subproject_registry_refresh_phase_reading_rows")
    "validating" -> LocalizationManager.t("subproject_registry_refresh_phase_validating")
    "saving" -> LocalizationManager.t("subproject_registry_refresh_phase_saving")
    "cancelling" -> LocalizationManager.t("subproject_registry_refresh_phase_cancelling")
    else -> LocalizationManager.t("loading_records")
}

@Composable
fun ProjectsTable(
    projects: List<Project>,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    pageScrollState: ScrollState,
    filters: @Composable () -> Unit,
    onOpenProject: (Project) -> Unit,
    onEditProject: (Project) -> Unit,
    onDeleteProject: (Project) -> Unit,
    canManageProjects: Boolean,
    canPermanentlyDeleteProjects: Boolean,
    onExpandRow: (Float) -> Unit,
    selectedProjectIds: Set<String>,
    onSelectionChange: (String, Boolean) -> Unit,
    onExportPdf: ((List<String>) -> Unit)? = null
) {

    var sortColumn by remember { mutableStateOf(SortColumn.NAME) }
    var ascending by remember { mutableStateOf(true) }
    var expandedParentIds by remember { mutableStateOf<Set<String>?>(null) }
    var pageSize by remember { mutableStateOf(20) }
    var currentPage by remember { mutableStateOf(0) }

    val sortedProjects = remember(projects, sortColumn, ascending, expandedParentIds) {
        fun sort(items: List<Project>): List<Project> = items.sortedWith { left, right ->
            val comparison = when (sortColumn) {
                SortColumn.ID -> compareBusinessIds(left.siteNumber, right.siteNumber)
                else -> sortKey(left, sortColumn).compareTo(sortKey(right, sortColumn), ignoreCase = true)
            }
            if (ascending) comparison else -comparison
        }

        val childrenByParent = projects
            .filter { !it.projectType.equals("project", ignoreCase = true) }
            .groupBy { it.parentProjectUuid }
        val displayedIds = projects.mapTo(mutableSetOf()) { it.id }
        // The programme parent is intentionally omitted from this visual tree.
        // Its direct subprojects become the visible roots without losing their
        // parent UUID in the persisted model.
        val roots = sort(projects.filter { project ->
            project.projectType.equals("project", ignoreCase = true) ||
                project.parentProjectUuid !in displayedIds
        })
        val expanded = expandedParentIds ?: roots.map { it.id }.toSet()
        buildList {
            fun addBranch(item: Project, depth: Int, isLast: Boolean) {
                val children = sort(childrenByParent[item.id].orEmpty())
                val isExpanded = item.id in expanded
                add(ProjectTreeRow(item, depth, isLast, children.size, isExpanded))
                if (isExpanded) children.forEachIndexed { index, child ->
                    addBranch(child, depth + 1, index == children.lastIndex)
                }
            }
            roots.forEachIndexed { index, root -> addBranch(root, 0, index == roots.lastIndex) }
        }
    }

    LaunchedEffect(projects, sortColumn, ascending, expandedParentIds, pageSize) {
        currentPage = 0
    }
    val pageCount = if (sortedProjects.isEmpty() || pageSize == Int.MAX_VALUE) 1
    else (sortedProjects.size + pageSize - 1) / pageSize
    if (currentPage >= pageCount) currentPage = (pageCount - 1).coerceAtLeast(0)
    val pageRows = if (pageSize == Int.MAX_VALUE) sortedProjects
    else sortedProjects.drop(currentPage * pageSize).take(pageSize)

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 620.dp) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                oms.components.WorkspaceSearchField(value = searchText, onValueChange = onSearchTextChange, label = LocalizationManager.t("search_project"), modifier = Modifier.weight(1f).widthIn(min = 280.dp))
                onExportPdf?.let { export ->
                    OutlinedButton(onClick = { export(sortedProjects.map { it.project.id }) }, enabled = sortedProjects.isNotEmpty()) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(LocalizationManager.t("export_pdf"))
                    }
                }
                ProjectPagination(pageSize, currentPage, pageCount, sortedProjects.size, { pageSize = it }) { currentPage = it.coerceIn(0, pageCount - 1) }
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                oms.components.WorkspaceSearchField(value = searchText, onValueChange = onSearchTextChange, label = LocalizationManager.t("search_project"), modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    onExportPdf?.let { export -> OutlinedButton(onClick = { export(sortedProjects.map { it.project.id }) }, enabled = sortedProjects.isNotEmpty()) { Text(LocalizationManager.t("export_pdf")) } }
                    ProjectPagination(pageSize, currentPage, pageCount, sortedProjects.size, { pageSize = it }) { currentPage = it.coerceIn(0, pageCount - 1) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        oms.components.ScrollableTable(
            pageScrollState = pageScrollState,
            pinnedStartWidth = ProjectTableColumns.frozenStartWidth,
            pinnedStartHeader = {
                ProjectFrozenCodeHeader(sortColumn, ascending) { column ->
                    if (sortColumn == column) ascending = !ascending
                    else { sortColumn = column; ascending = true }
                }
            },
            pinnedStartContent = {
                pageRows.forEach { row ->
                    ProjectFrozenCodeRow(
                        project = row.project,
                        isProject = row.project.projectType.equals("project", ignoreCase = true),
                        isSelected = row.project.id in selectedProjectIds,
                        highlightSearchMatch = searchText.isNotBlank() && row.project.matchesProjectSearch(searchText),
                        childCount = row.childCount,
                        expanded = row.expanded,
                        canManageProjects = canManageProjects,
                        onToggleChildren = { id ->
                            val childParentIds = projects.filter { !it.projectType.equals("project", true) }.mapNotNull { it.parentProjectUuid }.toSet()
                            val parentIds = projects.filter { it.id in childParentIds }.map { it.id }.toSet()
                            val current = expandedParentIds ?: parentIds
                            expandedParentIds = if (id in current) current - id else current + id
                        },
                        onExpandRow = onExpandRow,
                        onSelectedChange = { onSelectionChange(row.project.id, it) },
                        onOpen = { onOpenProject(row.project) }
                    )
                }
            },
            header = {
                filters()
                TableHeader(sortColumn, ascending) { column ->
                    if (sortColumn == column) ascending = !ascending
                    else { sortColumn = column; ascending = true }
                }
                HorizontalDivider()
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                pageRows.forEach { row -> ProjectRow(
                    project = row.project,
                    highlightSearchMatch = searchText.isNotBlank() && row.project.matchesProjectSearch(searchText),
                    isSubproject = !row.project.projectType.equals("project", ignoreCase = true),
                    isLastSubproject = row.isLastSubproject,
                    indentLevel = row.depth,
                    childCount = row.childCount,
                    expanded = row.expanded,
                    onToggleChildren = { id ->
                        val childParentIds = projects.filter { !it.projectType.equals("project", true) }.mapNotNull { it.parentProjectUuid }.toSet()
                        val parentIds = projects.filter { it.id in childParentIds }.map { it.id }.toSet()
                        val current = expandedParentIds ?: parentIds
                        expandedParentIds = if (id in current) current - id else current + id
                    },
                    onExpandRow = onExpandRow,
                    onOpen = onOpenProject,
                    onEdit = onEditProject,
                    onDelete = onDeleteProject,
                    canManageProjects = canManageProjects,
                    canPermanentlyDeleteProjects = canPermanentlyDeleteProjects,
                    isSelected = row.project.id in selectedProjectIds,
                    onSelectedChange = { onSelectionChange(row.project.id, it) }
                ) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ProjectPagination(pageSize, currentPage, pageCount, sortedProjects.size, { pageSize = it }) {
                currentPage = it.coerceIn(0, pageCount - 1)
            }
        }
    }
}

/** Leading overlay used only while the wide registry is scrolled right. */
@Composable
private fun ProjectFrozenCodeHeader(
    sortColumn: SortColumn,
    ascending: Boolean,
    onSort: (SortColumn) -> Unit
) {
    Column(Modifier.width(ProjectTableColumns.frozenStartWidth).background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                LocalizationManager.t("filters"),
                modifier = Modifier.padding(start = ProjectTableColumns.selection + ProjectTableColumns.hierarchy),
                style = MaterialTheme.typography.labelLarge
            )
        }
        Row(Modifier.fillMaxWidth().height(ProjectTableColumns.headerHeight)) {
            Spacer(Modifier.width(ProjectTableColumns.selection + ProjectTableColumns.hierarchy))
            oms.components.SortableHeader(
                LocalizationManager.t("project_code"),
                SortColumn.ID,
                sortColumn,
                ascending,
                onSort,
                Modifier.width(ProjectTableColumns.width(SortColumn.ID)),
                height = ProjectTableColumns.headerHeight,
                maxLines = 3
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun ProjectFrozenCodeRow(
    project: Project,
    isProject: Boolean,
    isSelected: Boolean,
    highlightSearchMatch: Boolean,
    childCount: Int,
    expanded: Boolean,
    canManageProjects: Boolean,
    onToggleChildren: (String) -> Unit,
    onExpandRow: (Float) -> Unit,
    onSelectedChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    var rowTopInRoot by remember { mutableStateOf(0f) }
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        highlightSearchMatch -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.50f)
        isProject -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        Modifier
            .width(ProjectTableColumns.frozenStartWidth)
            .height(ProjectTableColumns.rowHeight)
            .background(background)
            .onGloballyPositioned { rowTopInRoot = it.positionInRoot().y },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canManageProjects) Checkbox(
            checked = isSelected,
            onCheckedChange = onSelectedChange,
            modifier = Modifier.width(ProjectTableColumns.selection)
        ) else Spacer(Modifier.width(ProjectTableColumns.selection))
        Box(
            modifier = Modifier.width(ProjectTableColumns.hierarchy).height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (childCount > 0) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .clickable { onToggleChildren(project.id); onExpandRow(rowTopInRoot) }
                        .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (expanded) LocalizationManager.t("collapse") else LocalizationManager.t("expand"),
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else if (isProject) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = LocalizationManager.t("project"),
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
            } else if (project.projectType.equals("subproject_part", ignoreCase = true)) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = LocalizationManager.t("subproject_part"),
                    tint = Primary,
                    modifier = Modifier.size(19.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = LocalizationManager.t("subproject"),
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            project.siteNumber,
            modifier = Modifier
                .width(ProjectTableColumns.width(SortColumn.ID))
                .clickable(onClick = onOpen)
                .pointerHoverIcon(PointerIcon.Hand),
            fontWeight = if (isProject) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
    HorizontalDivider()
}

@Composable
private fun ProjectPagination(
    pageSize: Int, currentPage: Int, pageCount: Int, total: Int,
    onPageSize: (Int) -> Unit, onPage: (Int) -> Unit
) {
    oms.components.AdaptivePagination(sizeControls = {
        Text(LocalizationManager.t("rows_per_page"), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        InlineOptionPicker(listOf(20, 50, 100, Int.MAX_VALUE), pageSize, LocalizationManager.t("rows_per_page"), onPageSize,
            itemLabel = { if (it == Int.MAX_VALUE) LocalizationManager.t("all") else it.toString() }, fillWidth = false)
    }, navigation = {
        Text(LocalizationManager.t("page_of").replace("{page}", (currentPage + 1).toString())
            .replace("{pages}", pageCount.toString()).replace("{total}", total.toString()), style = MaterialTheme.typography.bodySmall)
        TableActionIconButton(LocalizationManager.t("previous_page"), Icons.Default.KeyboardArrowLeft) { onPage(currentPage - 1) }
        TableActionIconButton(LocalizationManager.t("next_page"), Icons.Default.KeyboardArrowRight) { onPage(currentPage + 1) }
    })
}

private data class ProjectTreeRow(
    val project: Project,
    val depth: Int,
    val isLastSubproject: Boolean = false,
    val childCount: Int = 0,
    val expanded: Boolean = true
)

/*
   ---------- FILTERS ----------
*/

@Composable
private fun ProjectsFilters(
    regionFilter: String?,
    onRegionChange: (String?) -> Unit,
    statusFilter: ProjectStatus?,
    onStatusChange: (ProjectStatus?) -> Unit,
    trancheFilter: Int?,
    onTrancheChange: (Int?) -> Unit,
    constructionTypeFilter: String?,
    onConstructionTypeChange: (String?) -> Unit,
    sectorFilter: String?,
    onSectorChange: (String?) -> Unit,
    canReset: Boolean,
    onReset: () -> Unit
) {
    Row(Modifier.width(ProjectTableColumns.totalWidth).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(ProjectTableColumns.selection + ProjectTableColumns.hierarchy))
        ProjectTableColumns.columns.forEach { column ->
            Box(Modifier.width(ProjectTableColumns.width(column)).padding(end = 6.dp)) {
                when (column) {
                    SortColumn.ID -> Text(
                        LocalizationManager.t("filters"),
                        style = MaterialTheme.typography.labelLarge
                    )
                    SortColumn.REGION -> ProjectFilterDropdown(
                        LocalizationManager.t("region"),
                        ProjectRepository.projects.map { it.region }.filter { it.isNotBlank() }.map(::canonicalUkraineRegion).distinct().sorted(),
                        regionFilter, onRegionChange, ::localizedUkraineRegion)
                    SortColumn.POOL -> ProjectFilterDropdown(
                        LocalizationManager.t("tranche"), listOf(1, 2), trancheFilter, onTrancheChange,
                        { LocalizationManager.t(if (it == 1) "tranche_a" else "tranche_b") })
                    SortColumn.CONSTRUCTION_TYPE -> ProjectFilterDropdown(
                        LocalizationManager.t("construction_type"), constructionTypes, constructionTypeFilter,
                        onConstructionTypeChange, String::constructionTypeLabel)
                    else -> Unit
                }
            }
        }
        Box(
            Modifier.width(ProjectTableColumns.actions),
            contentAlignment = Alignment.CenterEnd
        ) {
            TextButton(
                onClick = onReset,
                enabled = canReset,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(LocalizationManager.t("reset_filters"), maxLines = 1, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun <T> ProjectFilterDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    itemLabel: (T) -> String = { it.toString() }
) {
    InlineOptionPicker(
        options = options, selected = selected, prompt = label, onSelect = onSelect,
        itemLabel = itemLabel,
        clearLabel = LocalizationManager.t("all"), onClear = { onSelect(null) }
    )
}


enum class SortColumn {
    ID, POOL, REGION, DREAM, NAME, COMMUNITY, PRIORITY, CONSTRUCTION_TYPE, IMPLEMENTER, BENEFICIARY,
    FUNDING_TOTAL, FUNDING_WORKS, FUNDING_TS, FUNDING_CSC, EIB_COST, LOCAL_COST,
    CONTRACTED_TOTAL, CONTRACTED_EIB, CONTRACTED_LOCAL, APPROVED_FUNDING_PCT, CONTRACTOR,
    CONTRACT_DATE, CONTRACT_END_DATE, CONTRACT_DURATION, CONSTRUCTION_AMOUNT, CONSTRUCTION_EIB,
    CONSTRUCTION_LOCAL, PAID_CONSTRUCTION, DED, ESTIMATE, EXPERTISE, PERMIT, WORK_STATUS,
    ACT_NUMBER, ACT_DATE, COMPLETED_WORK_PCT, FINANCING_USED_PCT, PAID_FINANCING_PCT,
    WORK_SUMMARY, WORK_COMMENTS,
    /** Kept for decoding saved local sort state from pre-registry versions. */
    TRANCHE, CITY, SECTOR, STATUS, BUDGET, START_DATE, PLANNED_END_DATE, IMPLEMENTATION_DURATION
}

private fun sortKey(project: Project, column: SortColumn): String = when (column) {
    SortColumn.ID -> project.siteNumber
    SortColumn.POOL, SortColumn.TRANCHE -> project.registryValue("pool", project.trancheNumber.toString()).numericSortKey()
    SortColumn.REGION -> localizedUkraineRegion(project.region)
    SortColumn.DREAM -> project.registryValue("dreamUrl")
    SortColumn.NAME -> project.localizedRegistryValue("name", project.localizedName())
    SortColumn.COMMUNITY -> project.localizedRegistryValue("community")
    SortColumn.PRIORITY -> project.sourceBilingualValue("priority", ukrainianBeforeSlash = true)
    SortColumn.CONSTRUCTION_TYPE -> project.sourceBilingualValue("constructionType", ukrainianBeforeSlash = false, fallback = project.constructionType)
    SortColumn.IMPLEMENTER -> project.localizedRegistryValue("implementer")
    SortColumn.BENEFICIARY -> project.localizedRegistryValue("beneficiary", project.localizedBeneficiary())
    SortColumn.FUNDING_TOTAL -> project.registryValue("fundingTotal").numericSortKey()
    SortColumn.FUNDING_WORKS -> project.registryValue("fundingWorks").numericSortKey()
    SortColumn.FUNDING_TS -> project.registryValue("fundingTs").numericSortKey()
    SortColumn.FUNDING_CSC -> project.registryValue("fundingCsc").numericSortKey()
    SortColumn.EIB_COST -> project.registryValue("eibCost").numericSortKey()
    SortColumn.LOCAL_COST -> project.registryValue("localCost").numericSortKey()
    SortColumn.CONTRACTED_TOTAL -> project.registryValue("contractedTotal").numericSortKey()
    SortColumn.CONTRACTED_EIB -> project.registryValue("contractedEib").numericSortKey()
    SortColumn.CONTRACTED_LOCAL -> project.registryValue("contractedLocal").numericSortKey()
    SortColumn.APPROVED_FUNDING_PCT -> project.registryValue("approvedFundingPct").numericSortKey()
    SortColumn.CONTRACTOR -> project.localizedRegistryValue("contractor", project.contractorName.orEmpty())
    SortColumn.CONTRACT_DATE -> project.registryValue("contractDate")
    SortColumn.CONTRACT_END_DATE -> project.registryValue("contractEndDate")
    SortColumn.CONTRACT_DURATION -> project.registryValue("contractDuration").numericSortKey()
    SortColumn.CONSTRUCTION_AMOUNT -> project.registryValue("constructionAmount").numericSortKey()
    SortColumn.CONSTRUCTION_EIB -> project.registryValue("constructionEib").numericSortKey()
    SortColumn.CONSTRUCTION_LOCAL -> project.registryValue("constructionLocal").numericSortKey()
    SortColumn.PAID_CONSTRUCTION -> project.registryValue("paidConstruction").numericSortKey()
    SortColumn.DED -> project.registryValue("dedUrl")
    SortColumn.ESTIMATE -> project.registryValue("estimateUrl")
    SortColumn.EXPERTISE -> project.registryValue("expertiseUrl")
    SortColumn.PERMIT -> project.registryValue("permitUrl")
    SortColumn.WORK_STATUS -> project.sourceBilingualValue("workStatus", ukrainianBeforeSlash = true)
    SortColumn.ACT_NUMBER -> project.registryValue("actNumber")
    SortColumn.ACT_DATE -> project.registryValue("actDate")
    SortColumn.COMPLETED_WORK_PCT -> project.registryValue("completedWorkPct").numericSortKey()
    SortColumn.FINANCING_USED_PCT -> project.registryValue("financingUsedPct").numericSortKey()
    SortColumn.PAID_FINANCING_PCT -> project.registryValue("paidFinancingPct").numericSortKey()
    SortColumn.WORK_SUMMARY -> project.registryValue("workSummary")
    SortColumn.WORK_COMMENTS -> project.registryValue("workComments")
    SortColumn.CITY -> project.localizedCity()
    SortColumn.SECTOR -> project.sector
    SortColumn.STATUS -> project.status.name
    SortColumn.BUDGET -> project.budgetPlanned.toString().padStart(20, '0')
    SortColumn.START_DATE -> project.startDate.orEmpty()
    SortColumn.PLANNED_END_DATE -> project.plannedEndDate.orEmpty()
    SortColumn.IMPLEMENTATION_DURATION -> project.contractDurationDays?.toString()?.padStart(12, '0').orEmpty()
}

private fun Project.registryValue(key: String, fallback: String = ""): String = registry[key]?.takeIf(String::isNotBlank) ?: fallback
private fun Project.localizedRegistryValue(key: String, fallback: String = ""): String =
    if (LocalizationManager.currentLanguage.name == "EN") registryValue("${key}En", registryValue(key, fallback)) else registryValue(key, fallback)
private fun Project.sourceBilingualValue(key: String, ukrainianBeforeSlash: Boolean, fallback: String = ""): String {
    val raw = registryValue(key, fallback)
    val parts = raw.split("/").map(String::trim).filter(String::isNotBlank)
    if (parts.size < 2) return raw
    val ukrainian = if (ukrainianBeforeSlash) parts.first() else parts.last()
    val english = if (ukrainianBeforeSlash) parts.last() else parts.first()
    return if (LocalizationManager.currentLanguage.name == "EN") english else ukrainian
}
private fun String.numericSortKey(): String = toDoubleOrNull()?.toString()?.padStart(30, '0') ?: ""

private fun compareBusinessIds(left: String, right: String): Int {
    val leftParts = Regex("(\\d+|\\D+)").findAll(left).map { it.value }.toList()
    val rightParts = Regex("(\\d+|\\D+)").findAll(right).map { it.value }.toList()
    for (index in 0 until minOf(leftParts.size, rightParts.size)) {
        val a = leftParts[index]
        val b = rightParts[index]
        val comparison = if (a.all(Char::isDigit) && b.all(Char::isDigit)) {
            a.trimStart('0').padStart(1, '0').length.compareTo(b.trimStart('0').padStart(1, '0').length)
                .takeIf { it != 0 } ?: a.trimStart('0').padStart(1, '0').compareTo(b.trimStart('0').padStart(1, '0'))
        } else a.compareTo(b, ignoreCase = true)
        if (comparison != 0) return comparison
    }
    return leftParts.size.compareTo(rightParts.size)
}

/*
   ---------- TABLE ROW ----------
*/
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProjectRow(

    project: Project,

    isSubproject: Boolean = false,
    isLastSubproject: Boolean = false,
    indentLevel: Int = if (isSubproject) 1 else 0,
    childCount: Int = 0,
    expanded: Boolean = true,
    onToggleChildren: (String) -> Unit = {},
    onExpandRow: (Float) -> Unit = {},

    onOpen: (Project) -> Unit = {},
    onEdit: (Project) -> Unit = {},
    onDelete: (Project) -> Unit = {},
    canManageProjects: Boolean = false,
    canPermanentlyDeleteProjects: Boolean = false,
    highlightSearchMatch: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {}

) {

    var hovered by remember { mutableStateOf(false) }
    var rowTopInRoot by remember { mutableStateOf(0f) }

    Row(
        modifier = Modifier
            .width(ProjectTableColumns.totalWidth)
            // The leading frozen strip mirrors this row. A shared fixed
            // height prevents checkbox minimum sizes or cell contents from
            // moving the overlay out of alignment while scrolling sideways.
            .height(ProjectTableColumns.rowHeight)
            .onGloballyPositioned { rowTopInRoot = it.positionInRoot().y }

            // hover detection
            .onPointerEvent(
                eventType = PointerEventType.Enter
            ) {
                hovered = true
            }

            .onPointerEvent(
                eventType = PointerEventType.Exit
            ) {
                hovered = false
            }

            // row click
            .clickable {
                onOpen(project)
            }

            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    hovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                    highlightSearchMatch -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.50f)
                    !isSubproject -> MaterialTheme.colorScheme.surfaceContainerLow
                    else -> MaterialTheme.colorScheme.surface
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        val rowFontWeight = if (!isSubproject) FontWeight.Bold else FontWeight.Normal

        if (canManageProjects) Checkbox(
            checked = isSelected,
            onCheckedChange = onSelectedChange,
            modifier = Modifier.width(ProjectTableColumns.selection)
        ) else Spacer(Modifier.width(ProjectTableColumns.selection))

        Box(
            modifier = Modifier.width(ProjectTableColumns.hierarchy).height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (childCount > 0) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .clickable { onToggleChildren(project.id); onExpandRow(rowTopInRoot) }
                    .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Primary, MaterialTheme.shapes.extraLarge),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (expanded) LocalizationManager.t("collapse") else LocalizationManager.t("expand"),
                        tint = Color.White
                    )
                }
            }
            } else if (!isSubproject) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = LocalizationManager.t("project"),
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                if (project.projectType.equals("subproject_part", ignoreCase = true)) {
                    Box(
                        modifier = Modifier.size(24.dp).background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = LocalizationManager.t("subproject_part"),
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else if (indentLevel == 0) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = LocalizationManager.t("subproject"),
                        tint = Primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text("•", color = Primary, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // The source identifier is the primary frozen value; selection and
        // hierarchy controls stay immediately to its left.
        Text(project.siteNumber, modifier = Modifier.width(ProjectTableColumns.width(SortColumn.ID)), fontWeight = rowFontWeight)

        ProjectTableColumns.columns.filterNot { it == SortColumn.ID }.forEach { column ->
            RegistryTableCell(project, column, rowFontWeight, isSubproject)
        }

        // Keep the icon group centred in the exact same 144dp cell as the
        // "Actions" heading.  This also keeps view-only rows aligned.
        Row(
            Modifier.width(ProjectTableColumns.actions),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableActionIconButton(LocalizationManager.t("view"), Icons.Default.Visibility) { onOpen(project) }
            if (canManageProjects) {
                TableActionIconButton(LocalizationManager.t("edit"), Icons.Default.Edit) { onEdit(project) }
                if (canPermanentlyDeleteProjects) {
                    TableActionIconButton(LocalizationManager.t("permanently_delete"), Icons.Default.DeleteForever) { onDelete(project) }
                }
            }
        }
    }

    HorizontalDivider()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RegistryTableCell(project: Project, column: SortColumn, fontWeight: FontWeight, isSubproject: Boolean) {
    val width = ProjectTableColumns.width(column)
    val sourceReference = when (column) {
        SortColumn.DREAM -> project.registryValue("dreamUrl")
        SortColumn.DED -> project.registryValue("dedUrl")
        SortColumn.ESTIMATE -> project.registryValue("estimateUrl")
        SortColumn.EXPERTISE -> project.registryValue("expertiseUrl")
        SortColumn.PERMIT -> project.registryValue("permitUrl")
        else -> ""
    }
    val link = sourceReference.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }.orEmpty()
    val value = when (column) {
        SortColumn.POOL -> project.registryValue("pool", project.trancheNumber.trancheLabel())
        SortColumn.REGION -> localizedUkraineRegion(project.region)
        SortColumn.DREAM, SortColumn.DED, SortColumn.ESTIMATE, SortColumn.EXPERTISE, SortColumn.PERMIT ->
            link.takeIf(String::isNotBlank)?.let { LocalizationManager.t("open") } ?: sourceReference
        SortColumn.NAME -> project.localizedRegistryValue("name", project.localizedName())
        SortColumn.COMMUNITY -> project.localizedRegistryValue("community")
        SortColumn.PRIORITY -> project.sourceBilingualValue("priority", ukrainianBeforeSlash = true)
        SortColumn.CONSTRUCTION_TYPE -> project.sourceBilingualValue("constructionType", ukrainianBeforeSlash = false, fallback = project.constructionType.constructionTypeLabel())
        SortColumn.IMPLEMENTER -> project.localizedRegistryValue("implementer")
        SortColumn.BENEFICIARY -> project.localizedRegistryValue("beneficiary", project.localizedBeneficiary())
        SortColumn.FUNDING_TOTAL -> project.registryValue("fundingTotal")
        SortColumn.FUNDING_WORKS -> project.registryValue("fundingWorks")
        SortColumn.FUNDING_TS -> project.registryValue("fundingTs")
        SortColumn.FUNDING_CSC -> project.registryValue("fundingCsc")
        SortColumn.EIB_COST -> project.registryValue("eibCost")
        SortColumn.LOCAL_COST -> project.registryValue("localCost")
        SortColumn.CONTRACTED_TOTAL -> project.registryValue("contractedTotal")
        SortColumn.CONTRACTED_EIB -> project.registryValue("contractedEib")
        SortColumn.CONTRACTED_LOCAL -> project.registryValue("contractedLocal")
        SortColumn.APPROVED_FUNDING_PCT -> project.registryValue("approvedFundingPct").takeIf(String::isNotBlank)?.plus(" %").orEmpty()
        SortColumn.CONTRACTOR -> project.localizedRegistryValue("contractor", project.contractorName.orEmpty())
        SortColumn.CONTRACT_DATE -> project.registryValue("contractDate").toOmsDate()
        SortColumn.CONTRACT_END_DATE -> project.registryValue("contractEndDate").toOmsDate()
        SortColumn.CONTRACT_DURATION -> project.registryValue("contractDuration").takeIf(String::isNotBlank)?.let { "$it ${LocalizationManager.t("months_short")}" }.orEmpty()
        SortColumn.CONSTRUCTION_AMOUNT -> project.registryValue("constructionAmount")
        SortColumn.CONSTRUCTION_EIB -> project.registryValue("constructionEib")
        SortColumn.CONSTRUCTION_LOCAL -> project.registryValue("constructionLocal")
        SortColumn.PAID_CONSTRUCTION -> project.registryValue("paidConstruction")
        SortColumn.WORK_STATUS -> project.sourceBilingualValue("workStatus", ukrainianBeforeSlash = true)
        SortColumn.ACT_NUMBER -> project.registryValue("actNumber")
        SortColumn.ACT_DATE -> project.registryValue("actDate").toOmsDate()
        SortColumn.COMPLETED_WORK_PCT -> project.registryValue("completedWorkPct").takeIf(String::isNotBlank)?.plus(" %").orEmpty()
        SortColumn.FINANCING_USED_PCT -> project.registryValue("financingUsedPct").takeIf(String::isNotBlank)?.plus(" %").orEmpty()
        SortColumn.PAID_FINANCING_PCT -> project.registryValue("paidFinancingPct").takeIf(String::isNotBlank)?.plus(" %").orEmpty()
        SortColumn.WORK_SUMMARY -> project.registryValue("workSummary")
        SortColumn.WORK_COMMENTS -> project.registryValue("workComments")
        else -> ""
    }
    if (column == SortColumn.CONSTRUCTION_TYPE) {
        // Construction types are a small, closed set.  Keep their visual
        // treatment consistent with status and sector values instead of
        // rendering an easily overlooked plain-text value in a wide table.
        Box(Modifier.width(width)) {
            ConstructionTypeChip(project.constructionType, fontWeight)
        }
    } else if (column == SortColumn.PRIORITY) {
        // Priority areas are controlled programme categories as well.  A chip
        // makes the value scannable without making it look like an action.
        Box(Modifier.width(width)) {
            OmsBadge(value, Primary, fontWeight)
        }
    } else if (column == SortColumn.WORK_STATUS) {
        // The construction-work status is a programme state, not free-form
        // prose.  Render it consistently with the other registry categories,
        // while colour-coding the state so delayed and completed work can be
        // scanned without opening the details card.
        Box(Modifier.width(width)) {
            OmsBadge(value, constructionWorkStatusColor(value), fontWeight)
        }
    } else if (link.isNotBlank()) {
        Text(
            value,
            modifier = Modifier.width(width).clickable { window.open(link, "_blank") }
                .pointerHoverIcon(PointerIcon.Hand),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = fontWeight
        )
    } else {
        ExpandableTableText(
            value,
            modifier = Modifier.width(width),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = fontWeight),
            color = if (isSubproject) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun Project.budgetLabel(): String {
    return "${budgetDisplayAmount ?: budgetPlanned} $budgetCurrency"
}

private fun Int.trancheLabel(): String = when (this) {
    1, 8 -> "A"
    2, 9 -> "B"
    else -> toString()
}

private fun Project.matchesTranche(filter: Int?): Boolean = when (filter) {
    null -> true
    1 -> trancheNumber == 1 || trancheNumber == 8
    2 -> trancheNumber == 2 || trancheNumber == 9
    else -> trancheNumber == filter
}

private fun Project.matchesProjectSearch(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
        siteNumber.contains(query, ignoreCase = true) ||
        region.contains(query, ignoreCase = true) ||
        city.contains(query, ignoreCase = true)

private fun constructionWorkStatusColor(status: String): Color {
    val normalized = status.lowercase().replace('_', ' ').trim()
    return when {
        normalized.contains("completed") || normalized.contains("заверш") || normalized.contains("виконано") -> oms.theme.OmsColors.Success
        normalized.contains("delay") || normalized.contains("затрим") || normalized.contains("проблем") -> Color(0xFFC62828)
        normalized.contains("suspend") || normalized.contains("призуп") || normalized.contains("hold") -> oms.theme.OmsColors.Warning
        // The approved registry currently has a distinct "Not started" state.
        // Keep it visibly different from a neutral fallback, in-progress work
        // and a temporary suspension.
        normalized == "not started" ||
            normalized == "не розпочато" ||
            normalized.contains("not started") ||
            normalized.contains("не розпочато") ||
            normalized.contains("непочато") ||
            normalized.contains("not begun") -> Color(0xFF7B1FA2)
        normalized.contains("progress") || normalized.contains("виконан") || normalized.contains("construction") -> Primary
        normalized.contains("preparation") || normalized.contains("підготов") || normalized.contains("design") || normalized.contains("проєктув") -> Color(0xFF00838F)
        else -> Color(0xFF546E7A)
    }
}
/*
   ---------- PAGINATION ----------
*/

