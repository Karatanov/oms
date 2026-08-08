package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import oms.components.FilterDropdown
import oms.components.StatusChip
import oms.components.TableHeader
import oms.components.TableActionIconButton
import oms.data.ProjectRepository
import oms.localization.LocalizationManager
import oms.model.Project
import oms.model.ProjectStatus
import oms.theme.Primary

// ... existing code ...

@Composable
fun ProjectsScreen(
    onOpenProject: (Project) -> Unit = {},
    onCreateProject: () -> Unit = {},
    onEditProject: (Project) -> Unit = {}
) {

    var searchText by remember { mutableStateOf("") }
    var regionFilter by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf<ProjectStatus?>(null) }
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { ProjectRepository.refresh() }
    val projects = ProjectRepository.projects

    val filteredProjects = remember(projects, searchText, regionFilter, statusFilter) {
        fun matches(project: Project) =
            (searchText.isBlank() || project.name.contains(searchText, true)) &&
                (regionFilter == null || project.region == regionFilter) &&
                (statusFilter == null || project.status == statusFilter)
        val matchingProjects = projects.filter(::matches)
        val visibleParentIds = matchingProjects.mapNotNull { it.parentProjectUuid }.toSet()
        matchingProjects + projects.filter { it.id in visibleParentIds && it !in matchingProjects }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                LocalizationManager.t("projects_title"),
                style = MaterialTheme.typography.headlineMedium
            )
            Button(onClick = onCreateProject) { Text("Створити проєкт") }
        }

        Spacer(Modifier.height(16.dp))

        ProjectsFilters(
            searchText = searchText,
            onSearchChange = { searchText = it },
            regionFilter = regionFilter,
            onRegionChange = { regionFilter = it },
            statusFilter = statusFilter,
            onStatusChange = { statusFilter = it }
        )

        Spacer(Modifier.height(16.dp))

        ProjectsTable(
            projects = filteredProjects,
            onOpenProject = onOpenProject,
            onEditProject = onEditProject,
            onDeleteProject = { project ->
                scope.launch {
                    if (oms.data.OmsApiClient.deleteProject(project.id)) ProjectRepository.refresh()
                    else errorMessage = "Could not delete project."
                }
            }
        )

        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Spacer(Modifier.height(16.dp))

        //Pagination()
    }
}

// ... existing code ...

@Composable
fun ProjectsTable(
    projects: List<Project>,
    onOpenProject: (Project) -> Unit,
    onEditProject: (Project) -> Unit,
    onDeleteProject: (Project) -> Unit
) {

    var sortColumn by remember { mutableStateOf(SortColumn.NAME) }
    var ascending by remember { mutableStateOf(true) }

    val sortedProjects = remember(projects, sortColumn, ascending) {
        fun sort(items: List<Project>): List<Project> {
            val list = when (sortColumn) {
                SortColumn.ID -> items.sortedBy { it.id }
                SortColumn.NAME -> items.sortedBy { it.name }
                SortColumn.REGION -> items.sortedBy { it.region }
                SortColumn.STATUS -> items.sortedBy { it.status }
            }
            return if (ascending) list else list.reversed()
        }

        val subprojectsByParent = projects
            .filter { it.projectType.equals("subproject", ignoreCase = true) }
            .groupBy { it.parentProjectUuid }
        buildList {
            sort(projects.filter { !it.projectType.equals("subproject", ignoreCase = true) }).forEach { parent ->
                add(parent)
                addAll(sort(subprojectsByParent[parent.id].orEmpty()))
            }
            addAll(sort(subprojectsByParent[null].orEmpty()))
        }
    }

    Column {

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

            sortedProjects.forEach {

                ProjectRow(
                    project = it,
                    isSubproject = it.projectType.equals("subproject", ignoreCase = true),
                    onOpen = onOpenProject,
                    onEdit = onEditProject,
                    onDelete = onDeleteProject
                )

            }
        }
    }
}

/*
   ---------- FILTERS ----------
*/

@Composable
fun ProjectsFilters(
    searchText: String,
    onSearchChange: (String) -> Unit,
    regionFilter: String?,
    onRegionChange: (String?) -> Unit,
    statusFilter: ProjectStatus?,
    onStatusChange: (ProjectStatus?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            label = { Text(LocalizationManager.t("search_project")) },
            modifier = Modifier.weight(1f)
        )

        FilterDropdown(
            label = LocalizationManager.t("region"),
            options = listOf("Kyiv", "Lviv", "Odesa"),
            selected = regionFilter,
            onSelect = onRegionChange,
            itemLabel = { region ->
                when (region) {
                    "Kyiv" -> "Kyiv"
                    "Lviv" -> "Lviv"
                    "Odesa" -> "Odesa"
                    else -> region
                }
            }
        )

        FilterDropdown(
            label = LocalizationManager.t("status"),
            options = ProjectStatus.entries,
            selected = statusFilter,
            onSelect = onStatusChange,
            itemLabel = { status ->
                when (status) {
                    ProjectStatus.ACTIVE -> LocalizationManager.t("active")
                    ProjectStatus.PLANNING -> LocalizationManager.t("planning")
                    ProjectStatus.COMPLETED -> LocalizationManager.t("completed")
                }
            }
        )
    }
}


enum class SortColumn {

    ID,

    NAME,

    REGION,

    STATUS
}

/*
   ---------- TABLE ROW ----------
*/
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProjectRow(

    project: Project,

    isSubproject: Boolean = false,

    onOpen: (Project) -> Unit = {},
    onEdit: (Project) -> Unit = {},
    onDelete: (Project) -> Unit = {}

) {

    var hovered by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()

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
                if (hovered)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                else
                    Color.Transparent
            )

            .padding(vertical = 10.dp)
    ) {

        if (isSubproject) {
            Box(modifier = Modifier.width(30.dp).height(32.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(2.dp)
                        .height(18.dp)
                        .background(Primary.copy(alpha = 0.55f))
                )
                Text("↳", color = Primary, style = MaterialTheme.typography.titleMedium)
            }
        }

        Text(
            project.name,
            modifier = Modifier.weight(1f),
            fontWeight = if (isSubproject) FontWeight.Normal else FontWeight.SemiBold,
            color = if (isSubproject) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )

        Text(project.region, modifier = Modifier.width(160.dp))

        Box(
            modifier = Modifier.width(140.dp)
        ) {
            StatusChip(project.status)
        }

        TableActionIconButton(LocalizationManager.t("view"), Icons.Default.Visibility) { onOpen(project) }
        TableActionIconButton(LocalizationManager.t("edit"), Icons.Default.Edit) { onEdit(project) }
        TableActionIconButton("Delete project", Icons.Default.Delete) { onDelete(project) }
    }

    HorizontalDivider()
}
/*
   ---------- PAGINATION ----------
*/

