package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.data.ProjectRepository
import oms.localization.LocalizationManager
import oms.map.LeafletMapView
import oms.model.Project

@Composable
fun MapScreen(
    onOpenProject: (Project) -> Unit = {}
) {
    val projects = ProjectRepository.projects

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
                Text(text = "${LocalizationManager.t("markers_count")} ${projects.size}")
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
                    projects = projects,
                    onProjectClick = { projectId ->
                        projects.find { it.id == projectId }?.let(onOpenProject)
                    }
                )
            }
        }
    }
}