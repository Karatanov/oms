package oms.screens

import androidx.compose.foundation.layout.*
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
    LaunchedEffect(Unit) { ProjectRepository.refresh() }
    val scope = rememberCoroutineScope()
    val projects = ProjectRepository.projects
    var search by remember { mutableStateOf("") }
    var region by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<ProjectStatus?>(null) }
    var mapScope by remember { mutableStateOf(MapScope.SUBPROJECTS) }
    // Show exactly one child level, never portfolio-level projects.
    val located = projects.filter {
        it.projectType == mapScope.projectType &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 &&
            (it.latitude != 0.0 || it.longitude != 0.0)
    }
    val visible = located.filter {
        (search.isBlank() || it.localizedName().contains(search, true) || it.siteNumber.contains(search, true) || it.localizedCity().contains(search, true)) &&
        (region == null || it.region == region) && (status == null || it.status == status)
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageHeading(LocalizationManager.t("projects_map"), Icons.Default.Map)
        Text(LocalizationManager.t("map_scope"), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            MapScope.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = mapScope == option,
                    onClick = { mapScope = option },
                    shape = SegmentedButtonDefaults.itemShape(index, MapScope.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = Color.White,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    icon = {},
                    label = { Text(LocalizationManager.t(option.labelKey)) }
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val controls: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedTextField(search, { search = it }, singleLine = true,
                    label = { Text(LocalizationManager.t("search_project")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = modifier)
            }
            val regionFilter: @Composable (Modifier) -> Unit = { modifier ->
                InlineOptionPicker(located.map { it.region }.filter(String::isNotBlank).distinct().sorted(), region,
                    LocalizationManager.t("region"), { region = it }, ::localizedUkraineRegion, modifier = modifier,
                    clearLabel = LocalizationManager.t("all"), onClear = { region = null })
            }
            val statusFilter: @Composable (Modifier) -> Unit = { modifier ->
                InlineOptionPicker(ProjectStatus.entries, status, LocalizationManager.t("status"), { status = it },
                    { LocalizationManager.t("project_status_${it.name.lowercase()}") }, modifier = modifier,
                    clearLabel = LocalizationManager.t("all"), onClear = { status = null })
            }
            val reset: @Composable () -> Unit = {
                TextButton(onClick = { search = ""; region = null; status = null }) { Text(LocalizationManager.t("reset_filters")) }
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
                    regionFilter(Modifier.width(200.dp))
                    statusFilter(Modifier.width(190.dp))
                    reset()
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${LocalizationManager.t("markers_count")} ${visible.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = ::fitUkraineOverview) { Icon(Icons.Default.Public, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(LocalizationManager.t("all_ukraine")) }
            TextButton(onClick = ::fitLeafletResults, enabled = visible.isNotEmpty()) { Icon(Icons.Default.CenterFocusStrong, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(LocalizationManager.t("fit_results")) }
        }
        if (ProjectRepository.loading) ContentState(LocalizationManager.t("loading_records"), loading = true)
        else if (ProjectRepository.errorMessage != null) ContentState(LocalizationManager.t("load_records_error"), error = true, onRetry = { scope.launch { ProjectRepository.refresh(force = true) } })
        else if (visible.isEmpty()) ContentState(LocalizationManager.t("no_map_results"))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            LeafletMapView(visible, allProjects = projects) { id -> visible.firstOrNull { it.id == id }?.let(onOpenProject) }
        }
    }
}
