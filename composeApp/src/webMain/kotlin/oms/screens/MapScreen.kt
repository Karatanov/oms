package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.data.ProjectRepository
import oms.components.*
import oms.localization.LocalizationManager
import oms.map.LeafletMapView
import oms.model.Project
import oms.model.ProjectStatus
import kotlin.js.JsName
import kotlinx.coroutines.launch

@JsName("fitUkraineOverview") private external fun fitUkraineOverview()
@JsName("fitLeafletResults") private external fun fitLeafletResults()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapScreen(onOpenProject: (Project) -> Unit = {}) {
    LaunchedEffect(Unit) { ProjectRepository.refresh() }
    val scope = rememberCoroutineScope()
    val projects = ProjectRepository.projects
    var search by remember { mutableStateOf("") }
    var region by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<ProjectStatus?>(null) }
    var type by remember { mutableStateOf<String?>(null) }
    val located = projects.filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 && (it.latitude != 0.0 || it.longitude != 0.0) }
    val visible = located.filter {
        (search.isBlank() || it.name.contains(search, true) || it.siteNumber.contains(search, true) || it.city.contains(search, true)) &&
        (region == null || it.region == region) && (status == null || it.status == status) && (type == null || it.projectType == type)
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeading(LocalizationManager.t("projects_map"), Icons.Default.Map)
        OutlinedTextField(search, { search = it }, singleLine = true,
            label = { Text(LocalizationManager.t("search_project")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InlineOptionPicker(located.map { it.region }.filter(String::isNotBlank).distinct().sorted(), region,
                LocalizationManager.t("region"), { region = it }, modifier = Modifier.width(200.dp),
                clearLabel = LocalizationManager.t("all"), onClear = { region = null })
            InlineOptionPicker(ProjectStatus.entries, status, LocalizationManager.t("status"), { status = it },
                { LocalizationManager.t("project_status_${it.name.lowercase()}") }, modifier = Modifier.width(190.dp),
                clearLabel = LocalizationManager.t("all"), onClear = { status = null })
            InlineOptionPicker(listOf("project", "subproject", "subproject_part"), type, LocalizationManager.t("project_type"),
                { type = it }, LocalizationManager::t, modifier = Modifier.width(220.dp),
                clearLabel = LocalizationManager.t("all"), onClear = { type = null })
            TextButton(onClick = { search = ""; region = null; status = null; type = null }) { Text(LocalizationManager.t("reset_filters")) }
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
            LeafletMapView(visible) { id -> visible.firstOrNull { it.id == id }?.let(onOpenProject) }
        }
    }
}
