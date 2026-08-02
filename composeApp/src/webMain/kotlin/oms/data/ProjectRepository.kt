package oms.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import oms.model.Project
import oms.model.ProjectStatus

object ProjectRepository {
    var projects by mutableStateOf<List<Project>>(emptyList())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    suspend fun refresh() {
        try {
            projects = OmsApiClient.projects().map { api ->
                Project(
                    id = api.uuid,
                    name = api.name,
                    region = api.region,
                    status = api.status.toProjectStatus(),
                    latitude = api.latitude,
                    longitude = api.longitude
                )
            }
            errorMessage = null
        } catch (_: Exception) {
            errorMessage = "Unable to load projects from OMS API."
        }
    }
}

private fun String.toProjectStatus(): ProjectStatus =
    runCatching { ProjectStatus.valueOf(uppercase()) }.getOrDefault(ProjectStatus.PLANNING)
