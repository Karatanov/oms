package oms.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import oms.model.Project
import oms.model.ProjectStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import oms.localization.LocalizationManager

object ProjectRepository {
    private val refreshMutex = Mutex()
    private var loaded = false
    private var loadedArchiveFilter = "active"
    var loading by mutableStateOf(false)
        private set
    var projects by mutableStateOf<List<Project>>(emptyList())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /**
     * Keeps navigation between screens responsive.  Mutating flows pass
     * [force] so that their change is visible immediately; read-only screens
     * reuse the same in-memory snapshot instead of requesting projects again.
     */
    suspend fun refresh(force: Boolean = false, archive: String = "active") = refreshMutex.withLock {
        if (!force && loaded && loadedArchiveFilter == archive) return@withLock
        loading = true
        try {
            projects = OmsApiClient.projects(archive).map { api ->
                Project(
                    id = api.uuid,
                    projectType = api.projectType,
                    trancheNumber = api.trancheNumber,
                    parentProjectUuid = api.parentProjectUuid,
                    name = api.name,
                    nameEn = api.nameEn,
                    siteNumber = api.siteNumber,
                    region = api.region,
                    city = api.city,
                    cityEn = api.cityEn,
                    sector = api.sector,
                    constructionType = api.constructionType,
                    budgetPlanned = api.budgetPlanned,
                    budgetDisplayAmount = api.amounts["budget"]?.amount,
                    budgetCurrency = api.amounts["budget"]?.currency ?: api.currency,
                    contractorName = api.contractorName,
                    startDate = api.startDate,
                    status = api.status.toProjectStatus(),
                    latitude = api.latitude,
                    longitude = api.longitude,
                    isArchived = api.isArchived
                )
            }
            loaded = true
            loadedArchiveFilter = archive
            errorMessage = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            errorMessage = LocalizationManager.t("load_records_error")
        } finally {
            loading = false
        }
    }

    fun clear() {
        loaded = false
        loadedArchiveFilter = "active"
        projects = emptyList()
        errorMessage = null
    }
}

private fun String.toProjectStatus(): ProjectStatus =
    runCatching { ProjectStatus.valueOf(uppercase()) }.getOrDefault(ProjectStatus.PLANNED)
