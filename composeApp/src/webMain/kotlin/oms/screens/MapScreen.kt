package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
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

@Composable
fun MapScreen(
    onOpenProject: (Project) -> Unit = {}
) {
    LaunchedEffect(Unit) { ProjectRepository.refresh() }
    val projects = ProjectRepository.projects
    var search by remember { mutableStateOf("") }
    var region by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<ProjectStatus?>(null) }
    val visibleProjects = remember(projects, search, region, status) {
        projects.filter { project ->
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
                .height(160.dp)
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(search, { search = it }, label = { Text(LocalizationManager.t("search")) }, modifier = Modifier.weight(1f))
                    FilterDropdown(LocalizationManager.t("region"), projects.map { it.region }.distinct().sorted(), region, { region = it })
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
