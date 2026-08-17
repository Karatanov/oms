package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.localization.LocalizationManager
import oms.components.OmsDateField
import oms.components.InlineOptionPicker
import oms.components.currentIsoDate
import oms.model.Project
import kotlin.js.JsName

@JsName("openSirImportDialog")
external fun openSirImportDialog(projectUuid: String)

@JsName("openInspectionPhotoPicker")
external fun openInspectionPhotoPicker(onSelectionChanged: (Int) -> Unit)

@JsName("uploadSelectedInspectionPhotos")
external fun uploadSelectedInspectionPhotos(reportUuid: String, onComplete: (String) -> Unit)

@Composable
fun CreateInspectionScreen(
    isEditMode: Boolean = false,
    currentUserName: String = "Current User",
    isAdmin: Boolean = false,
    rejectedReason: String? = null,
    onSaveDraft: () -> Unit = {},
    onSubmit: () -> Unit = {},
    onImportXls: () -> Unit = {}
) {
    var date by remember { mutableStateOf(currentIsoDate()) }
    var inspectionType by remember { mutableStateOf(InspectionType.PLANNED) }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf(TextFieldValue("")) }
    var photoCount by remember { mutableStateOf(0) }
    var selectedProjectUuid by remember { mutableStateOf<String?>(null) }
    var selectedSubprojectUuid by remember { mutableStateOf<String?>(null) }
    var selectedSubprojectPartUuid by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var createdReportUuid by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ProjectRepository.refresh()
    }

    val projects = ProjectRepository.projects
    val subprojects = projects.filter {
        it.projectType.equals("subproject", true) && it.parentProjectUuid == selectedProjectUuid
    }
    val subprojectParts = projects.filter {
        it.projectType.equals("subproject_part", true) && it.parentProjectUuid == selectedSubprojectUuid
    }
    val inspectionTargetUuid = selectedSubprojectPartUuid ?: selectedSubprojectUuid ?: selectedProjectUuid
    val selectionError = when {
        selectedProjectUuid == null -> "Select a project."
        subprojects.isNotEmpty() && selectedSubprojectUuid == null -> "Select a subproject."
        subprojectParts.isNotEmpty() && selectedSubprojectPartUuid == null -> "Select a subproject part."
        else -> null
    }

    val maxComments = 2000

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (isEditMode) LocalizationManager.t("edit_inspection") else LocalizationManager.t("create_inspection"),
            style = MaterialTheme.typography.headlineMedium
        )

        if (rejectedReason != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${LocalizationManager.t("rejected")}: $rejectedReason",
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFF8D6E63)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InspectionProjectSelector(
                    projects = projects,
                    selectedProjectUuid = selectedProjectUuid,
                    selectedSubprojectUuid = selectedSubprojectUuid,
                    selectedSubprojectPartUuid = selectedSubprojectPartUuid,
                    onProjectSelect = {
                        selectedProjectUuid = it
                        selectedSubprojectUuid = null
                        selectedSubprojectPartUuid = null
                    },
                    onSubprojectSelect = {
                        selectedSubprojectUuid = it
                        selectedSubprojectPartUuid = null
                    },
                    onSubprojectPartSelect = { selectedSubprojectPartUuid = it }
                )

                OmsDateField(
                    value = date,
                    onValueChange = { date = it },
                    label = LocalizationManager.t("date_label"),
                    modifier = Modifier.fillMaxWidth(),
                    required = true
                )
                Text(LocalizationManager.t("cannot_be_future_date"), style = MaterialTheme.typography.bodySmall)

                InspectionTypeDropdown(
                    value = inspectionType,
                    onChange = { inspectionType = it }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(latitude, { value -> if (value.matches(Regex("-?[0-9.,]*"))) latitude = value }, label = { Text("Широта") }, modifier = Modifier.weight(1f), supportingText = { Text("-90…90") })
                    OutlinedTextField(longitude, { value -> if (value.matches(Regex("-?[0-9.,]*"))) longitude = value }, label = { Text("Довгота") }, modifier = Modifier.weight(1f), supportingText = { Text("-180…180") })
                }

                OutlinedTextField(
                    value = comments,
                    onValueChange = {
                        if (it.text.length <= maxComments) comments = it
                    },
                    label = { Text(LocalizationManager.t("comments")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    minLines = 5,
                    maxLines = 8,
                    supportingText = {
                        Text("${comments.text.length} / $maxComments")
                    }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = LocalizationManager.t("photos"),
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedButton(
                    onClick = { openInspectionPhotoPicker { photoCount = it } },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(LocalizationManager.t("add_inspection_photos"))
                }
                Text(
                    text = if (photoCount == 0) LocalizationManager.t("no_photos_selected") else LocalizationManager.t("photos_selected").replace("{count}", photoCount.toString()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = LocalizationManager.t("photo_upload_requirements"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onSaveDraft) {
                Text(LocalizationManager.t("save_draft"))
            }

            OutlinedButton(onClick = {
                val selectedProject = inspectionTargetUuid
                if (selectionError != null) errorMessage = selectionError
                else {
                    openSirImportDialog(requireNotNull(selectedProject))
                    onImportXls()
                }
            }) {
                Text("Завантажити XLS/XLSX")
            }

            Button(
                enabled = !isSubmitting,
                onClick = {
                    val createdReport = createdReportUuid
                    if (createdReport != null) {
                        isSubmitting = true
                        errorMessage = null
                        uploadSelectedInspectionPhotos(createdReport) { uploadError ->
                            if (uploadError.isBlank()) onSubmit()
                            else errorMessage = "Photo upload failed: $uploadError. You can retry without creating a second report."
                            isSubmitting = false
                        }
                        return@Button
                    }
                    val selectedProject = inspectionTargetUuid
                    when {
                        selectionError != null -> errorMessage = selectionError
                        !date.isIsoDate() -> errorMessage = "Date must use YYYY-MM-DD format."
                        comments.text.isBlank() -> errorMessage = "Add a report summary before submitting."
                        else -> {
                            isSubmitting = true
                            errorMessage = null
                            scope.launch {
                                val summary = buildString {
                                    append("[${inspectionType.name.lowercase()}] ")
                                    append(comments.text.trim())
                                    if (latitude.isNotBlank() || longitude.isNotBlank()) append(" | GPS: ${latitude.trim()}, ${longitude.trim()}")
                                }
                                runCatching {
                                    OmsApiClient.createAndSubmitInspectionReport(requireNotNull(selectedProject), date, summary)
                                }.onSuccess { report ->
                                    createdReportUuid = report.uuid
                                    uploadSelectedInspectionPhotos(report.uuid) { uploadError ->
                                        if (uploadError.isBlank()) onSubmit()
                                        else errorMessage = "Report was created, but photo upload failed: $uploadError"
                                        isSubmitting = false
                                    }
                                }.onFailure {
                                    errorMessage = "Could not submit report: ${it.message ?: "unknown error"}"
                                    isSubmitting = false
                                }
                            }
                        }
                    }
                }
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(LocalizationManager.t("submit_report"))
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun InspectionProjectSelector(
    projects: List<Project>,
    selectedProjectUuid: String?,
    selectedSubprojectUuid: String?,
    selectedSubprojectPartUuid: String?,
    onProjectSelect: (String?) -> Unit,
    onSubprojectSelect: (String?) -> Unit,
    onSubprojectPartSelect: (String?) -> Unit
) {
    val rootProjects = projects.filter { it.projectType.equals("project", true) }
    val subprojects = projects.filter {
        it.projectType.equals("subproject", true) && it.parentProjectUuid == selectedProjectUuid
    }
    val parts = projects.filter {
        it.projectType.equals("subproject_part", true) && it.parentProjectUuid == selectedSubprojectUuid
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProjectLevelDropdown(LocalizationManager.t("select_project"), rootProjects, selectedProjectUuid, onProjectSelect)
        ProjectLevelDropdown(LocalizationManager.t("select_subproject"), subprojects, selectedSubprojectUuid, onSubprojectSelect, enabled = subprojects.isNotEmpty())
        ProjectLevelDropdown(LocalizationManager.t("select_subproject_part"), parts, selectedSubprojectPartUuid, onSubprojectPartSelect, enabled = parts.isNotEmpty())
    }
}

@Composable
private fun ProjectLevelDropdown(
    label: String,
    options: List<Project>,
    selectedUuid: String?,
    onSelect: (String?) -> Unit,
    enabled: Boolean = true
) {
    val selected = options.firstOrNull { it.id == selectedUuid }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        InlineOptionPicker(
            options = options,
            selected = selected,
            prompt = label,
            onSelect = { onSelect(it.id) },
            itemLabel = {
                if (it.siteNumber.equals(it.name, ignoreCase = true)) it.name
                else "${it.siteNumber} — ${it.name}"
            },
            enabled = enabled
        )
    }
}

private fun String.isIsoDate(): Boolean = matches(Regex("\\d{4}-\\d{2}-\\d{2}"))

@Composable
private fun InspectionTypeDropdown(
    value: InspectionType,
    onChange: (InspectionType) -> Unit
) {
    InlineOptionPicker(
        options = InspectionType.entries,
        selected = value,
        prompt = LocalizationManager.t("inspection_type"),
        onSelect = onChange,
        itemLabel = { it.label }
    )
}

private enum class InspectionType {
    PLANNED,
    UNPLANNED,
    FINAL;

    val label: String
        get() = when (this) {
            PLANNED -> LocalizationManager.t("planned")
            UNPLANNED -> LocalizationManager.t("unplanned")
            FINAL -> LocalizationManager.t("final")
        }
}
