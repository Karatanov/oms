package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.data.UpdateProjectRequest
import oms.model.Project
import oms.components.OmsDateField
import oms.components.ConstructionTypeSelector
import oms.components.SectorSelector
import oms.components.UkraineRegionAutocomplete
import oms.components.UkraineCityAutocomplete
import oms.components.AddressCoordinatesCalculator
import oms.localization.LocalizationManager

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EditProjectScreen(
    project: Project,
    onCancel: () -> Unit = {},
    onSaved: (Project) -> Unit = {}
) {
    var name by remember { mutableStateOf(project.name) }
    var siteName by remember { mutableStateOf("") }
    var siteNumber by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(project.region) }
    var city by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("planned") }
    var sector by remember { mutableStateOf("") }
    var constructionType by remember { mutableStateOf("") }
    var budgetPlanned by remember { mutableStateOf("") }
    var engineerConsultantContractAmount by remember { mutableStateOf("") }
    var technicalSupervisionAmount by remember { mutableStateOf("") }
    var projectType by remember { mutableStateOf("project") }
    var subprojectContractAmount by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var contractSignedDate by remember { mutableStateOf("") }
    var plannedEndDate by remember { mutableStateOf("") }
    var designContractSigningDate by remember { mutableStateOf("") }
    var constructionContractSigningDate by remember { mutableStateOf("") }
    var constructionStartDate by remember { mutableStateOf("") }
    var projectedCompletionTime by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("UAH") }
    var contractorName by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf(project.latitude.toString()) }
    var longitude by remember { mutableStateOf(project.longitude.toString()) }
    var isCalculatingCoordinates by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(project.id) {
        runCatching { OmsApiClient.projectDetails(project.id).data }
            .onSuccess { details ->
                name = details.name; siteName = details.siteName; siteNumber = details.siteNumber
                description = details.description.orEmpty()
                address = details.address; region = details.region; city = details.city
                status = details.status
                latitude = details.latitude.toString(); longitude = details.longitude.toString()
                sector = details.sector; constructionType = details.constructionType
                budgetPlanned = details.budgetPlanned.toString()
                engineerConsultantContractAmount = details.engineerConsultantContractAmount?.toString().orEmpty()
                technicalSupervisionAmount = details.technicalSupervisionAmount?.toString().orEmpty()
                projectType = details.projectType
                subprojectContractAmount = details.subprojectContractAmount?.toString().orEmpty()
                startDate = details.startDate.orEmpty(); endDate = details.endDate.orEmpty()
                contractSignedDate = details.contractSignedDate.orEmpty(); plannedEndDate = details.plannedEndDate.orEmpty()
                designContractSigningDate = details.designContractSigningDate.orEmpty()
                constructionContractSigningDate = details.constructionContractSigningDate.orEmpty()
                constructionStartDate = details.constructionStartDate.orEmpty(); projectedCompletionTime = details.projectedCompletionTime.orEmpty()
                currency = details.currency; contractorName = details.contractorName.orEmpty()
            }
            .onFailure { errorMessage = LocalizationManager.t("error_load_project") }
        isLoading = false
    }

    val allRequiredFilled = listOf(name, siteName).all { it.isNotBlank() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(LocalizationManager.t("edit_project"), style = MaterialTheme.typography.headlineMedium)
        if (isLoading) {
            CircularProgressIndicator()
            return@Column
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(LocalizationManager.t("project_name_required")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text(LocalizationManager.t("description")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    siteName,
                    { siteName = it },
                    label = { Text(LocalizationManager.t(if (projectType == "subproject_part") "subproject_part_code" else "project_code_required")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(LocalizationManager.t("status"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("planned", "active", "suspended", "completed", "archived", "dlp").forEach { value ->
                        FilterChip(selected = status == value, onClick = { status = value }, label = { Text(LocalizationManager.t("project_status_$value")) })
                    }
                }
                if (projectType != "project") {
                    Text(LocalizationManager.t(if (projectType == "subproject_part") "subproject_part_data" else "subproject_data"), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(subprojectContractAmount, { value -> if (value.all(Char::isDigit)) subprojectContractAmount = value }, label = { Text(LocalizationManager.t("subproject_contract_amount")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OmsDateField(startDate, { startDate = it }, LocalizationManager.t("start_date"), Modifier.weight(1f), true)
                        OmsDateField(endDate, { endDate = it }, LocalizationManager.t("end_date"), Modifier.weight(1f), true)
                        OmsDateField(contractSignedDate, { contractSignedDate = it }, LocalizationManager.t("contract_signed_date"), Modifier.weight(1f), true)
                        OmsDateField(plannedEndDate, { plannedEndDate = it }, LocalizationManager.t("planned_end_date"), Modifier.weight(1f), true)
                    }
                    Text(LocalizationManager.t("contract_duration_hint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(LocalizationManager.t("design_construction_dates"), style = MaterialTheme.typography.titleMedium)
                if (projectType == "project") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OmsDateField(startDate, { startDate = it }, LocalizationManager.t("start_date"), Modifier.weight(1f), true)
                        OmsDateField(endDate, { endDate = it }, LocalizationManager.t("end_date"), Modifier.weight(1f), true)
                        OmsDateField(contractSignedDate, { contractSignedDate = it }, LocalizationManager.t("contract_signed_date"), Modifier.weight(1f), true)
                        OmsDateField(plannedEndDate, { plannedEndDate = it }, LocalizationManager.t("planned_end_date"), Modifier.weight(1f), true)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OmsDateField(designContractSigningDate, { designContractSigningDate = it }, LocalizationManager.t("design_contract_date"), Modifier.weight(1f), true)
                    OmsDateField(constructionContractSigningDate, { constructionContractSigningDate = it }, LocalizationManager.t("construction_contract_date"), Modifier.weight(1f), true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OmsDateField(constructionStartDate, { constructionStartDate = it }, LocalizationManager.t("construction_start_date"), Modifier.weight(1f), true)
                    OmsDateField(projectedCompletionTime, { projectedCompletionTime = it }, LocalizationManager.t("projected_completion_date"), Modifier.weight(1f), true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(contractorName, { contractorName = it }, label = { Text(LocalizationManager.t("contractor")) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(currency, { value -> if (value.all { it.isLetter() } && value.length <= 3) currency = value.uppercase() }, label = { Text(LocalizationManager.t("currency_iso_required")) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                if (projectType != "project") {
                    OutlinedTextField(address, { address = it }, label = { Text(LocalizationManager.t("address")) }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        UkraineRegionAutocomplete(region, { region = it }, LocalizationManager.t("region"), Modifier.weight(1f), required = false)
                        UkraineCityAutocomplete(city, { city = it }, LocalizationManager.t("city"), Modifier.weight(1f), required = false)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectorSelector(sector, { sector = it }, Modifier.weight(1f))
                    ConstructionTypeSelector(constructionType, { constructionType = it }, Modifier.weight(1f))
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(LocalizationManager.t("planned_budget_hint")) } },
                    state = rememberTooltipState()
                ) {
                    OutlinedTextField(
                        budgetPlanned,
                        { value -> if (value.matches(Regex("[0-9.,]*"))) budgetPlanned = value },
                        label = { Text(LocalizationManager.t("planned_budget_required")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(engineerConsultantContractAmount, { value -> if (value.all(Char::isDigit)) engineerConsultantContractAmount = value }, label = { Text(LocalizationManager.t("engineer_consultant_amount")) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(technicalSupervisionAmount, { value -> if (value.all(Char::isDigit)) technicalSupervisionAmount = value }, label = { Text(LocalizationManager.t("technical_supervision_amount")) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                if (projectType != "project") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(latitude, { value -> if (value.matches(Regex("-?[0-9.,]*"))) latitude = value }, label = { Text(LocalizationManager.t("latitude")) }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(longitude, { value -> if (value.matches(Regex("-?[0-9.,]*"))) longitude = value }, label = { Text(LocalizationManager.t("longitude")) }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    AddressCoordinatesCalculator(
                        address = address,
                        city = city,
                        region = region,
                        calculationRequested = isCalculatingCoordinates,
                        onCalculationRequestedChange = { isCalculatingCoordinates = it },
                        onCoordinatesResolved = { resolvedLatitude, resolvedLongitude ->
                            latitude = resolvedLatitude
                            longitude = resolvedLongitude
                        },
                        onError = { errorMessage = it }
                    )
                }
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            OutlinedButton(onClick = onCancel, enabled = !isSaving) { Text(LocalizationManager.t("cancel")) }
            Button(enabled = !isSaving, onClick = {
                val budget = budgetPlanned.toLongOrNull()
                val lat = latitude.replace(',', '.').toDoubleOrNull()
                val lon = longitude.replace(',', '.').toDoubleOrNull()
                val engineerAmount = engineerConsultantContractAmount.takeIf { it.isNotBlank() }?.toLongOrNull()
                val supervisionAmount = technicalSupervisionAmount.takeIf { it.isNotBlank() }?.toLongOrNull()
                val subprojectAmount = subprojectContractAmount.takeIf { it.isNotBlank() }?.toLongOrNull()
                errorMessage = when {
                    !allRequiredFilled -> LocalizationManager.t("error_required_fields")
                    budget == null || budget <= 0 -> LocalizationManager.t("error_positive_budget")
                    lat != null && lat !in -90.0..90.0 -> LocalizationManager.t("error_latitude_range")
                    lon != null && lon !in -180.0..180.0 -> LocalizationManager.t("error_longitude_range")
                    engineerConsultantContractAmount.isNotBlank() && engineerAmount == null -> LocalizationManager.t("error_engineer_amount")
                    technicalSupervisionAmount.isNotBlank() && supervisionAmount == null -> LocalizationManager.t("error_supervision_amount")
                    currency.isNotBlank() && currency.length != 3 -> LocalizationManager.t("error_currency_iso")
                    else -> null
                }
                if (errorMessage == null) {
                    isSaving = true
                    scope.launch {
                        runCatching {
                            OmsApiClient.updateProject(project.id, UpdateProjectRequest(
                                name = name.trim(), siteName = siteName.trim(), siteNumber = siteNumber.trim().ifBlank { siteName.trim() },
                                description = description.trim(),
                                address = address.trim(), region = region.trim(), city = city.trim(),
                                status = status,
                                latitude = lat, longitude = lon, sector = sector.trim(),
                                constructionType = constructionType.trim(), budgetPlanned = budget!!,
                                engineerConsultantContractAmount = engineerAmount,
                                technicalSupervisionAmount = supervisionAmount,
                                subprojectContractAmount = subprojectAmount,
                                startDate = startDate.takeIf { it.isNotBlank() },
                                endDate = endDate.takeIf { it.isNotBlank() },
                                contractSignedDate = contractSignedDate.takeIf { it.isNotBlank() },
                                plannedEndDate = plannedEndDate.takeIf { it.isNotBlank() },
                                designContractSigningDate = designContractSigningDate.takeIf { it.isNotBlank() },
                                constructionContractSigningDate = constructionContractSigningDate.takeIf { it.isNotBlank() },
                                constructionStartDate = constructionStartDate.takeIf { it.isNotBlank() },
                                projectedCompletionTime = projectedCompletionTime.takeIf { it.isNotBlank() },
                                currency = currency.ifBlank { "UAH" },
                                contractorName = contractorName.trim()
                            ))
                        }.onSuccess {
                            ProjectRepository.refresh(force = true)
                            onSaved(ProjectRepository.projects.firstOrNull { it.id == project.id } ?: project)
                        }.onFailure { errorMessage = LocalizationManager.t("error_save_project").replace("{message}", it.message ?: LocalizationManager.t("unknown_error")) }
                        isSaving = false
                    }
                }
            }) { if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(LocalizationManager.t("save_changes")) }
        }
    }
}
