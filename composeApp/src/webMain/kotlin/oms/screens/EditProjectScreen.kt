package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oms.data.*
import oms.localization.LocalizationManager as L
import oms.model.Project

@Composable
fun EditProjectScreen(project: Project, onCancel: () -> Unit = {}, onSaved: (Project) -> Unit = {}) {
    var state by remember(project.id) { mutableStateOf<ProjectFormState?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(project.id) {
        runCatching { OmsApiClient.projectDetails(project.id).data }
            .onSuccess { details ->
                state = ProjectFormState(details).also { form ->
                    runCatching { OmsApiClient.projectExchangeRate() }.getOrNull()?.let(form::applyRate)
                }
            }.onFailure { error = L.t("error_load_project") }
    }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onPrimary = Color.White)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            oms.components.PageHeading(L.t("edit_project"), Icons.Default.Edit)
            val form = state
            if (form == null) CircularProgressIndicator() else {
                ProjectForm(form, emptyList(), editing = true)
                if (form.currentRate == null) oms.components.ContentState(L.t("project_money_rate_unavailable"))
                error?.let { oms.components.ContentState(it, error = true) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    OutlinedButton(onClick = onCancel, enabled = !saving) { Text(L.t("cancel")) }
                    Button(enabled = !saving, onClick = {
                        error = form.validationError(true)
                        if (error == null) scope.launch {
                            saving = true
                            runCatching { OmsApiClient.updateProject(project.id, form.updateRequest()) }
                                .onSuccess { ProjectRepository.refresh(force = true); onSaved(ProjectRepository.projects.firstOrNull { it.id == project.id } ?: project) }
                                .onFailure { error = L.t("error_save_project").replace("{message}", it.message ?: L.t("unknown_error")) }
                            saving = false
                        }
                    }) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(L.t("save_changes"))
                    }
                }
            }
        }
    }
}
