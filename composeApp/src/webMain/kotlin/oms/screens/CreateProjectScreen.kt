package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import oms.data.CreateProjectRequest
import oms.components.ConstructionTypeSelector
import oms.components.SectorSelector
import oms.components.OmsDateField
import oms.components.InlineOptionPicker
import oms.components.UkraineRegionAutocomplete
import oms.components.UkraineCityAutocomplete
import oms.components.currentIsoDate
import oms.components.AddressCoordinatesCalculator
import oms.data.ApiProject
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.localization.LocalizationManager
import oms.theme.Primary

@Composable
fun CreateProjectScreen(
    onCancel: () -> Unit = {},
    onCreated: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var siteName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var sector by remember { mutableStateOf("Education") }
    var constructionType by remember { mutableStateOf("reconstruction") }
    var budgetPlanned by remember { mutableStateOf("") }
    var engineerConsultantContractAmount by remember { mutableStateOf("") }
    var technicalSupervisionAmount by remember { mutableStateOf("") }
    var projectType by remember { mutableStateOf("project") }
    var parentProjectUuid by remember { mutableStateOf<String?>(null) }
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
    var parentProjects by remember { mutableStateOf<List<ApiProject>>(emptyList()) }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var isCalculatingCoordinates by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        parentProjects = runCatching { OmsApiClient.projects() }.getOrDefault(emptyList())
    }

    fun requiredFieldsFilled() = listOf(name, siteName).all { it.isNotBlank() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(LocalizationManager.t("create_project"), style = MaterialTheme.typography.headlineMedium)
        Text(
            LocalizationManager.t("project_created_hint"),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionTitle(Icons.Default.Folder, LocalizationManager.t("basic_information"))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { projectType = "project" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (projectType == "project") Primary else MaterialTheme.colorScheme.surface, contentColor = if (projectType == "project") MaterialTheme.colorScheme.onPrimary else Primary)) { Text(LocalizationManager.t("project")) }
                    OutlinedButton(onClick = { projectType = "subproject" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (projectType == "subproject") Primary else MaterialTheme.colorScheme.surface, contentColor = if (projectType == "subproject") MaterialTheme.colorScheme.onPrimary else Primary)) { Text(LocalizationManager.t("subproject")) }
                    OutlinedButton(onClick = { projectType = "subproject_part"; siteName = "" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (projectType == "subproject_part") Primary else MaterialTheme.colorScheme.surface, contentColor = if (projectType == "subproject_part") MaterialTheme.colorScheme.onPrimary else Primary)) { Text(LocalizationManager.t("subproject_part")) }
                }
                if (projectType != "project") {
                    val eligibleParents = parentProjects.filter { it.projectType == if (projectType == "subproject") "project" else "subproject" }
                    InlineOptionPicker(
                        options = eligibleParents,
                        selected = eligibleParents.firstOrNull { it.uuid == parentProjectUuid },
                        prompt = LocalizationManager.t(if (projectType == "subproject") "select_parent_project" else "select_parent_subproject"),
                        onSelect = {
                            parentProjectUuid = it.uuid
                            if (projectType == "subproject_part") siteName = nextSubprojectPartCode(it, parentProjects)
                        },
                        itemLabel = { it.name }
                    )
                }
                OutlinedTextField(name, { name = it }, label = { Text(LocalizationManager.t("project_name_required")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text(LocalizationManager.t("description")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    siteName,
                    { siteName = it },
                    label = { Text(LocalizationManager.t(if (projectType == "subproject_part") "subproject_part_code" else "project_code_required")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectorSelector(sector, { sector = it }, Modifier.weight(1f))
                    ConstructionTypeSelector(constructionType, { constructionType = it }, Modifier.weight(1f))
                }
            }
        }

        if (projectType != "project") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SectionTitle(Icons.Default.LocationOn, LocalizationManager.t("parameters_and_location"))
                    OutlinedTextField(address, { address = it }, label = { Text(LocalizationManager.t("address")) }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        UkraineRegionAutocomplete(region, { region = it }, LocalizationManager.t("region"), Modifier.weight(1f), required = false)
                        UkraineCityAutocomplete(city, { city = it }, LocalizationManager.t("city"), Modifier.weight(1f), required = false)
                    }
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionTitle(Icons.Default.AccountBalanceWallet, LocalizationManager.t("financial_parameters"))
                OutlinedTextField(budgetPlanned, { value -> if (value.matches(Regex("[0-9.,]*"))) budgetPlanned = value }, label = { Text(LocalizationManager.t("planned_budget_required")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(engineerConsultantContractAmount, { value -> if (value.all(Char::isDigit)) engineerConsultantContractAmount = value }, label = { Text(LocalizationManager.t("engineer_consultant_amount")) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(technicalSupervisionAmount, { value -> if (value.all(Char::isDigit)) technicalSupervisionAmount = value }, label = { Text(LocalizationManager.t("technical_supervision_amount")) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                if (projectType != "project") {
                    OutlinedTextField(subprojectContractAmount, { value -> if (value.all(Char::isDigit)) subprojectContractAmount = value }, label = { Text(LocalizationManager.t("subproject_contract_amount")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OmsDateField(startDate, { startDate = it }, LocalizationManager.t("start_date"), Modifier.weight(1f), true)
                        OmsDateField(endDate, { endDate = it }, LocalizationManager.t("end_date"), Modifier.weight(1f), true)
                        OmsDateField(contractSignedDate, { contractSignedDate = it }, LocalizationManager.t("contract_signed_date"), Modifier.weight(1f), true)
                        OmsDateField(plannedEndDate, { plannedEndDate = it }, LocalizationManager.t("planned_end_date"), Modifier.weight(1f), true)
                    }
                }
                if (projectType == "project") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OmsDateField(startDate, { startDate = it }, LocalizationManager.t("start_date"), Modifier.weight(1f), true)
                        OmsDateField(endDate, { endDate = it }, LocalizationManager.t("end_date"), Modifier.weight(1f), true)
                        OmsDateField(contractSignedDate, { contractSignedDate = it }, LocalizationManager.t("contract_signed_date"), Modifier.weight(1f), true)
                        OmsDateField(plannedEndDate, { plannedEndDate = it }, LocalizationManager.t("planned_end_date"), Modifier.weight(1f), true)
                    }
                }
                Text(LocalizationManager.t("design_construction_dates"), style = MaterialTheme.typography.titleMedium)
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
            }
        }

        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onCancel, enabled = !isSubmitting) { Text(LocalizationManager.t("cancel")) }
            Button(
                enabled = !isSubmitting,
                onClick = {
                    val parsedBudget = budgetPlanned.toLongOrNull()
                    val parsedLatitude = latitude.replace(',', '.').toDoubleOrNull()
                    val parsedLongitude = longitude.replace(',', '.').toDoubleOrNull()
                    val parsedEngineerConsultantAmount = engineerConsultantContractAmount.takeIf { it.isNotBlank() }?.toLongOrNull()
                    val parsedTechnicalSupervisionAmount = technicalSupervisionAmount.takeIf { it.isNotBlank() }?.toLongOrNull()
                    val parsedSubprojectContractAmount = subprojectContractAmount.takeIf { it.isNotBlank() }?.toLongOrNull()
                    errorMessage = when {
                        !requiredFieldsFilled() -> LocalizationManager.t("error_required_fields")
                        parsedBudget == null || parsedBudget <= 0 -> LocalizationManager.t("error_positive_budget")
                        parsedLatitude != null && parsedLatitude !in -90.0..90.0 -> LocalizationManager.t("error_latitude_range")
                        parsedLongitude != null && parsedLongitude !in -180.0..180.0 -> LocalizationManager.t("error_longitude_range")
                        engineerConsultantContractAmount.isNotBlank() && parsedEngineerConsultantAmount == null -> LocalizationManager.t("error_engineer_amount")
                        technicalSupervisionAmount.isNotBlank() && parsedTechnicalSupervisionAmount == null -> LocalizationManager.t("error_supervision_amount")
                        currency.isNotBlank() && currency.length != 3 -> LocalizationManager.t("error_currency_iso")
                        else -> null
                    }
                    if (errorMessage == null) {
                        isSubmitting = true
                        scope.launch {
                            runCatching {
                                OmsApiClient.createProject(
                                    CreateProjectRequest(
                                        name = name.trim(), siteName = siteName.trim(), siteNumber = siteName.trim(), description = description.trim().ifBlank { null },
                                        address = address.trim(), region = region.trim(), city = city.trim(),
                                        latitude = parsedLatitude ?: 0.0, longitude = parsedLongitude ?: 0.0, sector = sector.trim(),
                                        constructionType = constructionType.trim(), budgetPlanned = parsedBudget!!,
                                        engineerConsultantContractAmount = parsedEngineerConsultantAmount,
                                        technicalSupervisionAmount = parsedTechnicalSupervisionAmount,
                                        projectType = projectType,
                                        parentProjectUuid = parentProjectUuid,
                                        subprojectContractAmount = parsedSubprojectContractAmount,
                                        startDate = startDate.takeIf { it.isNotBlank() },
                                        endDate = endDate.takeIf { it.isNotBlank() },
                                        contractSignedDate = contractSignedDate.takeIf { it.isNotBlank() },
                                        plannedEndDate = plannedEndDate.takeIf { it.isNotBlank() },
                                        designContractSigningDate = designContractSigningDate.takeIf { it.isNotBlank() },
                                        constructionContractSigningDate = constructionContractSigningDate.takeIf { it.isNotBlank() },
                                        constructionStartDate = constructionStartDate.takeIf { it.isNotBlank() },
                                        projectedCompletionTime = projectedCompletionTime.takeIf { it.isNotBlank() },
                                        currency = currency.ifBlank { "UAH" },
                                        contractorName = contractorName.trim().ifBlank { null }
                                    )
                                )
                            }.onSuccess {
                                ProjectRepository.refresh()
                                onCreated()
                            }.onFailure {
                                errorMessage = LocalizationManager.t("error_create_project").replace("{message}", it.message ?: LocalizationManager.t("unknown_error"))
                            }
                            isSubmitting = false
                        }
                    }
                }
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                else Text(LocalizationManager.t("create_project"))
            }
        }
    }
}

private fun nextSubprojectPartCode(parent: ApiProject, projects: List<ApiProject>): String {
    val parentCode = parent.siteNumber.trim()
    val pattern = Regex("^${Regex.escape(parentCode)}-(\\d+)$")
    val nextNumber = projects
        .asSequence()
        .filter { it.projectType == "subproject_part" && it.parentProjectUuid == parent.uuid }
        .mapNotNull { pattern.matchEntire(it.siteNumber.trim())?.groupValues?.get(1)?.toIntOrNull() }
        .maxOrNull()
        ?.plus(1)
        ?: 1
    return "$parentCode-${nextNumber.toString().padStart(2, '0')}"
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = Primary)
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
