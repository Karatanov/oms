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

    /**
     * Keeps navigation between screens responsive.  Mutating flows pass
     * [force] so that their change is visible immediately; read-only screens
     * reuse the same in-memory snapshot instead of requesting projects again.
     */
    suspend fun refresh(force: Boolean = false) {
        if (!force && projects.isNotEmpty()) return
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

    fun clear() {
        projects = emptyList()
        errorMessage = null
    }
}

private fun String.toProjectStatus(): ProjectStatus =
    runCatching { ProjectStatus.valueOf(uppercase()) }.getOrDefault(ProjectStatus.PLANNED)
