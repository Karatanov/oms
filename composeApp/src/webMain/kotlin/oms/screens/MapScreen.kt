package oms.screens

import androidx.compose.foundation.layout.*
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
fun MapScreen(onOpenProject: (Project) -> Unit = {}) {
    var initialLoad by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { ProjectRepository.refresh(); initialLoad = false }
    oms.navigation.ReportRouteReadiness(initialLoad, ProjectRepository.errorMessage != null)
    val scope = rememberCoroutineScope()
    val projects = ProjectRepository.projects
    var search by remember { mutableStateOf("") }
    var regions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var statuses by remember { mutableStateOf<Set<ProjectStatus>>(emptySet()) }
    var mapScope by remember { mutableStateOf(MapScope.SUBPROJECTS) }
    var trancheNumber by remember { mutableStateOf<Int?>(null) }
    // Show exactly one child level, never portfolio-level projects.
    val located = projects.filter {
        it.projectType == mapScope.projectType &&
            it.matchesMapTranche(trancheNumber) &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 &&
            (it.latitude != 0.0 || it.longitude != 0.0)
    }
    val visible = located.filter { it.matchesMapFilters(search, regions, statuses) }
    val mobile = oms.layout.LocalMobileLayout.current
    var filtersExpanded by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Leave room for the heading/actions and a usable map, even on short phones.
    val filterHeight = (maxHeight * .5f).coerceAtMost((maxHeight - 400.dp).coerceAtLeast(80.dp))
    val shortViewport = maxHeight < 500.dp
    Column(Modifier.fillMaxSize().padding(oms.layout.workspacePadding()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mobile && shortViewport) Text(LocalizationManager.t("projects_map"), style = MaterialTheme.typography.titleLarge)
        else PageHeading(LocalizationManager.t("projects_map"), Icons.Default.Map)
        if (mobile) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${LocalizationManager.t("markers_count")} ${visible.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { filtersExpanded = !filtersExpanded }) {
                Icon(if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.FilterList, null)
                Spacer(Modifier.width(6.dp))
                Text(LocalizationManager.t("filters"))
            }
        }
        if (!mobile || filtersExpanded) Column(
            if (mobile) (if (shortViewport) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().heightIn(max = filterHeight))
                .verticalScroll(rememberScrollState()) else Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        Text(LocalizationManager.t("map_scope"), style = MaterialTheme.typography.labelLarge)
        @Composable fun ScopeSelector(modifier: Modifier) {
            SingleChoiceSegmentedButtonRow(modifier) {
                MapScope.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = mapScope == option,
                        onClick = { mapScope = option },
                        modifier = Modifier.buttonHandCursor(),
                        shape = SegmentedButtonDefaults.itemShape(index, MapScope.entries.size),
                        colors = mapSegmentedColors(),
                        icon = {},
                        label = { Text(LocalizationManager.t(option.labelKey)) }
                    )
                }
            }
        }
        @Composable fun TrancheSelector(modifier: Modifier) {
            SingleChoiceSegmentedButtonRow(modifier) {
                listOf<Int?>(null, 1, 2).forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = trancheNumber == option,
                        onClick = { trancheNumber = option },
                        modifier = Modifier.buttonHandCursor(),
                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                        colors = mapSegmentedColors(),
                        icon = {},
                        label = {
                            Text(LocalizationManager.t(when (option) {
                                null -> "all_tranches"
                                1 -> "tranche_a"
                                else -> "tranche_b"
                            }))
                        }
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
            if (maxWidth < 620.dp) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScopeSelector(Modifier.fillMaxWidth())
                TrancheSelector(Modifier.fillMaxWidth())
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScopeSelector(Modifier.weight(1f))
                TrancheSelector(Modifier.weight(1f))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val controls: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedTextField(search, { search = it.safeTextInput(200) }, singleLine = true,
                    label = { Text(LocalizationManager.t("search_project")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = modifier)
            }
            val regionFilter: @Composable (Modifier) -> Unit = { modifier ->
                MultiSelectOptionPicker(
                    options = located.map { it.region }.filter(String::isNotBlank).distinct().sorted(),
                    selected = regions,
                    prompt = LocalizationManager.t("region"),
                    onSelectionChange = { regions = it },
                    itemLabel = ::localizedUkraineRegion,
                    modifier = modifier,
                    clearLabel = LocalizationManager.t("map_clear_selection")
                )
            }
            val statusFilter: @Composable (Modifier) -> Unit = { modifier ->
                MultiSelectOptionPicker(
                    options = ProjectStatus.entries,
                    selected = statuses,
                    prompt = LocalizationManager.t("status"),
                    onSelectionChange = { statuses = it },
                    itemLabel = { LocalizationManager.t("project_status_${it.name.lowercase()}") },
                    modifier = modifier,
                    clearLabel = LocalizationManager.t("map_clear_selection")
                )
            }
            val reset: @Composable () -> Unit = {
                TextButton(onClick = {
                    search = ""
                    regions = emptySet()
                    statuses = emptySet()
                    trancheNumber = null
                    fitUkraineOverview()
                }) { Text(LocalizationManager.t("reset_filters")) }
            }
            if (maxWidth >= 900.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    controls(Modifier.weight(1f))
                    regionFilter(Modifier.width(180.dp))
                    statusFilter(Modifier.width(174.dp))
                    reset()
                }
            } else {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    controls(Modifier.fillMaxWidth())
                    regionFilter(if (mobile) Modifier.fillMaxWidth() else Modifier.width(200.dp))
                    statusFilter(if (mobile) Modifier.fillMaxWidth() else Modifier.width(190.dp))
                    reset()
                }
            }
        }
        }
        if (!mobile || !shortViewport || !filtersExpanded) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!mobile) Text("${LocalizationManager.t("markers_count")} ${visible.size}", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = ::fitUkraineOverview) { Icon(Icons.Default.Public, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(LocalizationManager.t("all_ukraine")) }
            TextButton(onClick = ::fitLeafletResults, enabled = visible.isNotEmpty()) { Icon(Icons.Default.CenterFocusStrong, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(LocalizationManager.t("fit_results")) }
        }
        if (ProjectRepository.loading) ContentState(LocalizationManager.t("loading_records"), loading = true)
        else if (ProjectRepository.errorMessage != null) ContentState(LocalizationManager.t("load_records_error"), error = true, onRetry = { scope.launch { ProjectRepository.refresh(force = true) } })
        else if (visible.isEmpty()) ContentState(LocalizationManager.t("no_map_results"))
        if (!mobile || !shortViewport || !filtersExpanded) Box(Modifier.fillMaxWidth().weight(1f)) {
            LeafletMapView(visible, allProjects = projects) { id -> visible.firstOrNull { it.id == id }?.let(onOpenProject) }
        }
    }
    }
}

@Composable
private fun mapSegmentedColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary,
    activeContentColor = Color.White,
    inactiveContainerColor = MaterialTheme.colorScheme.surface,
    inactiveContentColor = MaterialTheme.colorScheme.onSurface
)

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
