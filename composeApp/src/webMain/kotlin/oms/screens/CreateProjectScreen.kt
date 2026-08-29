package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import oms.data.*
import oms.localization.LocalizationManager as L

@Composable
fun CreateProjectScreen(onCancel: () -> Unit = {}, onCreated: () -> Unit = {}) {
    val state = remember { ProjectFormState() }
    var parents by remember { mutableStateOf<List<ApiProject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        coroutineScope {
            val projects = async { runCatching { OmsApiClient.projects() }.getOrDefault(emptyList()) }
            val rate = async { runCatching { OmsApiClient.projectExchangeRate() }.getOrNull() }
            parents = projects.await()
            rate.await()?.let(state::applyRate)
        }
    }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onPrimary = Color.White)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            oms.components.PageHeading(L.t("create_project"), Icons.Default.CreateNewFolder)
            Text(L.t("project_created_hint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ProjectForm(state, parents, editing = false, onError = { error = it })
            if (state.currentRate == null) oms.components.ContentState(L.t("project_money_rate_unavailable"))
            error?.let { oms.components.ContentState(it, error = true) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                OutlinedButton(onClick = onCancel, enabled = !saving) { Text(L.t("cancel")) }
                Button(enabled = !saving, onClick = {
                    error = state.validationError(false)
                    if (error == null) scope.launch {
                        saving = true
                        runCatching { OmsApiClient.createProject(state.createRequest()) }
                            .onSuccess { ProjectRepository.refresh(force = true); onCreated() }
                            .onFailure { error = L.t("error_create_project").replace("{message}", it.message ?: L.t("unknown_error")) }
                        saving = false
                    }
                }) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text(L.t("create_project"))
                }
            }
        }
    }
}
