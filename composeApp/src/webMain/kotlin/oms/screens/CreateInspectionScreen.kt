package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.localization.LocalizationManager
import oms.components.OmsDateField
import oms.components.InlineOptionPicker
import oms.components.SearchableOptionPicker
import oms.components.currentIsoDate
import oms.model.Project
import kotlin.js.JsName
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.AcUnit
import oms.components.PageHeading
import oms.components.TableActionIconButton
import oms.components.NativePaneAnchor

@JsName("openSirImportDialog")
external fun openSirImportDialog(projectUuid: String, onComplete: (String) -> Unit)

@JsName("openInspectionPhotoPicker")
external fun openInspectionPhotoPicker(onSelectionChanged: (Int) -> Unit)

@JsName("uploadSelectedInspectionPhotos")
external fun uploadSelectedInspectionPhotos(reportUuid: String, onComplete: (String) -> Unit)

@JsName("showPendingInspectionPhotoPreviews")
external fun showPendingInspectionPhotoPreviews(onSelectionChanged: (Int) -> Unit)

@JsName("hidePendingInspectionPhotoPreviews")
external fun hidePendingInspectionPhotoPreviews()

@Composable
fun CreateInspectionScreen(
    isEditMode: Boolean = false,
    editingReportUuid: String? = null,
    currentUserName: String = "",
    isAdmin: Boolean = false,
    canChangeReportStatus: Boolean = false,
    rejectedReason: String? = null,
    onSaveDraft: () -> Unit = {},
    onSubmit: () -> Unit = {},
    onImportXls: () -> Unit = {}
) {
    val isManualEdit = editingReportUuid != null
    var date by remember { mutableStateOf(currentIsoDate()) }
    var inspectionType by remember { mutableStateOf(InspectionType.PLANNED) }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf(TextFieldValue("")) }
    var photoCount by remember { mutableStateOf(0) }
    var photoSelectionRevision by remember { mutableStateOf(0) }
    var selectedProjectUuid by remember { mutableStateOf<String?>(null) }
    var selectedSubprojectUuid by remember { mutableStateOf<String?>(null) }
    var selectedSubprojectPartUuid by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var createdReportUuid by remember { mutableStateOf<String?>(null) }
    var entryMode by remember { mutableStateOf("manual") }
    var contractor by remember { mutableStateOf("") }
    var inspectorName by remember { mutableStateOf(currentUserName) }
    var contractorRepresentative by remember { mutableStateOf("") }
    var qaStaff by remember { mutableStateOf(currentUserName) }
    var usifRepresentative by remember { mutableStateOf("") }
    var skilledLabor by remember { mutableStateOf("") }
    var unskilledLabor by remember { mutableStateOf("") }
    var siteManagement by remember { mutableStateOf("") }
    var weatherCondition by remember { mutableStateOf<WeatherCondition?>(null) }
    var temperatureCelsius by remember { mutableStateOf("") }
    var activities by remember { mutableStateOf(listOf(ManualActivityInput())) }
    var projectName by remember { mutableStateOf("") }
    var siteAddress by remember { mutableStateOf("") }
    var ongoingObservations by remember { mutableStateOf(listOf("")) }
    var hseObservations by remember { mutableStateOf(defaultHseObservations) }
    var qualityText by remember { mutableStateOf("") }
    var progressComment by remember { mutableStateOf("") }
    var scheduleRemark by remember { mutableStateOf("") }
    var inspectorTitle by remember { mutableStateOf("") }
    var reportStatus by remember { mutableStateOf("draft") }
    var loadingManualReport by remember(editingReportUuid) { mutableStateOf(isManualEdit) }
    val scope = rememberCoroutineScope()
    val pageScrollState = rememberScrollState()
    fun scrollBy(delta: Float) = scope.launch { pageScrollState.animateScrollBy(delta) }

    // The report's QA staff is normally the person creating it.  Preserve a
    // manually selected value, but recover the default if the user context
    // becomes available after the screen has first composed.
    LaunchedEffect(currentUserName) {
        if (qaStaff.isBlank()) qaStaff = currentUserName
        if (inspectorName.isBlank()) inspectorName = currentUserName
    }

    val projects = ProjectRepository.projects
    val subprojects = projects.filter {
        it.projectType.equals("subproject", true) && it.parentProjectUuid == selectedProjectUuid
    }
    val inspectionTargetUuid = selectedSubprojectPartUuid ?: selectedSubprojectUuid ?: selectedProjectUuid
    val selectedSubproject = projects.firstOrNull { it.id == selectedSubprojectUuid }
    val siteReference = selectedSubproject?.let { subproject ->
        listOf(subproject.siteNumber, siteAddress)
            .filter(String::isNotBlank)
            .joinToString(", ")
    }.orEmpty()
    val selectionError = when {
        selectedProjectUuid == null -> LocalizationManager.t("select_project_error")
        subprojects.isNotEmpty() && selectedSubprojectUuid == null -> LocalizationManager.t("select_subproject_error")
        // A report may belong directly to a subproject. Selecting a part is
        // optional and only narrows that placement when it is applicable.
        else -> null
    }

    LaunchedEffect(editingReportUuid) {
        val reportUuid = editingReportUuid ?: return@LaunchedEffect
        loadingManualReport = true
        runCatching {
            ProjectRepository.refresh()
            OmsApiClient.manualInspectionReport(reportUuid)
        }.onSuccess { editor ->
            val manual = editor.manual
            val projectsById = ProjectRepository.projects.associateBy { it.id }
            val target = projectsById[editor.projectUuid]
            val ancestry = target?.let {
                generateSequence(it) { current -> current.parentProjectUuid?.let(projectsById::get) }
                    .toList().asReversed()
            }.orEmpty()
            selectedProjectUuid = ancestry.getOrNull(0)?.id
            selectedSubprojectUuid = ancestry.getOrNull(1)?.id
            selectedSubprojectPartUuid = ancestry.getOrNull(2)?.id
            date = manual.inspectionDate
            reportStatus = editor.status
            inspectionType = InspectionType.entries.firstOrNull { it.name.equals(manual.inspectionType, true) } ?: InspectionType.PLANNED
            projectName = manual.projectName.orEmpty()
            contractor = manual.contractor
            contractorRepresentative = manual.contractorRepresentative.orEmpty()
            qaStaff = manual.qaStaff.orEmpty()
            usifRepresentative = manual.usifRepresentative.orEmpty()
            skilledLabor = manual.skilledLabor.orEmpty()
            unskilledLabor = manual.unskilledLabor.orEmpty()
            siteManagement = manual.siteManagement.orEmpty()
            weatherCondition = WeatherCondition.fromWorkbookValue(manual.weather)
            temperatureCelsius = manual.weather.workbookTemperature()
            activities = manual.activities.map {
                ManualActivityInput(it.location, it.description, it.onSchedule.lowercase().ifBlank { "no" }, it.remarks.orEmpty())
            }.ifEmpty { listOf(ManualActivityInput()) }
            ongoingObservations = manual.ongoingObservations.ifEmpty { listOf("") }
            hseObservations = manual.hseObservations.map {
                HseObservationInput(it.observation, it.answer.equals("yes", true), it.comment.orEmpty())
            }.ifEmpty { defaultHseObservations }
            qualityText = manual.qualityRemarks.joinToString("\n") { remark -> listOfNotNull(remark.comment, remark.rectification).joinToString(" | ") }
            progressComment = manual.progressComment.orEmpty()
            scheduleRemark = manual.scheduleRemark.orEmpty()
            inspectorName = manual.inspectorName.ifBlank { currentUserName }
            inspectorTitle = manual.inspectorTitle.orEmpty()
            latitude = manual.latitude?.toString().orEmpty()
            longitude = manual.longitude?.toString().orEmpty()
            entryMode = "manual"
        }.onFailure { failure ->
            errorMessage = LocalizationManager.t("error_update_report").replace("{message}", failure.message ?: LocalizationManager.t("unknown_error"))
        }
        loadingManualReport = false
    }

    // The SIR workbook stores the code and address in a single cell.  Keep
    // them separate in the form and load the address from the selected
    // subproject whenever it is available.
    LaunchedEffect(selectedSubprojectUuid) {
        val subprojectUuid = selectedSubprojectUuid
        if (subprojectUuid == null) {
            siteAddress = ""
        } else {
            runCatching { OmsApiClient.projectDetails(subprojectUuid).data.address }
                .onSuccess { address -> siteAddress = address }
        }
    }

    val maxComments = 2000

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { scrollBy(-420f); true }
                    Key.DirectionDown -> { scrollBy(420f); true }
                    Key.PageUp -> { scrollBy(-720f); true }
                    Key.PageDown -> { scrollBy(720f); true }
                    Key.MoveHome -> { scope.launch { pageScrollState.animateScrollTo(0) }; true }
                    Key.MoveEnd -> { scope.launch { pageScrollState.animateScrollTo(pageScrollState.maxValue) }; true }
                    else -> false
                }
            }
            .verticalScroll(pageScrollState)
            .padding(start = 24.dp, top = 24.dp, end = 76.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeading(if (isEditMode || isManualEdit) LocalizationManager.t("edit_inspection") else LocalizationManager.t("create_inspection"), Icons.Default.FactCheck)

        if (loadingManualReport) {
            oms.components.ContentState(LocalizationManager.t("loading_records"), loading = true)
        }

        if (!isManualEdit) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(entryMode == "manual", { entryMode = "manual"; errorMessage = null }, label = { Text(LocalizationManager.t("manual_sir_entry")) })
            FilterChip(entryMode == "import", { entryMode = "import"; errorMessage = null }, label = { Text(LocalizationManager.t("import_sir_xlsx")) })
        }

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
                containerColor = Color.White
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
                    loadProjectsOnOpen = {
                        ProjectRepository.refresh()
                        ProjectRepository.projects
                    },
                    selectedProjectUuid = selectedProjectUuid,
                    selectedSubprojectUuid = selectedSubprojectUuid,
                    selectedSubprojectPartUuid = selectedSubprojectPartUuid,
                    onProjectSelect = {
                        errorMessage = null
                        selectedProjectUuid = it
                        projectName = projects.firstOrNull { project -> project.id == it }?.name.orEmpty()
                        selectedSubprojectUuid = null
                        selectedSubprojectPartUuid = null
                    },
                    onSubprojectSelect = {
                        errorMessage = null
                        selectedSubprojectUuid = it
                        selectedSubprojectPartUuid = null
                        contractor = projects.firstOrNull { project -> project.id == it }?.contractorName.orEmpty()
                    },
                    onSubprojectPartSelect = {
                        errorMessage = null
                        selectedSubprojectPartUuid = it
                    }
                )

                OutlinedTextField(
                    value = siteAddress,
                    onValueChange = { siteAddress = it.inspectionText(500) },
                    label = { Text(LocalizationManager.t("address")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )

                InspectionTypeDropdown(value = inspectionType, onChange = { inspectionType = it })
                if (isManualEdit && canChangeReportStatus) {
                    InlineOptionPicker(
                        options = listOf("draft", "pending_review", "completed"), selected = reportStatus,
                        prompt = LocalizationManager.t("status"), onSelect = { reportStatus = it },
                        itemLabel = { value -> LocalizationManager.t("${value}_status") }
                    )
                }
            }
        }

        if (entryMode != "import") Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
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
                    onClick = { openInspectionPhotoPicker { count -> photoCount = count; photoSelectionRevision++ } },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(LocalizationManager.t("add_inspection_photos"))
                }
                Text(
                    text = if (photoCount == 0) LocalizationManager.t("no_photos_selected") else LocalizationManager.t("photos_selected").replace("{count}", photoCount.toString()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (photoCount > 0) PendingInspectionPhotoPreviews(photoSelectionRevision) { count ->
                    photoCount = count
                    photoSelectionRevision++
                }
                Text(
                    text = LocalizationManager.t("photo_upload_requirements"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (entryMode == "manual") ManualSirForm(
            date = date, onDateChange = { date = it }, projectName = projectName, onProjectNameChange = { projectName = it },
            contractor = contractor, onContractorChange = { contractor = it },
            contractorRepresentative = contractorRepresentative, onContractorRepresentativeChange = { contractorRepresentative = it },
            qaStaff = qaStaff, onQaStaffChange = { qaStaff = it }, usifRepresentative = usifRepresentative, onUsifRepresentativeChange = { usifRepresentative = it },
            skilledLabor = skilledLabor, onSkilledLaborChange = { skilledLabor = it }, unskilledLabor = unskilledLabor, onUnskilledLaborChange = { unskilledLabor = it },
            siteManagement = siteManagement, onSiteManagementChange = { siteManagement = it }, weatherCondition = weatherCondition, onWeatherConditionChange = { weatherCondition = it }, temperatureCelsius = temperatureCelsius, onTemperatureCelsiusChange = { temperatureCelsius = it },
            activities = activities, onActivitiesChange = { activities = it }, ongoingObservations = ongoingObservations, onOngoingObservationsChange = { ongoingObservations = it },
            hseObservations = hseObservations, onHseObservationsChange = { hseObservations = it }, quality = qualityText, onQualityChange = { qualityText = it },
            progress = progressComment, onProgressChange = { progressComment = it }, schedule = scheduleRemark, onScheduleChange = { scheduleRemark = it },
            inspectorName = inspectorName, inspectorTitle = inspectorTitle, onInspectorTitleChange = { inspectorTitle = it }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            if (!isManualEdit) OutlinedButton(
                enabled = !isSubmitting && !loadingManualReport,
                onClick = {
                    val selectedProject = inspectionTargetUuid
                    when {
                        selectionError != null -> errorMessage = selectionError
                        !date.isIsoDate() -> errorMessage = LocalizationManager.t("error_invalid_iso_date")
                        else -> {
                            isSubmitting = true
                            errorMessage = null
                            scope.launch {
                                val summary = buildInspectionSummary(inspectionType, comments.text, latitude, longitude)
                                runCatching {
                                    OmsApiClient.createInspectionReportDraft(
                                        requireNotNull(selectedProject), date, summary,
                                        inspectionType.name.lowercase(),
                                        latitude.replace(',', '.').toDoubleOrNull(),
                                        longitude.replace(',', '.').toDoubleOrNull()
                                    )
                                }.onSuccess {
                                    onSaveDraft()
                                }.onFailure {
                                    errorMessage = LocalizationManager.t("error_save_draft")
                                        .replace("{message}", it.message ?: LocalizationManager.t("unknown_error"))
                                    isSubmitting = false
                                }
                            }
                        }
                    }
                }
            ) {
                Text(LocalizationManager.t("save_draft"))
            }

            if (!isManualEdit) OutlinedButton(enabled = entryMode == "import" && !loadingManualReport, onClick = {
                val selectedProject = inspectionTargetUuid
                if (selectionError != null) errorMessage = selectionError
                else {
                    errorMessage = null
                    openSirImportDialog(requireNotNull(selectedProject)) { importError ->
                        if (importError.isBlank()) onImportXls()
                        else errorMessage = LocalizationManager.t("error_import_inspection_report")
                            .replace("{message}", importError)
                    }
                }
            }) {
                Text(LocalizationManager.t("upload_xls"))
            }

            Button(
                enabled = !isSubmitting && !loadingManualReport,
                onClick = {
                    if (entryMode == "manual") {
                        val target = inspectionTargetUuid
                        if (selectionError != null) errorMessage = selectionError
                        else if (contractor.isBlank() || inspectorName.isBlank()) errorMessage = LocalizationManager.t("manual_sir_required")
                        else {
                            isSubmitting = true
                            scope.launch {
                                runCatching {
                                    val manualRequest = oms.data.ManualInspectionReportRequest(
                                            inspectionDate = date,
                                            inspectionType = inspectionType.name.lowercase(),
                                            contractor = contractor,
                                            contractorRepresentative = contractorRepresentative.ifBlank { null },
                                            qaStaff = qaStaff.trim().ifBlank { inspectorName.trim() }.ifBlank { null },
                                            usifRepresentative = usifRepresentative.ifBlank { null },
                                            skilledLabor = skilledLabor.ifBlank { null },
                                            unskilledLabor = unskilledLabor.ifBlank { null },
                                            siteManagement = siteManagement.ifBlank { null },
                                            weather = weatherCondition.toWeatherWorkbookValue(temperatureCelsius),
                                            projectName = projectName.ifBlank { null },
                                            siteReference = siteReference.ifBlank { null },
                                            activities = activities.map { activity ->
                                                oms.data.ManualActivityRequest(
                                                    location = activity.location,
                                                    description = activity.description,
                                                    onSchedule = activity.onSchedule,
                                                    remarks = activity.remarks.ifBlank { null }
                                                )
                                            }.filter { activity ->
                                                activity.location.isNotBlank() || activity.description.isNotBlank() || activity.remarks != null
                                            },
                                            ongoingObservations = ongoingObservations.map(String::trim).filter(String::isNotBlank),
                                            hseObservations = hseObservations.map { oms.data.ManualHseObservationRequest(it.observation, if (it.isYes) "Yes" else "No", it.comment.ifBlank { null }) },
                                            qualityRemarks = qualityText.toManualQualityRemarks(),
                                            progressComment = progressComment.ifBlank { null },
                                            scheduleRemark = scheduleRemark.ifBlank { null },
                                            inspectorName = inspectorName,
                                            inspectorTitle = inspectorTitle.ifBlank { null },
                                            latitude = latitude.replace(',', '.').toDoubleOrNull(),
                                            longitude = longitude.replace(',', '.').toDoubleOrNull()
                                    )
                                    if (editingReportUuid == null) {
                                        OmsApiClient.createManualInspectionReport(requireNotNull(target), manualRequest)
                                    } else {
                                        OmsApiClient.updateManualInspectionReport(
                                            editingReportUuid, requireNotNull(target),
                                            reportStatus.takeIf { canChangeReportStatus }, manualRequest
                                        )
                                    }
                                }.onSuccess { report ->
                                    uploadSelectedInspectionPhotos(report.uuid) { uploadError ->
                                        if (uploadError.isBlank()) onSubmit()
                                        else errorMessage = LocalizationManager.t("error_upload_photos_after_report").replace("{message}", uploadError)
                                        isSubmitting = false
                                    }
                                }.onFailure {
                                    errorMessage = it.message
                                    isSubmitting = false
                                }
                            }
                            return@Button
                        }
                    }
                    val createdReport = createdReportUuid
                    if (createdReport != null) {
                        isSubmitting = true
                        errorMessage = null
                        uploadSelectedInspectionPhotos(createdReport) { uploadError ->
                            if (uploadError.isBlank()) onSubmit()
                            else errorMessage = LocalizationManager.t("error_upload_photos_after_report").replace("{message}", uploadError)
                            isSubmitting = false
                        }
                        return@Button
                    }
                    val selectedProject = inspectionTargetUuid
                    when {
                        selectionError != null -> errorMessage = selectionError
                        !date.isIsoDate() -> errorMessage = LocalizationManager.t("error_invalid_iso_date")
                        comments.text.isBlank() -> errorMessage = LocalizationManager.t("error_report_summary_required")
                        else -> {
                            isSubmitting = true
                            errorMessage = null
                            scope.launch {
                                val summary = buildInspectionSummary(inspectionType, comments.text, latitude, longitude)
                                runCatching {
                                    OmsApiClient.createAndSubmitInspectionReport(
                                        requireNotNull(selectedProject), date, summary,
                                        inspectionType.name.lowercase(),
                                        latitude.replace(',', '.').toDoubleOrNull(),
                                        longitude.replace(',', '.').toDoubleOrNull()
                                    )
                                }.onSuccess { report ->
                                    createdReportUuid = report.uuid
                                    uploadSelectedInspectionPhotos(report.uuid) { uploadError ->
                                        if (uploadError.isBlank()) onSubmit()
                                        else errorMessage = LocalizationManager.t("error_report_created_photo_upload").replace("{message}", uploadError)
                                        isSubmitting = false
                                    }
                                }.onFailure {
                                    errorMessage = LocalizationManager.t("error_submit_report").replace("{message}", it.message ?: LocalizationManager.t("unknown_error"))
                                    isSubmitting = false
                                }
                            }
                        }
                    }
                }
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (isManualEdit) LocalizationManager.t("save") else LocalizationManager.t("submit_report"))
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    Column(
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        oms.components.HoldToScrollButton(LocalizationManager.t("dashboard_scroll_up"), Icons.Default.KeyboardArrowUp, pageScrollState, -1)
        oms.components.HoldToScrollButton(LocalizationManager.t("dashboard_scroll_down"), Icons.Default.KeyboardArrowDown, pageScrollState, 1)
    }
    }
}

@Composable
private fun PendingInspectionPhotoPreviews(revision: Int, onSelectionChanged: (Int) -> Unit) {
    NativePaneAnchor("inspection-photo-preview-pane", Modifier.fillMaxWidth().height(210.dp))
    DisposableEffect(revision, LocalizationManager.currentLanguage) {
        showPendingInspectionPhotoPreviews(onSelectionChanged)
        onDispose(::hidePendingInspectionPhotoPreviews)
    }
}

@Composable
private fun ManualSirForm(
    date: String, onDateChange: (String) -> Unit,
    projectName: String, onProjectNameChange: (String) -> Unit,
    contractor: String, onContractorChange: (String) -> Unit,
    contractorRepresentative: String, onContractorRepresentativeChange: (String) -> Unit,
    qaStaff: String, onQaStaffChange: (String) -> Unit,
    usifRepresentative: String, onUsifRepresentativeChange: (String) -> Unit,
    skilledLabor: String, onSkilledLaborChange: (String) -> Unit,
    unskilledLabor: String, onUnskilledLaborChange: (String) -> Unit,
    siteManagement: String, onSiteManagementChange: (String) -> Unit,
    weatherCondition: WeatherCondition?, onWeatherConditionChange: (WeatherCondition?) -> Unit,
    temperatureCelsius: String, onTemperatureCelsiusChange: (String) -> Unit,
    activities: List<ManualActivityInput>, onActivitiesChange: (List<ManualActivityInput>) -> Unit,
    ongoingObservations: List<String>, onOngoingObservationsChange: (List<String>) -> Unit,
    hseObservations: List<HseObservationInput>, onHseObservationsChange: (List<HseObservationInput>) -> Unit,
    quality: String, onQualityChange: (String) -> Unit,
    progress: String, onProgressChange: (String) -> Unit,
    schedule: String, onScheduleChange: (String) -> Unit,
    inspectorName: String,
    inspectorTitle: String, onInspectorTitleChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(LocalizationManager.t("manual_sir_title"), style = MaterialTheme.typography.titleLarge, color = Color(0xFF278DAD))

            OutlinedTextField(
                projectName,
                { onProjectNameChange(it.inspectionText(500)) },
                label = { Text(LocalizationManager.t("sir_project_name")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            SirSectionTitle(LocalizationManager.t("sir_contractor_section"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(contractor, { onContractorChange(it.inspectionText(300)) }, label = { Text("${LocalizationManager.t("contractor")} *") }, modifier = Modifier.weight(1f), singleLine = true)
                OmsDateField(date, onDateChange, LocalizationManager.t("date_label"), Modifier.weight(1f), required = true)
            }

            SirSectionTitle(LocalizationManager.t("sir_representatives"))
            OutlinedTextField(contractorRepresentative, { onContractorRepresentativeChange(it.inspectionText(300)) }, label = { Text(LocalizationManager.t("sir_contractor_representative")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(qaStaff, { onQaStaffChange(it.inspectionText(300)) }, label = { Text(LocalizationManager.t("sir_qa_staff")) }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(usifRepresentative, { onUsifRepresentativeChange(it.inspectionText(300)) }, label = { Text(LocalizationManager.t("sir_usif_representative")) }, modifier = Modifier.weight(1f), singleLine = true)
            }

            SirSectionTitle(LocalizationManager.t("sir_personnel_weather"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(skilledLabor, { onSkilledLaborChange(it.filter(Char::isDigit).take(6)) }, label = { Text(LocalizationManager.t("sir_skilled_labor")) }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(unskilledLabor, { value -> onUnskilledLaborChange(value.filter { it.isDigit() || it == '-' }.take(7).takeIf { it == "-" || it.all(Char::isDigit) } ?: unskilledLabor) }, label = { Text(LocalizationManager.t("sir_unskilled_labor")) }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(siteManagement, { onSiteManagementChange(it.inspectionText(300)) }, label = { Text(LocalizationManager.t("sir_site_management")) }, modifier = Modifier.weight(1f), singleLine = true)
            }
            WeatherPicker(weatherCondition, onWeatherConditionChange, temperatureCelsius, onTemperatureCelsiusChange)

            SirSectionTitle(LocalizationManager.t("sir_ongoing_activities"))
            RepeatableManualActivities(activities, onActivitiesChange)
            SirSectionTitle(LocalizationManager.t("sir_ongoing_observations"))
            RepeatableSirRows(ongoingObservations, onOngoingObservationsChange, LocalizationManager.t("sir_one_per_line"))

            SirSectionTitle(LocalizationManager.t("sir_hse_observations"))
            hseObservations.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = item.isYes, onCheckedChange = { checked -> onHseObservationsChange(hseObservations.mapIndexed { current, value -> if (current == index) value.copy(isYes = checked) else value }) })
                    Text(LocalizationManager.hseObservation(item.observation), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(item.comment, { comment -> onHseObservationsChange(hseObservations.mapIndexed { current, value -> if (current == index) value.copy(comment = comment.inspectionText(2_000)) else value }) }, label = { Text(LocalizationManager.t("comment")) }, maxLines = 3, modifier = Modifier.widthIn(min = 220.dp).weight(1f))
                }
            }

            SirSectionTitle(LocalizationManager.t("sir_quality_assessment"))
            OutlinedTextField(quality, { onQualityChange(it.inspectionText(8_000)) }, label = { Text(LocalizationManager.t("sir_quality_hint")) }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(progress, { onProgressChange(it.inspectionText(4_000)) }, label = { Text(LocalizationManager.t("sir_progress_comments")) }, minLines = 2, maxLines = 6, modifier = Modifier.weight(1f))
                OutlinedTextField(schedule, { onScheduleChange(it.inspectionText(4_000)) }, label = { Text(LocalizationManager.t("sir_schedule_remarks")) }, minLines = 2, maxLines = 6, modifier = Modifier.weight(1f))
            }

            SirSectionTitle(LocalizationManager.t("sir_inspector_section"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = inspectorName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(LocalizationManager.t("sir_name")) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(inspectorTitle, { onInspectorTitleChange(it.inspectionText(300)) }, label = { Text(LocalizationManager.t("sir_title_field")) }, modifier = Modifier.weight(1f), singleLine = true)
            }
        }
    }
}

@Composable
private fun RepeatableSirRows(values: List<String>, onChange: (List<String>) -> Unit, label: String) {
    values.forEachIndexed { index, value ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value, { text -> onChange(values.mapIndexed { current, item -> if (current == index) text.inspectionText(8_000) else item }) }, label = { Text(label) }, minLines = 2, maxLines = 6, modifier = Modifier.weight(1f))
            if (values.size > 1) TableActionIconButton(LocalizationManager.t("delete"), Icons.Default.Remove) { onChange(values.filterIndexed { current, _ -> current != index }) }
        }
    }
    TableActionIconButton(LocalizationManager.t("add"), Icons.Default.Add) { onChange(values + "") }
}

private enum class WeatherCondition(val localizationKey: String, val workbookValue: String) {
    SUNNY("weather_sunny", "Sunny"),
    CLOUDY("weather_cloudy", "Cloudy"),
    RAIN("weather_rain", "Rain"),
    SNOW("weather_snow", "Snow");

    companion object {
        fun fromWorkbookValue(value: String?): WeatherCondition? =
            entries.firstOrNull { condition -> value.orEmpty().contains(condition.workbookValue, ignoreCase = true) }
    }
}

private fun WeatherCondition?.toWeatherWorkbookValue(temperatureCelsius: String): String? {
    val normalizedTemperature = temperatureCelsius.replace(',', '.').trim()
    val temperature = normalizedTemperature.takeIf { it.isNotBlank() }?.let { "$it °C" }
    val condition = this?.workbookValue
    return listOfNotNull(condition, temperature).joinToString(", ").ifBlank { null }
}

private fun String?.workbookTemperature(): String =
    this.orEmpty().substringAfter(',', "").replace("°C", "").trim().temperatureInput()

@Composable
private fun WeatherPicker(
    selected: WeatherCondition?,
    onSelect: (WeatherCondition?) -> Unit,
    temperatureCelsius: String,
    onTemperatureChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(LocalizationManager.t("sir_weather_conditions"), style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeatherCondition.entries.forEach { condition ->
                    FilterChip(
                        selected = selected == condition,
                        onClick = { onSelect(if (selected == condition) null else condition) },
                        label = { Text(LocalizationManager.t(condition.localizationKey)) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (condition) {
                                    WeatherCondition.SUNNY -> Icons.Default.WbSunny
                                    WeatherCondition.CLOUDY -> Icons.Default.Cloud
                                    WeatherCondition.RAIN -> Icons.Default.WaterDrop
                                    WeatherCondition.SNOW -> Icons.Default.AcUnit
                                },
                                contentDescription = LocalizationManager.t(condition.localizationKey)
                            )
                        }
                    )
                }
            }
            OutlinedTextField(
                value = temperatureCelsius,
                onValueChange = { value -> onTemperatureChange(value.temperatureInput()) },
                label = { Text(LocalizationManager.t("temperature_celsius")) },
                singleLine = true,
                modifier = Modifier.width(170.dp)
            )
        }
    }
}

private fun String.temperatureInput(): String {
    val normalized = replace(',', '.')
    val result = StringBuilder()
    normalized.forEach { character ->
        when {
            character.isDigit() -> result.append(character)
            (character == '-' || character == '+') && result.isEmpty() -> result.append(character)
            character == '.' && '.' !in result -> result.append(character)
        }
    }
    return result.toString().take(7)
}

private data class ManualActivityInput(
    val location: String = "",
    val description: String = "",
    val onSchedule: String = "no",
    val remarks: String = ""
)

@Composable
private fun RepeatableManualActivities(
    values: List<ManualActivityInput>,
    onChange: (List<ManualActivityInput>) -> Unit
) {
    fun update(index: Int, transform: (ManualActivityInput) -> ManualActivityInput) {
        onChange(values.mapIndexed { current, item -> if (current == index) transform(item) else item })
    }
    values.forEachIndexed { index, activity ->
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBFC))) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(activity.location, { value -> update(index) { it.copy(location = value.inspectionText(500)) } }, label = { Text(LocalizationManager.t("sir_activity_location")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(activity.description, { value -> update(index) { it.copy(description = value.inspectionText(2_000)) } }, label = { Text(LocalizationManager.t("description")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(LocalizationManager.t("sir_on_schedule"), style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 260.dp).fillMaxWidth()) {
                            listOf("yes", "no").forEachIndexed { optionIndex, option ->
                                SegmentedButton(
                                    selected = activity.onSchedule.equals(option, ignoreCase = true),
                                    onClick = { update(index) { it.copy(onSchedule = option) } },
                                    shape = SegmentedButtonDefaults.itemShape(optionIndex, 2),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = MaterialTheme.colorScheme.primary,
                                        activeContentColor = Color.White
                                    ),
                                    icon = {},
                                    label = { Text(LocalizationManager.t(option)) }
                                )
                            }
                        }
                    }
                    if (values.size > 1) TableActionIconButton(LocalizationManager.t("delete"), Icons.Default.Remove) { onChange(values.filterIndexed { current, _ -> current != index }) }
                }
                OutlinedTextField(activity.remarks, { value -> update(index) { it.copy(remarks = value.inspectionText(2_000)) } }, label = { Text(LocalizationManager.t("sir_activity_remarks")) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    TableActionIconButton(LocalizationManager.t("add"), Icons.Default.Add) { onChange(values + ManualActivityInput()) }
}

private data class HseObservationInput(val observation: String, val isYes: Boolean = false, val comment: String = "")
private val defaultHseObservations = listOf(
    "All workers wear PPE equipment as relevant.",
    "The fire shield / firefighting equipment is present at site.",
    "The site is appropriately fenced.",
    "There is lavatories on the site.",
    "There are safety briefing logs.",
    "Safety information plate is in tact."
).map(::HseObservationInput)

@Composable
private fun SirSectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().background(Color(0xFFE3F2F7), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelLarge,
        color = Color(0xFF176B84)
    )
}

@Composable
private fun InspectionProjectSelector(
    projects: List<Project>,
    loadProjectsOnOpen: suspend () -> List<Project>,
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
        ProjectLevelDropdown(
            LocalizationManager.t("select_project"), rootProjects, selectedProjectUuid, onProjectSelect,
            loadOptionsOnOpen = { loadProjectsOnOpen().filter { it.projectType.equals("project", true) } }
        )
        ProjectLevelDropdown(
            LocalizationManager.t("sir_subproject_number"), subprojects, selectedSubprojectUuid, onSubprojectSelect,
            enabled = subprojects.isNotEmpty(), searchable = true, codeOnly = true
        )
        ProjectLevelDropdown(LocalizationManager.t("select_subproject_part"), parts, selectedSubprojectPartUuid, onSubprojectPartSelect, enabled = parts.isNotEmpty())
    }
}

@Composable
private fun ProjectLevelDropdown(
    label: String,
    options: List<Project>,
    selectedUuid: String?,
    onSelect: (String?) -> Unit,
    enabled: Boolean = true,
    searchable: Boolean = false,
    codeOnly: Boolean = false,
    loadOptionsOnOpen: (suspend () -> List<Project>)? = null
) {
    val selected = options.firstOrNull { it.id == selectedUuid }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        val itemLabel: (Project) -> String = {
            if (codeOnly) it.siteNumber
            else if (it.siteNumber.equals(it.name, ignoreCase = true)) it.name
            else "${it.siteNumber} — ${it.name}"
        }
        if (searchable) {
            SearchableOptionPicker(
                options, selected, label, { onSelect(it.id) }, itemLabel,
                enabled = enabled, loadOptionsOnInput = loadOptionsOnOpen
            )
        } else {
            InlineOptionPicker(
                options = options, selected = selected, prompt = label, onSelect = { onSelect(it.id) },
                itemLabel = itemLabel, enabled = enabled, loadOptionsOnOpen = loadOptionsOnOpen
            )
        }
    }
}

private fun String.toManualHseObservations() = lines().map(String::trim).filter(String::isNotBlank).map { line ->
    val fields = line.split('|').map(String::trim)
    oms.data.ManualHseObservationRequest(fields.firstOrNull().orEmpty(), fields.getOrNull(1)?.ifBlank { null }, fields.getOrNull(2)?.ifBlank { null })
}

private fun String.toManualQualityRemarks() = lines().map(String::trim).filter(String::isNotBlank).map { line ->
    val fields = line.split('|').map(String::trim)
    oms.data.ManualRemarkRequest(fields.firstOrNull().orEmpty(), fields.getOrNull(1)?.ifBlank { null })
}

private fun String.isIsoDate(): Boolean = matches(Regex("\\d{4}-\\d{2}-\\d{2}"))

/** Bounds pasted text before Compose measures it, keeping the inspection form responsive. */
private fun String.inspectionText(maxLength: Int): String = replace("\u0000", "").take(maxLength)

private fun buildInspectionSummary(type: InspectionType, comments: String, latitude: String, longitude: String): String = buildString {
    append("[${type.name.lowercase()}]")
    comments.trim().takeIf { it.isNotEmpty() }?.let { append(" $it") }
    if (latitude.isNotBlank() || longitude.isNotBlank()) append(" | GPS: ${latitude.trim()}, ${longitude.trim()}")
}

@Composable
private fun InspectionTypeDropdown(
    value: InspectionType,
    onChange: (InspectionType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(LocalizationManager.t("inspection_type"), style = MaterialTheme.typography.labelLarge)
        InlineOptionPicker(
            options = InspectionType.entries,
            selected = value,
            prompt = LocalizationManager.t("inspection_type"),
            onSelect = onChange,
            itemLabel = { it.label }
        )
    }
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
