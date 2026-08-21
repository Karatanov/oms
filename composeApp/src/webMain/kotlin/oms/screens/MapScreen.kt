package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.data.ProjectRepository
import oms.components.FilterDropdown
import oms.localization.LocalizationManager
import oms.map.LeafletMapView
import oms.model.Project
import oms.model.ProjectStatus

private enum class MapProjectScope(val projectType: String, val labelKey: String) {
    Subprojects("subproject", "map_subprojects"),
    SubprojectParts("subproject_part", "map_subproject_parts")
}

@Composable
fun MapScreen(
    onOpenProject: (Project) -> Unit = {}
) {
    LaunchedEffect(Unit) { ProjectRepository.refresh() }
    val projects = ProjectRepository.projects
    var search by remember { mutableStateOf("") }
    var region by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<ProjectStatus?>(null) }
    var scope by remember { mutableStateOf(MapProjectScope.Subprojects) }
    val mapProjects = remember(projects, scope) {
        projects.filter { project ->
            project.projectType.equals(scope.projectType, ignoreCase = true) &&
                (project.latitude != 0.0 || project.longitude != 0.0)
        }
    }
    val visibleProjects = remember(mapProjects, search, region, status) {
        mapProjects.filter { project ->
            (search.isBlank() || project.name.contains(search, ignoreCase = true)) &&
                (region == null || project.region == region) &&
                (status == null || project.status == status)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = LocalizationManager.t("projects_map"),
            style = MaterialTheme.typography.headlineMedium
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = LocalizationManager.t("map_controls"),
                    style = MaterialTheme.typography.titleMedium
                )

                Text(text = LocalizationManager.t("map_projects_info"))
                Text(text = LocalizationManager.t("map_marker_info"))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(search, { search = it }, label = { Text(LocalizationManager.t("search")) }, modifier = Modifier.weight(1f))
                            FilterDropdown(LocalizationManager.t("region"), mapProjects.map { it.region }.filter { it.isNotBlank() }.distinct().sorted(), region, { region = it })
                            FilterDropdown(
                                LocalizationManager.t("status"),
                                ProjectStatus.entries,
                                status,
                                { status = it },
                                itemLabel = { LocalizationManager.t("project_status_${it.name.lowercase()}") }
                            )
                        }
                        Text(text = "${LocalizationManager.t("markers_count")} ${visibleProjects.size}")
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(LocalizationManager.t("map_scope"), style = MaterialTheme.typography.labelLarge)
                        MapProjectScope.entries.forEach { option ->
                            FilterChip(
                                selected = scope == option,
                                onClick = { scope = option; region = null },
                                label = { Text(LocalizationManager.t(option.labelKey)) }
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                LeafletMapView(
                    projects = visibleProjects,
                    onProjectClick = { projectId ->
                        visibleProjects.find { it.id == projectId }?.let(onOpenProject)
                    }
                )
            }
        }
    }
}
