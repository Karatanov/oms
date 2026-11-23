package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import oms.data.ProjectRepository
import oms.components.*
import oms.localization.LocalizationManager
import oms.map.LeafletMapView
import oms.model.Project
import oms.model.ProjectStatus
import oms.model.localizedName
import oms.model.localizedCity
import kotlin.js.JsName
import kotlinx.coroutines.launch

@JsName("fitUkraineOverview") private external fun fitUkraineOverview()
@JsName("fitLeafletResults") private external fun fitLeafletResults()

private enum class MapScope(val projectType: String, val labelKey: String) {
    SUBPROJECTS("subproject", "map_subprojects"),
    PARTS("subproject_part", "map_subproject_parts")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onOpenProject: (Project) -> Unit = {}, requestedTrancheFilter: Int? = null) {
    var initialLoad by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { ProjectRepository.refresh(); initialLoad = false }
    oms.navigation.ReportRouteReadiness(initialLoad, ProjectRepository.errorMessage != null)
    val scope = rememberCoroutineScope()
    val projects = ProjectRepository.projects
    var search by remember { mutableStateOf("") }
    var regions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var statuses by remember { mutableStateOf<Set<ProjectStatus>>(emptySet()) }
    var mapScope by remember { mutableStateOf(MapScope.SUBPROJECTS) }
    var trancheNumber by remember { mutableStateOf(requestedTrancheFilter) }
    // Show exactly one child level, never portfolio-level projects.
    val located = projects.filter {
        it.projectType == mapScope.projectType &&
            it.matchesMapTranche(trancheNumber) &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 &&
            (it.latitude != 0.0 || it.longitude != 0.0)
    }
    val visible = located.filter { it.matchesMapFilters(search, regions, statuses) }
    val mobile = oms.layout.LocalMobileLayout.current
    var filtersExpanded by remember { mutableStateOf(!mobile) }
    val hasFilters = search.isNotBlank() || regions.isNotEmpty() || statuses.isNotEmpty() || trancheNumber != null || mapScope != MapScope.SUBPROJECTS

    @Composable fun Selectors() {
        CompactChoiceSelector(MapScope.entries.toList(), mapScope, { mapScope = it },
            itemLabel = { LocalizationManager.t(it.labelKey) })
        TrancheSelector(trancheNumber, { trancheNumber = it })
    }
    @Composable fun MapAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
        enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
        OmsTooltipBox(tooltip = { Text(label) }) {
            IconButton(onClick = onClick, enabled = enabled,
                modifier = Modifier.size(32.dp)
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, MaterialTheme.shapes.extraSmall)
                    .then(if (enabled) Modifier.buttonHandCursor() else Modifier)) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val filterHeight = (maxHeight * .4f).coerceIn(80.dp, 240.dp)
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Map, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(LocalizationManager.t("projects_map"), style = MaterialTheme.typography.titleSmall)
                            }
                            Text("${LocalizationManager.t("markers_count")} ${visible.size}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!mobile) Selectors()
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                MapAction(LocalizationManager.t("all_ukraine"), Icons.Default.Public, onClick = ::fitUkraineOverview)
                                MapAction(LocalizationManager.t("fit_results"), Icons.Default.CenterFocusStrong,
                                    enabled = visible.isNotEmpty(), onClick = ::fitLeafletResults)
                                MapAction(LocalizationManager.t("reset_filters"), Icons.Default.RestartAlt, enabled = hasFilters) {
                                    search = ""
                                    regions = emptySet()
                                    statuses = emptySet()
                                    trancheNumber = null
                                    mapScope = MapScope.SUBPROJECTS
                                    fitUkraineOverview()
                                }
                                MapAction(LocalizationManager.t("filters"),
                                    if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.FilterList,
                                    active = filtersExpanded || hasFilters) { filtersExpanded = !filtersExpanded }
                            }
                        }
                        if (filtersExpanded) Column(
                            Modifier.fillMaxWidth().heightIn(max = filterHeight).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (mobile) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) { Selectors() }
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val searchControl: @Composable (Modifier) -> Unit = { modifier ->
                                    WorkspaceSearchField(search, { search = it.safeTextInput(200) },
                                        LocalizationManager.t("search_project"), modifier)
                                }
                                val regionControl: @Composable (Modifier) -> Unit = { modifier ->
                                    MultiSelectOptionPicker(
                                        options = located.map { it.region }.filter(String::isNotBlank).distinct().sorted(),
                                        selected = regions, prompt = LocalizationManager.t("region"),
                                        onSelectionChange = { regions = it }, itemLabel = ::localizedUkraineRegion,
                                        modifier = modifier, clearLabel = LocalizationManager.t("map_clear_selection")
                                    )
                                }
                                val statusControl: @Composable (Modifier) -> Unit = { modifier ->
                                    MultiSelectOptionPicker(
                                        options = ProjectStatus.entries, selected = statuses,
                                        prompt = LocalizationManager.t("status"), onSelectionChange = { statuses = it },
                                        itemLabel = { LocalizationManager.t("project_status_${it.name.lowercase()}") },
                                        modifier = modifier, clearLabel = LocalizationManager.t("map_clear_selection")
                                    )
                                }
                                if (maxWidth >= 720.dp) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    searchControl(Modifier.weight(1f))
                                    regionControl(Modifier.width(180.dp))
                                    statusControl(Modifier.width(174.dp))
                                } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    searchControl(Modifier.fillMaxWidth())
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        regionControl(Modifier.weight(1f))
                                        statusControl(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (ProjectRepository.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            else if (ProjectRepository.errorMessage != null) ChartContentState(LocalizationManager.t("load_records_error"),
                onRetry = { scope.launch { ProjectRepository.refresh(force = true) } })
            else if (visible.isEmpty()) Text(LocalizationManager.t("no_map_results"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                LeafletMapView(visible, allProjects = projects) { id -> visible.firstOrNull { it.id == id }?.let(onOpenProject) }
            }
        }
    }
}

/** Batch IDs 8/9 are the imported representations of tranches A/B. */
private fun Project.matchesMapTranche(filter: Int?): Boolean = when (filter) {
    null -> true
    1 -> trancheNumber == 1 || trancheNumber == 8
    2 -> trancheNumber == 2 || trancheNumber == 9
    else -> trancheNumber == filter
}

/** OR applies inside each selected category; independent categories use AND. */
internal fun Project.matchesMapFilters(
    search: String,
    regions: Set<String>,
    statuses: Set<ProjectStatus>
): Boolean =
    (search.isBlank() || localizedName().contains(search, true) || siteNumber.contains(search, true) || localizedCity().contains(search, true)) &&
        (regions.isEmpty() || region in regions) &&
        (statuses.isEmpty() || status in statuses)
