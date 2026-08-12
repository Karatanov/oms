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
                    projectType = api.projectType,
                    trancheNumber = api.trancheNumber,
                    parentProjectUuid = api.parentProjectUuid,
                    name = api.name,
                    siteNumber = api.siteNumber,
                    region = api.region,
                    city = api.city,
                    sector = api.sector,
                    constructionType = api.constructionType,
                    budgetPlanned = api.budgetPlanned,
                    contractorName = api.contractorName,
                    startDate = api.startDate,
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
