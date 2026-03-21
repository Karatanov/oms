package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import oms.model.Project
import oms.components.StatusChip
import oms.data.ProjectRepository.projects

/*
   Projects screen.

   Реалізує:
   - таблицю проектів
   - пошук
   - фільтри
   - pagination
*/

@Composable
fun ProjectsScreen() {

    var searchText by remember { mutableStateOf("") }

    val projects = projects

    val filteredProjects = remember(searchText) {

        projects.filter {

            it.name.contains(searchText, true) ||
                    it.region.contains(searchText, true)
            //  it.status.contains(searchText, true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            "Projects",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(16.dp))

        ProjectsFilters(
            searchText = searchText,
            onSearchChange = { searchText = it }
        )

        Spacer(Modifier.height(16.dp))

        ProjectsTable(filteredProjects)

        Spacer(Modifier.height(16.dp))

        Pagination()
    }
}
/*
   ---------- FILTERS ----------
*/

@Composable
fun ProjectsFilters(
    searchText: String,
    onSearchChange: (String) -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            label = { Text("Search project") },
            modifier = Modifier.weight(1f)
        )

        FilterDropdown("Region")

        FilterDropdown("Status")
    }
}

/*
   Простий dropdown фільтр
*/

@Composable
fun FilterDropdown(label: String) {

    var expanded by remember { mutableStateOf(false) }

    Box {

        Button(
            onClick = { expanded = true }
        ) {
            Text(label)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {

            DropdownMenuItem(
                text = { Text("All") },
                onClick = { expanded = false }
            )

            DropdownMenuItem(
                text = { Text("Option 1") },
                onClick = { expanded = false }
            )

            DropdownMenuItem(
                text = { Text("Option 2") },
                onClick = { expanded = false }
            )
        }
    }
}

enum class SortColumn {

    ID,

    NAME,

    REGION,

    STATUS
}

/*
   ---------- TABLE ----------
*/
@Composable
fun ProjectsTable(

    projects: List<Project>

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

        /*
           Scroll container.
           Таблиця може мати сотні рядків.
        */

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {

            sortedProjects.forEach {

                ProjectRow(
                    project = it,
                    onOpen = { project ->
                        println("Open project: ${project.id}")
                    }
                )

            }
        }
    }
}

@Composable
fun SortableHeader(
    title: String,
    column: SortColumn,
    currentSort: SortColumn,
    ascending: Boolean,
    onSort: (SortColumn) -> Unit,
    modifier: Modifier
) {

    TextButton(
        onClick = { onSort(column) },
        modifier = modifier
    ) {

        Row {

            Text(title)

            if (currentSort == column) {

                Spacer(Modifier.width(4.dp))

                Icon(
                    imageVector =
                        if (ascending)
                            Icons.Default.ArrowUpward
                        else
                            Icons.Default.ArrowDownward,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun TableHeader(

    currentSort: SortColumn,

    ascending: Boolean,

    onSort: (SortColumn) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {

        SortableHeader(
            "ID",
            SortColumn.ID,
            currentSort,
            ascending,
            onSort,
            Modifier.width(80.dp)
        )

        SortableHeader(
            "Project",
            SortColumn.NAME,
            currentSort,
            ascending,
            onSort,
            Modifier.weight(1f)
        )

        SortableHeader(
            "Region",
            SortColumn.REGION,
            currentSort,
            ascending,
            onSort,
            Modifier.width(160.dp)
        )

        SortableHeader(
            "Status",
            SortColumn.STATUS,
            currentSort,
            ascending,
            onSort,
            Modifier.width(140.dp)
        )
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(24.dp))
                Text(
                    text = "Action",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
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
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.surface
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
            Text("View")
        }
    }

    HorizontalDivider()
}
/*
   ---------- PAGINATION ----------
*/

@Composable
fun Pagination() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {

        Button(onClick = { }) { Text("<") }

        Spacer(Modifier.width(8.dp))

        Button(onClick = { }) { Text("1") }

        Spacer(Modifier.width(8.dp))

        Button(onClick = { }) { Text("2") }

        Spacer(Modifier.width(8.dp))

        Button(onClick = { }) { Text("3") }

        Spacer(Modifier.width(8.dp))

        Button(onClick = { }) { Text(">") }
    }
}