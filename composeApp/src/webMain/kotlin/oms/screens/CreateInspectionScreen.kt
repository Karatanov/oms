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
import kotlinx.coroutines.delay
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.data.ApiInspectionPhoto
import oms.data.omsApiUrl
import oms.localization.LocalizationManager
import oms.components.OmsDateField
import oms.components.InlineOptionPicker
import oms.components.SearchableOptionPicker
import oms.components.currentIsoDate
import oms.components.buttonHandCursor
import oms.model.Project
import kotlin.js.JsName
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.LocationOn
import oms.components.PageHeading
import oms.components.TableActionIconButton
import oms.components.NativePaneAnchor
import oms.components.FormSectionTitle
import oms.components.AutocompleteField
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@JsName("openSirImportDialog")
external fun openSirImportDialog(projectUuid: String, onComplete: (String) -> Unit)

@JsName("openInspectionPhotoPicker")
external fun openInspectionPhotoPicker(activityKey: String, onSelectionChanged: (Int) -> Unit)

@JsName("uploadSelectedInspectionPhotos")
external fun uploadSelectedInspectionPhotos(reportUuid: String, onComplete: (String) -> Unit)

@JsName("showPendingInspectionPhotoPreviews")
external fun showPendingInspectionPhotoPreviews(activityKey: String, onSelectionChanged: (Int) -> Unit)

@JsName("hidePendingInspectionPhotoPreviews")
external fun hidePendingInspectionPhotoPreviews(activityKey: String)

@JsName("setPendingInspectionPhotoDescription")
external fun setPendingInspectionPhotoDescription(activityKey: String, description: String)

@JsName("showInspectionActivityPhotoGallery")
external fun showInspectionActivityPhotoGallery(activityKey: String, photosJson: String)

@JsName("hideInspectionActivityPhotoGallery")
external fun hideInspectionActivityPhotoGallery(activityKey: String)

@JsName("openInspectionPhotoLightbox")
external fun openInspectionPhotoLightbox(photosJson: String)

@JsName("hideAllInspectionNativePanes")
external fun hideAllInspectionNativePanes()

@Composable
fun CreateInspectionScreen(
    isEditMode: Boolean = false,
    editingReportUuid: String? = null,
    currentUserName: String = "",
    isAdmin: Boolean = false,
    canChangeReportStatus: Boolean = false,
    readOnly: Boolean = false,
    rejectedReason: String? = null,
    onSaveDraft: () -> Unit = {},
    onSubmit: () -> Unit = {},
    onImportXls: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val isManualEdit = editingReportUuid != null
    var date by remember { mutableStateOf(currentIsoDate()) }
    var inspectionType by remember { mutableStateOf(InspectionType.PLANNED) }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf(TextFieldValue("")) }
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
    var mctdRepresentative by remember { mutableStateOf("") }
    var skilledLabor by remember { mutableStateOf("") }
    var unskilledLabor by remember { mutableStateOf("") }
    var siteManagement by remember { mutableStateOf("") }
    var weatherCondition by remember { mutableStateOf<WeatherCondition?>(null) }
    var temperatureCelsius by remember { mutableStateOf("") }
    var activities by remember { mutableStateOf(listOf(ManualActivityInput(photoKey = "activity-0"))) }
    // Materials are optional. A row is created only when the user explicitly
    // asks to add one, so a new SIR starts without an empty materials record.
    var purchasedMaterials by remember { mutableStateOf(emptyList<PurchasedMaterialInput>()) }
    var projectName by remember { mutableStateOf("UNDP") }
    var siteAddress by remember { mutableStateOf("") }
    var ongoingObservations by remember { mutableStateOf(listOf(OngoingObservationInput(photoKey = "observation-0"))) }
    var hseObservations by remember { mutableStateOf(defaultHseObservations) }
    var qualityRemarks by remember { mutableStateOf(listOf(QualityRemarkInput(photoKey = "quality-0"))) }
    var progressComment by remember { mutableStateOf("") }
    var scheduleRemark by remember { mutableStateOf("") }
    var inspectorTitle by remember { mutableStateOf("") }
    var reportStatus by remember { mutableStateOf("draft") }
    var loadingManualReport by remember(editingReportUuid) { mutableStateOf(isManualEdit) }
    var reportPhotos by remember(editingReportUuid) { mutableStateOf<List<ApiInspectionPhoto>?>(null) }
    val scope = rememberCoroutineScope()
    val pageScrollState = rememberScrollState()
    fun scrollBy(delta: Float) = scope.launch { pageScrollState.animateScrollBy(delta) }

    // Native photo panes belong to the document body, not Compose's tree. A
    // stale pane from an earlier form must never survive navigation and cover
    // this screen. Read-only and editing forms now use on-demand lightboxes.
    DisposableEffect(Unit) {
        hideAllInspectionNativePanes()
        onDispose { hideAllInspectionNativePanes() }
    }

    // The report's QA staff is normally the person creating it.  Preserve a
    // manually selected value, but recover the default if the user context
    // becomes available after the screen has first composed.
    LaunchedEffect(currentUserName) {
        if (qaStaff.isBlank()) qaStaff = currentUserName
        if (inspectorName.isBlank()) inspectorName = currentUserName
    }

    val projects = ProjectRepository.projects
    val rootProjects = projects.filter { it.projectType.equals("project", true) }

    // The current programme has one root project.  Preselect it as soon as
    // the lightweight shared project snapshot is available, while preserving
    // the explicit separate choice of subproject and subproject part.
    LaunchedEffect(isManualEdit) {
        if (!isManualEdit) ProjectRepository.refresh()
    }
    LaunchedEffect(isManualEdit, rootProjects) {
        if (!isManualEdit && selectedProjectUuid == null && rootProjects.size == 1) {
            selectedProjectUuid = rootProjects.single().id
        }
    }

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
            // Reports are opened from the register, which already has the
            // shared project snapshot.  Refetch only after a hard reload;
            // otherwise previewing a report needlessly waits for a second
            // large project-list request before its own SIR can render.
            if (ProjectRepository.projects.isEmpty()) ProjectRepository.refresh()
            OmsApiClient.manualInspectionReport(reportUuid)
        }.onSuccess { editor ->
            val manual = editor.manual
            val projectsById = ProjectRepository.projects.associateBy { it.id }
            val target = projectsById[editor.projectUuid]
            val ancestry = target?.let {
                // A report target can have at most project → subproject →
                // subproject part.  Cap traversal explicitly: a malformed
                // historical parent reference must never turn opening a
                // read-only report into an infinite Compose coroutine.
                generateSequence(it) { current -> current.parentProjectUuid?.let(projectsById::get) }
                    .take(3)
                    .toList().asReversed()
            }.orEmpty()
            selectedProjectUuid = ancestry.getOrNull(0)?.id
            selectedSubprojectUuid = ancestry.getOrNull(1)?.id
            selectedSubprojectPartUuid = ancestry.getOrNull(2)?.id
            date = manual.inspectionDate
            reportStatus = editor.status
            inspectionType = InspectionType.entries.firstOrNull { it.name.equals(manual.inspectionType, true) } ?: InspectionType.PLANNED
            // UNDP is the current organisation and is used when an older
            // report has no organisation value.
            projectName = manual.projectName.orEmpty().ifBlank { "UNDP" }
            contractor = manual.contractor.orEmpty()
            contractorRepresentative = manual.contractorRepresentative.orEmpty()
            qaStaff = manual.qaStaff.orEmpty()
            mctdRepresentative = manual.mctdRepresentative.orEmpty()
            skilledLabor = manual.skilledLabor.orEmpty()
            unskilledLabor = manual.unskilledLabor.orEmpty()
            siteManagement = manual.siteManagement.orEmpty()
            weatherCondition = WeatherCondition.fromWorkbookValue(manual.weather)
            temperatureCelsius = manual.weather.workbookTemperature()
            activities = manual.activities.mapIndexed { index, item ->
                ManualActivityInput(
                    photoKey = "activity-$index",
                    location = item.location,
                    description = item.description,
                    onSchedule = item.onSchedule.lowercase().ifBlank { "no" },
                    remarks = item.remarks.orEmpty()
                )
            }.ifEmpty { listOf(ManualActivityInput(photoKey = "activity-0")) }
            purchasedMaterials = manual.purchasedMaterials.mapIndexed { index, item ->
                PurchasedMaterialInput(
                    photoKey = "material-$index",
                    materialsAndEquipment = item.materialsAndEquipment,
                    characteristics = item.characteristics.orEmpty(),
                    perDed = item.perDed.orEmpty().ifBlank { "no" },
                    notes = item.notes.orEmpty()
                )
            }
            ongoingObservations = manual.ongoingObservations
                .mapIndexed { index, description -> OngoingObservationInput(description, "observation-$index") }
                .ifEmpty { listOf(OngoingObservationInput(photoKey = "observation-0")) }
            hseObservations = manual.hseObservations.map {
                HseObservationInput(
                    observation = it.observation,
                    isYes = it.answer.equals("yes", true),
                    comment = it.comment.orEmpty(),
                    isCustom = it.answer.isNullOrBlank() || !it.answer.equals("yes", true) && !it.answer.equals("no", true)
                )
            }.ifEmpty { defaultHseObservations }
            qualityRemarks = manual.qualityRemarks.mapIndexed { index, remark ->
                QualityRemarkInput(photoKey = "quality-$index", work = remark.work, comment = remark.comment, rectification = remark.rectification.orEmpty(), status = remark.status.orEmpty())
            }.ifEmpty { listOf(QualityRemarkInput(photoKey = "quality-0")) }
            progressComment = manual.progressComment.orEmpty()
            scheduleRemark = manual.scheduleRemark.orEmpty()
            // `NAME` in the workbook is the person signing this revision.
            // When an authorised user edits an imported SIR, it must reflect
            // the current editor rather than retain the historical template
            // value (for example, the original Pawel Neugebauer entry).
            inspectorName = if (!readOnly && currentUserName.isNotBlank()) currentUserName
            else manual.inspectorName.ifBlank { currentUserName }
            inspectorTitle = manual.inspectorTitle.orEmpty()
            latitude = manual.latitude?.toString().orEmpty()
            longitude = manual.longitude?.toString().orEmpty()
            entryMode = "manual"
        }.onFailure { failure ->
            errorMessage = LocalizationManager.t("error_update_report").replace("{message}", failure.message ?: LocalizationManager.t("unknown_error"))
        }
        loadingManualReport = false
    }

    // Photos are evidence attached to the report rather than cells in its
    // workbook, so load them independently from the manual-report payload.
    // This keeps them viewable even when the original SIR was imported.
    // Opening a large imported XLSX already parses the workbook.  Do not ask
    // the server to extract and hydrate all embedded images at the same time:
    // render the report first, then let photos arrive independently.
    LaunchedEffect(editingReportUuid, loadingManualReport) {
        val reportUuid = editingReportUuid ?: return@LaunchedEffect
        if (loadingManualReport) return@LaunchedEffect
        // Give the read-only form one paint frame before the optional,
        // potentially expensive embedded-photo hydration starts. This keeps
        // the report navigable even when an older XLSX contains many images.
        delay(250)
        reportPhotos = runCatching {
            OmsApiClient.inspectionPhotos(reportUuid)
        }.getOrDefault(emptyList())
    }

    // The SIR workbook stores the code and address in a single cell.  Keep
    // them separate in the form and load the address from the selected
    // subproject whenever it is available.
    LaunchedEffect(selectedSubprojectUuid, loadingManualReport) {
        val subprojectUuid = selectedSubprojectUuid
        if (subprojectUuid == null) {
            siteAddress = ""
        } else if (!loadingManualReport) {
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
        PageHeading(
            if (readOnly) LocalizationManager.t("report_preview")
            else if (isEditMode || isManualEdit) LocalizationManager.t("edit_inspection")
            else LocalizationManager.t("create_inspection"),
            Icons.Default.FactCheck
        ) {
            if (readOnly) OutlinedButton(onClick = onCancel) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(LocalizationManager.t("return_to_reports"))
            }
        }

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

        if (readOnly) ReadOnlyInspectionLocation(
            projects, selectedProjectUuid, selectedSubprojectUuid, selectedSubprojectPartUuid,
            siteAddress, inspectionType, reportStatus
        ) else Card(
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
                FormSectionTitle(LocalizationManager.t("sir_header_site"), Icons.Default.LocationOn)
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
                    Text(LocalizationManager.t("status"), style = MaterialTheme.typography.labelLarge)
                    InlineOptionPicker(
                        options = listOf("draft", "pending_review", "completed"), selected = reportStatus,
                        prompt = LocalizationManager.t("status"), onSelect = { reportStatus = it },
                        itemLabel = { value -> LocalizationManager.t("${value}_status") }
                    )
                }
            }
        }

        // Avoid composing a full report full of temporary default controls
        // while an imported workbook is still being parsed.
        if (!loadingManualReport && entryMode == "manual") {
            if (readOnly) ReadOnlySirReport(
                oms.data.ManualInspectionReportRequest(
                    inspectionDate = date,
                    inspectionType = inspectionType.name.lowercase(),
                    contractor = contractor,
                    contractorRepresentative = contractorRepresentative.ifBlank { null },
                    projectName = projectName.ifBlank { null },
                    siteReference = siteReference.ifBlank { null },
                    qaStaff = qaStaff.ifBlank { null },
                    mctdRepresentative = mctdRepresentative.ifBlank { null },
                    skilledLabor = skilledLabor.ifBlank { null },
                    unskilledLabor = unskilledLabor.ifBlank { null },
                    siteManagement = siteManagement.ifBlank { null },
                    weather = weatherCondition.toWeatherWorkbookValue(temperatureCelsius),
                    activities = activities.map { oms.data.ManualActivityRequest(it.location, it.description, it.onSchedule, it.remarks.ifBlank { null }) },
                    purchasedMaterials = purchasedMaterials.map { oms.data.ManualPurchasedMaterialRequest(it.materialsAndEquipment, it.characteristics.ifBlank { null }, it.perDed.ifBlank { "no" }, it.notes.ifBlank { null }) },
                    ongoingObservations = ongoingObservations.map { it.description.trim() }.filter(String::isNotBlank),
                    hseObservations = hseObservations.map { oms.data.ManualHseObservationRequest(it.observation, it.answer(), it.comment.ifBlank { null }) },
                    qualityRemarks = qualityRemarks.map { oms.data.ManualRemarkRequest(it.work, it.comment, it.rectification.ifBlank { null }, it.status.ifBlank { null }) },
                    progressComment = progressComment.ifBlank { null },
                    scheduleRemark = scheduleRemark.ifBlank { null },
                    inspectorName = inspectorName,
                    inspectorTitle = inspectorTitle.ifBlank { null },
                    latitude = latitude.replace(',', '.').toDoubleOrNull(),
                    longitude = longitude.replace(',', '.').toDoubleOrNull()
                ), reportPhotos.orEmpty()
            ) else ManualSirForm(
            date = date, onDateChange = { date = it }, projectName = projectName, onProjectNameChange = { projectName = it },
            contractor = contractor, onContractorChange = { contractor = it },
            contractorRepresentative = contractorRepresentative, onContractorRepresentativeChange = { contractorRepresentative = it },
            qaStaff = qaStaff, onQaStaffChange = { qaStaff = it }, mctdRepresentative = mctdRepresentative, onMctdRepresentativeChange = { mctdRepresentative = it },
            skilledLabor = skilledLabor, onSkilledLaborChange = { skilledLabor = it }, unskilledLabor = unskilledLabor, onUnskilledLaborChange = { unskilledLabor = it },
            siteManagement = siteManagement, onSiteManagementChange = { siteManagement = it }, weatherCondition = weatherCondition, onWeatherConditionChange = { weatherCondition = it }, temperatureCelsius = temperatureCelsius, onTemperatureCelsiusChange = { temperatureCelsius = it },
            activities = activities, onActivitiesChange = { activities = it }, ongoingObservations = ongoingObservations, onOngoingObservationsChange = { ongoingObservations = it },
            purchasedMaterials = purchasedMaterials, onPurchasedMaterialsChange = { purchasedMaterials = it },
            hseObservations = hseObservations, onHseObservationsChange = { hseObservations = it }, qualityRemarks = qualityRemarks, onQualityRemarksChange = { qualityRemarks = it },
            progress = progressComment, onProgressChange = { progressComment = it }, schedule = scheduleRemark, onScheduleChange = { scheduleRemark = it },
            inspectorName = inspectorName, onInspectorNameChange = { inspectorName = it },
            inspectorTitle = inspectorTitle, onInspectorTitleChange = { inspectorTitle = it },
            attachedPhotos = reportPhotos.orEmpty()
            )
        }

        if (!readOnly) Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            OutlinedButton(
                enabled = !isSubmitting && !loadingManualReport,
                onClick = onCancel
            ) {
                Text(LocalizationManager.t("cancel"))
            }

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
                        else if (!temperatureCelsius.isEarthTemperatureInput()) errorMessage = LocalizationManager.t("error_temperature_range")
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
                                            mctdRepresentative = mctdRepresentative.ifBlank { null },
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
                                            purchasedMaterials = purchasedMaterials.mapNotNull { material ->
                                                material.takeIf { it.materialsAndEquipment.isNotBlank() || it.characteristics.isNotBlank() || it.notes.isNotBlank() }
                                                    ?.let { oms.data.ManualPurchasedMaterialRequest(it.materialsAndEquipment.trim(), it.characteristics.trim().ifBlank { null }, it.perDed.trim().ifBlank { "no" }, it.notes.trim().ifBlank { null }) }
                                            },
                                            ongoingObservations = ongoingObservations.map { it.description.trim() }.filter(String::isNotBlank),
                                            hseObservations = hseObservations.mapNotNull { item ->
                                                item.observation.trim().takeIf(String::isNotBlank)?.let { observation ->
                                                    oms.data.ManualHseObservationRequest(observation, item.answer(), item.comment.trim().ifBlank { null })
                                                }
                                            },
                                            qualityRemarks = qualityRemarks.mapNotNull { remark ->
                                                remark.takeIf { it.work.isNotBlank() || it.comment.isNotBlank() || it.rectification.isNotBlank() || it.status.isNotBlank() }
                                                    ?.let { oms.data.ManualRemarkRequest(it.work.trim(), it.comment.trim(), it.rectification.trim().ifBlank { null }, it.status.trim().ifBlank { null }) }
                                            },
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

/** Read-only counterpart of the placement block on the edit screen. */
@Composable
private fun ReadOnlyInspectionLocation(
    projects: List<Project>,
    projectUuid: String?,
    subprojectUuid: String?,
    partUuid: String?,
    address: String,
    inspectionType: InspectionType,
    status: String
) {
    fun projectLabel(uuid: String?): String {
        val project = projects.firstOrNull { it.id == uuid } ?: return "—"
        return if (project.siteNumber.equals(project.name, true)) project.name else "${project.siteNumber} — ${project.name}"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormSectionTitle(LocalizationManager.t("sir_header_site"), Icons.Default.LocationOn)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fields = buildList {
                    add(LocalizationManager.t("project") to projectLabel(projectUuid))
                    add(LocalizationManager.t("subproject") to projectLabel(subprojectUuid))
                    // A report may be attached directly to a subproject. Do
                    // not show an empty third-level placement in that case.
                    projects.firstOrNull { it.id == partUuid }?.let {
                        add(LocalizationManager.t("subproject_part") to projectLabel(partUuid))
                    }
                    add(LocalizationManager.t("address") to address.ifBlank { "—" })
                    add(LocalizationManager.t("inspection_type") to inspectionType.label)
                    add(LocalizationManager.t("status") to LocalizationManager.t("${status}_status"))
                }
                if (maxWidth >= 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    fields.forEach { (label, value) -> ReadOnlyInspectionField(label, value, Modifier.weight(1f)) }
                } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fields.forEach { (label, value) -> ReadOnlyInspectionField(label, value, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyInspectionField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun PendingInspectionPhotoPreviews(activityKey: String, revision: Int, onSelectionChanged: (Int) -> Unit) {
    val paneId = "inspection-photo-preview-pane-$activityKey"
    NativePaneAnchor(paneId, Modifier.fillMaxWidth().height(210.dp))
    DisposableEffect(activityKey, revision, LocalizationManager.currentLanguage) {
        showPendingInspectionPhotoPreviews(activityKey, onSelectionChanged)
        onDispose { hidePendingInspectionPhotoPreviews(activityKey) }
    }
}

/** Compact evidence gallery anchored next to one current-work row. */
@Composable
internal fun InspectionActivityPhotoGallery(
    activityKey: String,
    photos: List<ApiInspectionPhoto>,
    modifier: Modifier = Modifier,
    nativeGallery: Boolean = false
) {
    val publicPhotos = photos.map { photo ->
        photo.copy(
            downloadUrl = photo.downloadUrl.toInspectionPhotoUrl(),
            thumbnailUrl = photo.thumbnailUrl.toInspectionPhotoUrl()
        )
    }
    // A native gallery is useful inside the editor, but it is an absolutely
    // positioned DOM element.  Never mount it in the read-only report: it can
    // end up above the Compose canvas after a relayout and swallow all clicks.
    if (!nativeGallery) {
        OutlinedButton(
            onClick = { openInspectionPhotoLightbox(Json.encodeToString(publicPhotos)) },
            modifier = modifier.heightIn(min = 40.dp)
        ) {
            Text("${LocalizationManager.t("photos")} (${photos.size})")
        }
        return
    }
    val paneId = "inspection-activity-photo-gallery-$activityKey"
    NativePaneAnchor(paneId, modifier.height(88.dp))
    DisposableEffect(activityKey, photos, LocalizationManager.currentLanguage) {
        showInspectionActivityPhotoGallery(activityKey, Json.encodeToString(publicPhotos))
        onDispose { hideInspectionActivityPhotoGallery(activityKey) }
    }
}

private fun String.toInspectionPhotoUrl(): String =
    if (startsWith("http://") || startsWith("https://")) this else omsApiUrl(this)

@Composable
private fun ManualSirForm(
    date: String, onDateChange: (String) -> Unit,
    projectName: String, onProjectNameChange: (String) -> Unit,
    contractor: String, onContractorChange: (String) -> Unit,
    contractorRepresentative: String, onContractorRepresentativeChange: (String) -> Unit,
    qaStaff: String, onQaStaffChange: (String) -> Unit,
    mctdRepresentative: String, onMctdRepresentativeChange: (String) -> Unit,
    skilledLabor: String, onSkilledLaborChange: (String) -> Unit,
    unskilledLabor: String, onUnskilledLaborChange: (String) -> Unit,
    siteManagement: String, onSiteManagementChange: (String) -> Unit,
    weatherCondition: WeatherCondition?, onWeatherConditionChange: (WeatherCondition?) -> Unit,
    temperatureCelsius: String, onTemperatureCelsiusChange: (String) -> Unit,
    activities: List<ManualActivityInput>, onActivitiesChange: (List<ManualActivityInput>) -> Unit,
    purchasedMaterials: List<PurchasedMaterialInput>, onPurchasedMaterialsChange: (List<PurchasedMaterialInput>) -> Unit,
    ongoingObservations: List<OngoingObservationInput>, onOngoingObservationsChange: (List<OngoingObservationInput>) -> Unit,
    hseObservations: List<HseObservationInput>, onHseObservationsChange: (List<HseObservationInput>) -> Unit,
    qualityRemarks: List<QualityRemarkInput>, onQualityRemarksChange: (List<QualityRemarkInput>) -> Unit,
    progress: String, onProgressChange: (String) -> Unit,
    schedule: String, onScheduleChange: (String) -> Unit,
    inspectorName: String, onInspectorNameChange: (String) -> Unit,
    inspectorTitle: String, onInspectorTitleChange: (String) -> Unit,
    attachedPhotos: List<ApiInspectionPhoto>
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SirFormSection(LocalizationManager.t("sir_report_header"), Icons.Default.Description) {
            Text(LocalizationManager.t("manual_sir_title"), style = MaterialTheme.typography.titleLarge, color = Color(0xFF278DAD))
            AutocompleteField(
                value = projectName,
                onValueChange = { onProjectNameChange(it.inspectionText(500)) },
                label = LocalizationManager.t("sir_inspection_organisation"),
                options = listOf(
                    "UNDP" to "UNDP"
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(contractor, { onContractorChange(it.inspectionText(300)) }, label = { Text("${LocalizationManager.t("contractor")} *") }, modifier = Modifier.weight(1f), singleLine = true)
                OmsDateField(date, onDateChange, LocalizationManager.t("date_label"), Modifier.weight(1f), required = true)
            }
        }

        SirFormSection(LocalizationManager.t("sir_representatives"), Icons.Default.Engineering) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 900.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(contractorRepresentative, { onContractorRepresentativeChange(it.inspectionText(300)) }, label = { Text(LocalizationManager.t("sir_contractor_representative")) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(qaStaff, { onQaStaffChange(it.inspectionText(300)) }, label = { Text(LocalizationManager.t("sir_qa_staff")) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(mctdRepresentative, { onMctdRepresentativeChange(it.inspectionText(300)) }, label = { Text(LocalizationManager.t("sir_mctd_representative")) }, modifier = Modifier.weight(1f), singleLine = true)
                } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(contractorRepresentative, { onContractorRepresentativeChange(it.inspectionText(300)) }, label = { Text(LocalizationManager.t("sir_contractor_representative")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(qaStaff, { onQaStaffChange(it.inspectionText(300)) }, label = { Text(LocalizationManager.t("sir_qa_staff")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(mctdRepresentative, { onMctdRepresentativeChange(it.inspectionText(300)) }, label = { Text(LocalizationManager.t("sir_mctd_representative")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }
        }

        SirFormSection(LocalizationManager.t("sir_personnel_weather"), Icons.Default.WbSunny) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(skilledLabor, { onSkilledLaborChange(it.filter(Char::isDigit).take(6)) }, label = { Text(LocalizationManager.t("sir_skilled_labor")) }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(unskilledLabor, { value -> onUnskilledLaborChange(value.filter { it.isDigit() || it == '-' }.take(7).takeIf { it == "-" || it.all(Char::isDigit) } ?: unskilledLabor) }, label = { Text(LocalizationManager.t("sir_unskilled_labor")) }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(siteManagement, { onSiteManagementChange(it.inspectionText(300)) }, label = { Text(LocalizationManager.t("sir_site_management")) }, modifier = Modifier.weight(1f), singleLine = true)
            }
            WeatherPicker(weatherCondition, onWeatherConditionChange, temperatureCelsius, onTemperatureCelsiusChange)
        }

        SirFormSection(LocalizationManager.t("sir_ongoing_activities"), Icons.Default.Engineering) {
            RepeatableManualActivities(activities, onActivitiesChange, attachedPhotos)
        }

        SirFormSection(LocalizationManager.t("sir_ongoing_observations"), Icons.Default.FactCheck) {
            RepeatableOngoingObservations(ongoingObservations, onOngoingObservationsChange)
        }

        SirFormSection(LocalizationManager.t("sir_hse_observations"), Icons.Default.FactCheck) {
            hseObservations.forEachIndexed { index, item ->
                if (item.isCustom) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            item.observation,
                            { value -> onHseObservationsChange(hseObservations.mapIndexed { current, row -> if (current == index) row.copy(observation = value.inspectionText(2_000)) else row }) },
                            label = { Text(LocalizationManager.t("custom_hse_observation")) },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.weight(1f)
                        )
                        TableActionIconButton(LocalizationManager.t("delete"), Icons.Default.Remove) {
                            onHseObservationsChange(hseObservations.filterIndexed { current, _ -> current != index })
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = item.isYes, onCheckedChange = { checked -> onHseObservationsChange(hseObservations.mapIndexed { current, value -> if (current == index) value.copy(isYes = checked) else value }) })
                        Text(LocalizationManager.hseObservation(item.observation), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(item.comment, { comment -> onHseObservationsChange(hseObservations.mapIndexed { current, value -> if (current == index) value.copy(comment = comment.inspectionText(2_000)) else value }) }, label = { Text(LocalizationManager.t("comment")) }, maxLines = 3, modifier = Modifier.widthIn(min = 220.dp).weight(1f))
                    }
                }
            }
            TableActionIconButton(LocalizationManager.t("add_custom_hse_observation"), Icons.Default.Add) {
                onHseObservationsChange(hseObservations + HseObservationInput(observation = "", isCustom = true))
            }
        }

        SirFormSection(LocalizationManager.t("sir_quality_assessment"), Icons.Default.FactCheck) {
            RepeatableQualityRemarks(qualityRemarks, onQualityRemarksChange)
        }

        SirFormSection(LocalizationManager.t("sir_progress_assessment"), Icons.Default.FactCheck) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(progress, { onProgressChange(it.inspectionText(4_000)) }, label = { Text(LocalizationManager.t("sir_progress_comments")) }, minLines = 2, maxLines = 6, modifier = Modifier.weight(1f))
                OutlinedTextField(schedule, { onScheduleChange(it.inspectionText(4_000)) }, label = { Text(LocalizationManager.t("sir_schedule_remarks")) }, minLines = 2, maxLines = 6, modifier = Modifier.weight(1f))
            }
        }

        SirFormSection(LocalizationManager.t("sir_purchased_materials"), Icons.Default.Description) {
            RepeatablePurchasedMaterials(purchasedMaterials, onPurchasedMaterialsChange, attachedPhotos)
        }

        SirFormSection(LocalizationManager.t("sir_inspector_section"), Icons.Default.Description) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = inspectorName,
                    onValueChange = { onInspectorNameChange(it.inspectionText(300)) },
                    label = { Text(LocalizationManager.t("sir_name")) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(inspectorTitle, { onInspectorTitleChange(it.inspectionText(300)) }, label = { Text(LocalizationManager.t("sir_title_field")) }, modifier = Modifier.weight(1f), singleLine = true)
            }
        }

    }
}

/** Matches the light, icon-led sections used in subproject details. */
@Composable
private fun SirFormSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormSectionTitle(title, icon)
            content()
        }
    }
}

private data class OngoingObservationInput(
    val description: String = "",
    val photoKey: String = "observation-${kotlin.random.Random.nextInt()}-${kotlin.random.Random.nextInt()}",
    val photoCount: Int = 0,
    val photoRevision: Int = 0
)

/**
 * The reference SIR's Photo Attachment sheet captions photographs with rows
 * from OBSERVANCES ON ONGOING ACTIVITIES.  Keep a stable photo key per row so
 * multiple photos remain attached to that exact observation while it is edited.
 */
@Composable
private fun RepeatableOngoingObservations(
    values: List<OngoingObservationInput>,
    onChange: (List<OngoingObservationInput>) -> Unit
) {
    fun update(index: Int, transform: (OngoingObservationInput) -> OngoingObservationInput) {
        onChange(values.mapIndexed { current, item -> if (current == index) transform(item) else item })
    }
    values.forEachIndexed { index, observation ->
        LaunchedEffect(observation.photoKey, observation.description) {
            setPendingInspectionPhotoDescription(observation.photoKey, observation.description)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBFC))) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        observation.description,
                        { value -> update(index) { it.copy(description = value.inspectionText(8_000)) } },
                        label = { Text(LocalizationManager.t("sir_one_per_line")) },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.weight(1f)
                    )
                    if (values.size > 1) {
                        TableActionIconButton(LocalizationManager.t("delete"), Icons.Default.Remove) {
                            onChange(values.filterIndexed { current, _ -> current != index })
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        openInspectionPhotoPicker(observation.photoKey) { count ->
                            update(index) { it.copy(photoCount = count, photoRevision = it.photoRevision + 1) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(LocalizationManager.t("add_inspection_photos")) }
                Text(
                    if (observation.photoCount == 0) LocalizationManager.t("no_photos_selected")
                    else LocalizationManager.t("photos_selected").replace("{count}", observation.photoCount.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (observation.photoCount > 0) {
                    PendingInspectionPhotoPreviews(observation.photoKey, observation.photoRevision) { count ->
                        update(index) { it.copy(photoCount = count, photoRevision = it.photoRevision + 1) }
                    }
                }
            }
        }
    }
    TableActionIconButton(LocalizationManager.t("add"), Icons.Default.Add) {
        onChange(values + OngoingObservationInput(photoKey = "observation-${values.size}"))
    }
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
    val temperature = normalizedTemperature.takeIf { it.toDoubleOrNull()?.let(::isEarthTemperature) == true }?.let { "$it °C" }
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
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
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
                OutlinedTextField(
                    value = temperatureCelsius,
                    onValueChange = { value ->
                        value.temperatureInput().takeIf { it.isEarthTemperatureInput() }?.let(onTemperatureChange)
                    },
                    label = { Text(LocalizationManager.t("temperature_celsius")) },
                    singleLine = true,
                    isError = !temperatureCelsius.isEarthTemperatureInput(),
                    supportingText = { Text(LocalizationManager.t("temperature_range_hint")) },
                    modifier = Modifier.width(170.dp)
                )
            }
        }
    }
}

internal fun String.temperatureInput(): String {
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

/** Historical terrestrial extremes rounded to practical whole-degree form. */
private const val MIN_EARTH_TEMPERATURE_C = -90.0
private const val MAX_EARTH_TEMPERATURE_C = 60.0

private fun isEarthTemperature(value: Double): Boolean = value in MIN_EARTH_TEMPERATURE_C..MAX_EARTH_TEMPERATURE_C

internal fun String.isEarthTemperatureInput(): Boolean =
    isBlank() || this in setOf("-", "+", "-.", "+.") || toDoubleOrNull()?.let(::isEarthTemperature) == true

private data class ManualActivityInput(
    val photoKey: String = "activity-${kotlin.random.Random.nextInt()}-${kotlin.random.Random.nextInt()}",
    val location: String = "",
    val description: String = "",
    val onSchedule: String = "no",
    val remarks: String = "",
    val photoCount: Int = 0,
    val photoRevision: Int = 0
)

@Composable
private fun RepeatableManualActivities(
    values: List<ManualActivityInput>,
    onChange: (List<ManualActivityInput>) -> Unit,
    attachedPhotos: List<ApiInspectionPhoto>
) {
    fun update(index: Int, transform: (ManualActivityInput) -> ManualActivityInput) {
        onChange(values.mapIndexed { current, item -> if (current == index) transform(item) else item })
    }
    values.forEachIndexed { index, activity ->
        // Imported workbooks predate the row marker.  Put those historic
        // photos beside the first current-work item rather than hiding them;
        // new uploads retain their exact activity association.
        val persistedPhotos = attachedPhotos.filter { photo ->
            photo.editorAssociationKey() == activity.photoKey ||
                (index == 0 && photo.editorAssociationKey() == null)
        }
        LaunchedEffect(activity.photoKey, activity.description) {
            setPendingInspectionPhotoDescription(activity.photoKey, activity.description)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBFC))) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 900.dp) Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        OutlinedTextField(activity.location, { value -> update(index) { it.copy(location = value.inspectionText(500)) } }, label = { Text(LocalizationManager.t("sir_activity_location")) }, modifier = Modifier.weight(1f), minLines = 2)
                        OutlinedTextField(activity.description, { value -> update(index) { it.copy(description = value.inspectionText(2_000)) } }, label = { Text(LocalizationManager.t("sir_activity_description")) }, modifier = Modifier.weight(2f), minLines = 2)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(LocalizationManager.t("sir_on_schedule"), style = MaterialTheme.typography.labelLarge)
                            ScheduleChoice(activity.onSchedule) { value -> update(index) { it.copy(onSchedule = value) } }
                        }
                        OutlinedTextField(activity.remarks, { value -> update(index) { it.copy(remarks = value.inspectionText(2_000)) } }, label = { Text(LocalizationManager.t("sir_activity_remarks")) }, modifier = Modifier.weight(1.5f), minLines = 2)
                        if (values.size > 1) TableActionIconButton(LocalizationManager.t("delete"), Icons.Default.Remove) { onChange(values.filterIndexed { current, _ -> current != index }) }
                    } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(activity.location, { value -> update(index) { it.copy(location = value.inspectionText(500)) } }, label = { Text(LocalizationManager.t("sir_activity_location")) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(activity.description, { value -> update(index) { it.copy(description = value.inspectionText(2_000)) } }, label = { Text(LocalizationManager.t("sir_activity_description")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(LocalizationManager.t("sir_on_schedule"), style = MaterialTheme.typography.labelLarge)
                                ScheduleChoice(activity.onSchedule) { value -> update(index) { it.copy(onSchedule = value) } }
                            }
                            if (values.size > 1) TableActionIconButton(LocalizationManager.t("delete"), Icons.Default.Remove) { onChange(values.filterIndexed { current, _ -> current != index }) }
                        }
                        OutlinedTextField(activity.remarks, { value -> update(index) { it.copy(remarks = value.inspectionText(2_000)) } }, label = { Text(LocalizationManager.t("sir_activity_remarks")) }, modifier = Modifier.fillMaxWidth())
                    }
                }
                OutlinedButton(
                    onClick = {
                        openInspectionPhotoPicker(activity.photoKey) { count ->
                            update(index) { it.copy(photoCount = count, photoRevision = it.photoRevision + 1) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(LocalizationManager.t("add_inspection_photos"))
                }
                Text(
                    text = if (activity.photoCount == 0) LocalizationManager.t("no_photos_selected")
                    else LocalizationManager.t("photos_selected").replace("{count}", activity.photoCount.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (activity.photoCount > 0) {
                    PendingInspectionPhotoPreviews(activity.photoKey, activity.photoRevision) { count ->
                        update(index) { it.copy(photoCount = count, photoRevision = it.photoRevision + 1) }
                    }
                }
                if (persistedPhotos.isNotEmpty()) {
                    InspectionActivityPhotoGallery(
                        activityKey = "saved-${activity.photoKey}",
                        photos = persistedPhotos,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    LocalizationManager.t("photo_upload_requirements"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    TableActionIconButton(LocalizationManager.t("sir_add_activity"), Icons.Default.Add) {
        onChange(values + ManualActivityInput(photoKey = "activity-${values.size}"))
    }
}

internal fun ApiInspectionPhoto.editorAssociationKey(): String? =
    Regex("\\[oms:([^\\]]+)\\]", RegexOption.IGNORE_CASE)
        .find(fileName.substringBeforeLast('.', fileName))
        ?.groupValues
        ?.getOrNull(1)

@Composable
private fun ScheduleChoice(selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf("yes", "no").forEachIndexed { optionIndex, option ->
            SegmentedButton(
                selected = selected.equals(option, ignoreCase = true),
                onClick = { onSelect(option) },
                modifier = Modifier.buttonHandCursor(),
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

private data class HseObservationInput(
    val observation: String,
    val isYes: Boolean = false,
    val comment: String = "",
    val isCustom: Boolean = false
) {
    fun answer(): String? = if (isCustom) null else if (isYes) "yes" else "no"
}
internal data class QualityRemarkInput(
    val photoKey: String = "quality-${kotlin.random.Random.nextInt()}-${kotlin.random.Random.nextInt()}",
    val work: String = "",
    val comment: String = "",
    val rectification: String = "",
    val status: String = "",
    val photoCount: Int = 0,
    val photoRevision: Int = 0
)
private val defaultHseObservations = listOf(
    "All workers wear PPE equipment as relevant.",
    "The fire shield / firefighting equipment is present at site.",
    "The site is appropriately fenced.",
    "There is lavatories on the site.",
    "There are safety briefing logs.",
    "Safety information plate is in tact."
).map(::HseObservationInput)

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
            enabled = subprojects.isNotEmpty(), searchable = true, codeOnly = true, showLabel = false
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
    showLabel: Boolean = true,
    loadOptionsOnOpen: (suspend () -> List<Project>)? = null
) {
    val selected = options.firstOrNull { it.id == selectedUuid }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showLabel) Text(label, style = MaterialTheme.typography.labelLarge)
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
    oms.data.ManualRemarkRequest(
        work = fields.firstOrNull().orEmpty(),
        comment = fields.getOrNull(1).orEmpty(),
        rectification = fields.getOrNull(2)?.ifBlank { null },
        status = fields.getOrNull(3)?.ifBlank { null }
    )
}

private fun String.isIsoDate(): Boolean = matches(Regex("\\d{4}-\\d{2}-\\d{2}"))

/** Bounds pasted text before Compose measures it, keeping the inspection form responsive. */
internal fun String.inspectionText(maxLength: Int): String = replace("\u0000", "").take(maxLength)

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
