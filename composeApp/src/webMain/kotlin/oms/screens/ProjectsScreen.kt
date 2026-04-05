package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import oms.components.FilterDropdown
import oms.components.StatusChip
import oms.components.TableHeader
import oms.data.ProjectRepository.projects
import oms.localization.LocalizationManager
import oms.model.Project
import oms.model.ProjectStatus

// ... existing code ...

@Composable
fun ProjectsScreen(
    onOpenProject: (Project) -> Unit = {}
) {

    var searchText by remember { mutableStateOf("") }
    var regionFilter by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf<ProjectStatus?>(null) }

    val projects = projects

    val filteredProjects = remember(searchText, regionFilter, statusFilter) {
        projects.filter {
            (searchText.isBlank() || it.name.contains(searchText, true)) &&
                    (regionFilter == null || it.region == regionFilter) &&
                    (statusFilter == null || it.status == statusFilter)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            LocalizationManager.t("projects_title"),
            style = MaterialTheme.typography.headlineMedium
        )

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
            onOpenProject = onOpenProject
        )

        Spacer(Modifier.height(16.dp))

        //Pagination()
    }
}

// ... existing code ...

@Composable
fun ProjectsTable(
    projects: List<Project>,
    onOpenProject: (Project) -> Unit
) {

    var sortColumn by remember { mutableStateOf(SortColumn.ID) }
    var ascending by remember { mutableStateOf(true) }

    val sortedProjects = remember(projects, sortColumn, ascending) {

        val list = when (sortColumn) {

            SortColumn.ID -> projects.sortedBy { it.id }

            SortColumn.NAME -> projects.sortedBy { it.name }

            SortColumn.REGION -> projects.sortedBy { it.region }

            SortColumn.STATUS -> projects.sortedBy { it.status }
        }

        if (ascending) list else list.reversed()
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {

            sortedProjects.forEach {

                ProjectRow(
                    project = it,
                    onOpen = onOpenProject
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

    onOpen: (Project) -> Unit = {}

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

        Text(project.id.toString(), modifier = Modifier.width(80.dp))

        Text(project.name, modifier = Modifier.weight(1f))

        Text(project.region, modifier = Modifier.width(160.dp))

        Box(
            modifier = Modifier.width(140.dp)
        ) {
            StatusChip(project.status)
        }

        Button(
            onClick = { onOpen(project) },
            modifier = Modifier.width(100.dp)
        ) {
            Text(LocalizationManager.t("view"))
        }
    }

    HorizontalDivider()
}
/*
   ---------- PAGINATION ----------
*/

