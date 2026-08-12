package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import oms.components.toOmsDate
import oms.components.ConstructionTypeChip

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
            (searchText.isBlank() || project.name.contains(searchText, true) || project.siteNumber.contains(searchText, true)) &&
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
    var expandedParentIds by remember { mutableStateOf<Set<String>?>(null) }

    val sortedProjects = remember(projects, sortColumn, ascending, expandedParentIds) {
        fun sort(items: List<Project>): List<Project> = items.sortedWith { left, right ->
            val comparison = when (sortColumn) {
                SortColumn.ID -> compareBusinessIds(left.siteNumber, right.siteNumber)
                else -> sortKey(left, sortColumn).compareTo(sortKey(right, sortColumn), ignoreCase = true)
            }
            if (ascending) comparison else -comparison
        }

        val subprojectsByParent = projects
            .filter { it.projectType.equals("subproject", ignoreCase = true) }
            .groupBy { it.parentProjectUuid }
        val parents = sort(projects.filter { !it.projectType.equals("subproject", ignoreCase = true) })
        val expanded = expandedParentIds ?: parents.map { it.id }.toSet()
        buildList {
            parents.forEach { parent ->
                val children = sort(subprojectsByParent[parent.id].orEmpty())
                add(ProjectTreeRow(parent, false, false, children.size, parent.id in expanded))
                if (parent.id in expanded) children.forEachIndexed { index, child ->
                    add(ProjectTreeRow(child, true, index == children.lastIndex))
                }
            }
            addAll(sort(subprojectsByParent[null].orEmpty()).map { ProjectTreeRow(it, true, true) })
        }
    }

    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {

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
                    isSubproject = row.isSubproject,
                    isLastSubproject = row.isLastSubproject,
                    childCount = row.childCount,
                    expanded = row.expanded,
                    onToggleChildren = { id ->
                        val childParentIds = projects.filter { it.projectType.equals("subproject", true) }.mapNotNull { it.parentProjectUuid }.toSet()
                        val parentIds = projects.filter { !it.projectType.equals("subproject", true) && it.id in childParentIds }.map { it.id }.toSet()
                        val current = expandedParentIds ?: parentIds
                        expandedParentIds = if (id in current) current - id else current + id
                    },
                    onOpen = onOpenProject,
                    onEdit = onEditProject,
                    onDelete = onDeleteProject
                )

            }
        }
    }
}

private data class ProjectTreeRow(
    val project: Project,
    val isSubproject: Boolean,
    val isLastSubproject: Boolean = false,
    val childCount: Int = 0,
    val expanded: Boolean = true
)

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
    childCount: Int = 0,
    expanded: Boolean = true,
    onToggleChildren: (String) -> Unit = {},

    onOpen: (Project) -> Unit = {},
    onEdit: (Project) -> Unit = {},
    onDelete: (Project) -> Unit = {}

) {

    var hovered by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .widthIn(min = 1_250.dp)

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

        if (childCount > 0) {
            Box(
                modifier = Modifier.width(30.dp).height(32.dp).clickable { onToggleChildren(project.id) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(2.dp)
                        .height(8.dp)
                        .background(Primary.copy(alpha = 0.65f))
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Primary, MaterialTheme.shapes.extraLarge),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (expanded) "−" else "+", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

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

        Text(project.siteNumber, modifier = Modifier.width(130.dp), fontWeight = FontWeight.Medium)
        Text(project.trancheNumber.toString(), modifier = Modifier.width(90.dp))

        Text(
            project.name,
            modifier = Modifier.width(230.dp),
            fontWeight = if (isSubproject) FontWeight.Normal else FontWeight.SemiBold,
            color = if (isSubproject) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )

        Text(project.region, modifier = Modifier.width(160.dp))

        Text(project.city, modifier = Modifier.width(130.dp))
        Text(project.sector, modifier = Modifier.width(130.dp))
        Box(modifier = Modifier.width(180.dp)) { ConstructionTypeChip(project.constructionType) }

        Box(
            modifier = Modifier.width(140.dp)
        ) {
            StatusChip(project.status)
        }

        Text(project.budgetPlanned.toString(), modifier = Modifier.width(120.dp))
        Text(project.startDate.toOmsDate(), modifier = Modifier.width(120.dp))
        Text(project.contractorName.orEmpty(), modifier = Modifier.width(150.dp))

        TableActionIconButton(LocalizationManager.t("view"), Icons.Default.Visibility) { onOpen(project) }
        TableActionIconButton(LocalizationManager.t("edit"), Icons.Default.Edit) { onEdit(project) }
        TableActionIconButton("Delete project", Icons.Default.Delete) { onDelete(project) }
    }

    HorizontalDivider()
}
/*
   ---------- PAGINATION ----------
*/

