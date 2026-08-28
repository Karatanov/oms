package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import oms.components.StatusChip
import oms.components.TableHeader
import oms.components.ProjectTableColumns
import oms.components.TableActionIconButton
import oms.data.ProjectRepository
import oms.localization.LocalizationManager
import oms.model.Project
import oms.model.ProjectStatus
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

// ... existing code ...

@Composable
fun ProjectsScreen(
    onOpenProject: (Project) -> Unit = {},
    onCreateProject: () -> Unit = {},
    onEditProject: (Project) -> Unit = {},
    canManageProjects: Boolean = false,
    canBulkReassign: Boolean = false
) {

    val deletion = oms.components.LocalDeleteConfirmation.current
    var searchText by remember { mutableStateOf("") }
    var regionFilter by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf<ProjectStatus?>(null) }
    var constructionTypeFilter by remember { mutableStateOf<String?>(null) }
    var sectorFilter by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedProjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var managers by remember { mutableStateOf<List<ApiUser>>(emptyList()) }
    var showReassign by remember { mutableStateOf(false) }
    val pageScrollState = rememberScrollState()

    LaunchedEffect(showReassign) {
        if (showReassign) pageScrollState.animateScrollTo(pageScrollState.maxValue)
    }

    LaunchedEffect(Unit) {
        coroutineScope {
            val refreshProjects = async { ProjectRepository.refresh() }
            val loadManagers = async {
                if (canBulkReassign) runCatching { oms.data.OmsApiClient.users() }
                    .getOrDefault(emptyList())
                    .filter { it.role.code == "PROJECT_MANAGER" && it.status == "active" }
                else emptyList()
            }
            refreshProjects.await()
            managers = loadManagers.await()
        }
    }
    val projects = ProjectRepository.projects

    val filteredProjects = remember(projects, searchText, regionFilter, statusFilter, constructionTypeFilter, sectorFilter) {
        fun matches(project: Project) =
            (searchText.isBlank() ||
                project.name.contains(searchText, true) ||
                project.siteNumber.contains(searchText, true) ||
                project.region.contains(searchText, true) ||
                project.city.contains(searchText, true)) &&
                (regionFilter == null || project.region == regionFilter) &&
                (statusFilter == null || project.status == statusFilter) &&
                (constructionTypeFilter == null || project.constructionType.equals(constructionTypeFilter, ignoreCase = true)) &&
                (sectorFilter == null || project.sector.equals(sectorFilter, ignoreCase = true))
        val matchingProjects = projects.filter(::matches)
        val projectsById = projects.associateBy { it.id }
        val visibleIds = matchingProjects
            .flatMap { project ->
                generateSequence(project) { current -> current.parentProjectUuid?.let(projectsById::get) }.toList()
            }
            .mapTo(mutableSetOf()) { it.id }
        projects.filter { it.id in visibleIds }
    }

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onPrimary = Color.White)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(pageScrollState)
            .padding(16.dp)
    ) {

        oms.components.PageHeading(LocalizationManager.t("projects_title"), Icons.Default.FolderOpen) {
            if (canManageProjects) Button(onClick = onCreateProject) { Text(LocalizationManager.t("create_project")) }
        }

        Spacer(Modifier.height(16.dp))
        if (ProjectRepository.loading) oms.components.ContentState(LocalizationManager.t("loading_records"), loading = true)
        ProjectRepository.errorMessage?.let { oms.components.ContentState(it, error = true, onRetry = { scope.launch { ProjectRepository.refresh(force = true) } }) }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text(LocalizationManager.t("search_project")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            enabled = searchText.isNotBlank() || regionFilter != null || statusFilter != null || constructionTypeFilter != null || sectorFilter != null,
            onClick = { searchText = ""; regionFilter = null; statusFilter = null; constructionTypeFilter = null; sectorFilter = null }
        ) { Text(LocalizationManager.t("reset_filters")) }
        if (canManageProjects && selectedProjectIds.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(LocalizationManager.t("selected_projects").replace("{count}", selectedProjectIds.size.toString()), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { selectedProjectIds = emptySet() }) { Text(LocalizationManager.t("clear_selection")) }
                    OutlinedButton(onClick = { window.open(oms.data.OmsApiClient.projectExportUrl(selectedProjectIds), "_blank") }) { Text("XLSX") }
                    if (canBulkReassign) Button(onClick = { showReassign = true }, enabled = managers.isNotEmpty()) { Text(LocalizationManager.t("reassign")) }
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

        Spacer(Modifier.height(16.dp))

        ProjectsTable(
            projects = filteredProjects,
            searchText = searchText,
            filters = {
                ProjectsFilters(regionFilter, { regionFilter = it }, statusFilter, { statusFilter = it },
                    constructionTypeFilter, { constructionTypeFilter = it }, sectorFilter, { sectorFilter = it })
            },
            onOpenProject = onOpenProject,
            onEditProject = onEditProject,
            onDeleteProject = { project ->
                deletion.show(project.name) { scope.launch {
                    if (oms.data.OmsApiClient.deleteProject(project.id)) ProjectRepository.refresh(force = true)
                    else errorMessage = LocalizationManager.t("error_delete_project")
                } }
            },
            canManageProjects = canManageProjects,
            onExpandRow = { rowTop -> scope.launch { pageScrollState.animateScrollTo((pageScrollState.value + rowTop - 16f).toInt().coerceAtLeast(0)) } },
            selectedProjectIds = selectedProjectIds,
            onSelectionChange = { id, selected -> selectedProjectIds = if (selected) selectedProjectIds + id else selectedProjectIds - id }
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
    }
}

// ... existing code ...

@Composable
fun ProjectsTable(
    projects: List<Project>,
    searchText: String,
    filters: @Composable () -> Unit,
    onOpenProject: (Project) -> Unit,
    onEditProject: (Project) -> Unit,
    onDeleteProject: (Project) -> Unit,
    canManageProjects: Boolean,
    onExpandRow: (Float) -> Unit,
    selectedProjectIds: Set<String>,
    onSelectionChange: (String, Boolean) -> Unit
) {

    var sortColumn by remember { mutableStateOf(SortColumn.NAME) }
    var ascending by remember { mutableStateOf(true) }
    var expandedParentIds by remember { mutableStateOf<Set<String>?>(null) }

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
        val parents = sort(projects.filter { it.projectType.equals("project", ignoreCase = true) })
        val expanded = expandedParentIds ?: parents.map { it.id }.toSet()
        buildList {
            fun addBranch(item: Project, depth: Int, isLast: Boolean) {
                val children = sort(childrenByParent[item.id].orEmpty())
                val isExpanded = item.id in expanded
                add(ProjectTreeRow(item, depth, isLast, children.size, isExpanded))
                if (isExpanded) children.forEachIndexed { index, child ->
                    addBranch(child, depth + 1, index == children.lastIndex)
                }
            }
            parents.forEachIndexed { index, parent -> addBranch(parent, 0, index == parents.lastIndex) }
            sort(childrenByParent[null].orEmpty()).forEach { addBranch(it, 1, true) }
        }
    }

    oms.components.ScrollableTable {
        filters()
        TableHeader(
            sortColumn,
            ascending
        ) { column ->

            if (sortColumn == column)
                ascending = !ascending
            else {
                sortColumn = column
                ascending = true
            }
        }

        HorizontalDivider()

        Column(modifier = Modifier.fillMaxWidth()) {

            sortedProjects.forEach { row ->

                ProjectRow(
                    project = row.project,
                    highlightSearchMatch = searchText.isNotBlank() && row.project.matchesProjectSearch(searchText),
                    isSubproject = row.depth > 0,
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
                    isSelected = row.project.id in selectedProjectIds,
                    onSelectedChange = { onSelectionChange(row.project.id, it) }
                )

            }
        }
    }
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
    constructionTypeFilter: String?,
    onConstructionTypeChange: (String?) -> Unit,
    sectorFilter: String?,
    onSectorChange: (String?) -> Unit
) {
    Row(Modifier.width(ProjectTableColumns.totalWidth).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(ProjectTableColumns.selection + ProjectTableColumns.hierarchy))
        ProjectTableColumns.columns.forEach { column ->
            Box(Modifier.width(ProjectTableColumns.width(column)).padding(end = 6.dp)) {
                when (column) {
                    SortColumn.NAME -> Text(LocalizationManager.t("filters"), style = MaterialTheme.typography.labelLarge)
                    SortColumn.REGION -> ProjectFilterDropdown(
                        LocalizationManager.t("region"),
                        ProjectRepository.projects.map { it.region }.filter { it.isNotBlank() }.distinct().sorted(),
                        regionFilter, onRegionChange)
                    SortColumn.SECTOR -> ProjectFilterDropdown(
                        LocalizationManager.t("sector"), sectors, sectorFilter, onSectorChange, String::sectorLabel)
                    SortColumn.CONSTRUCTION_TYPE -> ProjectFilterDropdown(
                        LocalizationManager.t("construction_type"), constructionTypes, constructionTypeFilter,
                        onConstructionTypeChange, String::constructionTypeLabel)
                    SortColumn.STATUS -> ProjectFilterDropdown(
                        LocalizationManager.t("status"), ProjectStatus.entries, statusFilter, onStatusChange,
                        { LocalizationManager.t("project_status_${it.name.lowercase()}") })
                    else -> Unit
                }
            }
        }
        Spacer(Modifier.width(ProjectTableColumns.actions))
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
    ID, TRANCHE, NAME, REGION, CITY, SECTOR, CONSTRUCTION_TYPE, STATUS, BUDGET, START_DATE, CONTRACTOR
}

private fun sortKey(project: Project, column: SortColumn): String = when (column) {
    SortColumn.ID -> project.siteNumber
    SortColumn.TRANCHE -> project.trancheNumber.toString().padStart(10, '0')
    SortColumn.NAME -> project.name
    SortColumn.REGION -> project.region
    SortColumn.CITY -> project.city
    SortColumn.SECTOR -> project.sector
    SortColumn.CONSTRUCTION_TYPE -> project.constructionType
    SortColumn.STATUS -> project.status.name
    SortColumn.BUDGET -> project.budgetPlanned.toString().padStart(20, '0')
    SortColumn.START_DATE -> project.startDate.orEmpty()
    SortColumn.CONTRACTOR -> project.contractorName.orEmpty()
}

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
    highlightSearchMatch: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {}

) {

    var hovered by remember { mutableStateOf(false) }
    var rowTopInRoot by remember { mutableStateOf(0f) }

    Row(
        modifier = Modifier
            .width(ProjectTableColumns.totalWidth)
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
            )

            .padding(vertical = 10.dp),
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
                modifier = Modifier.fillMaxSize().clickable { onToggleChildren(project.id); onExpandRow(rowTopInRoot) },
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
                if (indentLevel > 1) {
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
                } else {
                    Text("•", color = Primary, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Text(project.siteNumber, modifier = Modifier.width(ProjectTableColumns.width(SortColumn.ID)), fontWeight = rowFontWeight)
        Text(project.trancheNumber.toString(), modifier = Modifier.width(ProjectTableColumns.width(SortColumn.TRANCHE)), fontWeight = rowFontWeight)

        Text(
            project.name,
            modifier = Modifier.width(ProjectTableColumns.width(SortColumn.NAME)),
            fontWeight = rowFontWeight,
            color = if (isSubproject) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )

        Text(project.region, modifier = Modifier.width(ProjectTableColumns.width(SortColumn.REGION)), fontWeight = rowFontWeight)

        Text(project.city, modifier = Modifier.width(ProjectTableColumns.width(SortColumn.CITY)), fontWeight = rowFontWeight)
        Box(modifier = Modifier.width(ProjectTableColumns.width(SortColumn.SECTOR))) { SectorChip(project.sector, rowFontWeight) }
        Box(modifier = Modifier.width(ProjectTableColumns.width(SortColumn.CONSTRUCTION_TYPE))) { ConstructionTypeChip(project.constructionType, rowFontWeight) }

        Box(
            modifier = Modifier.width(ProjectTableColumns.width(SortColumn.STATUS))
        ) {
            StatusChip(project.status, rowFontWeight)
        }

        Text(project.budgetPlanned.toString(), modifier = Modifier.width(ProjectTableColumns.width(SortColumn.BUDGET)), fontWeight = rowFontWeight)
        Text(project.startDate.toOmsDate(), modifier = Modifier.width(ProjectTableColumns.width(SortColumn.START_DATE)), fontWeight = rowFontWeight)
        Text(project.contractorName.orEmpty(), modifier = Modifier.width(ProjectTableColumns.width(SortColumn.CONTRACTOR)), fontWeight = rowFontWeight)

        Row(Modifier.width(ProjectTableColumns.actions), verticalAlignment = Alignment.CenterVertically) {
        TableActionIconButton(LocalizationManager.t("view"), Icons.Default.Visibility) { onOpen(project) }
        if (canManageProjects) {
            TableActionIconButton(LocalizationManager.t("edit"), Icons.Default.Edit) { onEdit(project) }
            TableActionIconButton(LocalizationManager.t("delete_project"), Icons.Default.Delete) { onDelete(project) }
        }
        }
    }

    HorizontalDivider()
}

private fun Project.matchesProjectSearch(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
        siteNumber.contains(query, ignoreCase = true) ||
        region.contains(query, ignoreCase = true) ||
        city.contains(query, ignoreCase = true)
/*
   ---------- PAGINATION ----------
*/

