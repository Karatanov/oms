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
import kotlinx.coroutines.launch
import oms.data.CreateProjectRequest
import oms.components.ConstructionTypeSelector
import oms.components.OmsDateField
import oms.components.InlineOptionPicker
import oms.components.UkraineRegionAutocomplete
import oms.components.currentIsoDate
import oms.data.ApiProject
import oms.data.ApiUser
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
    var siteName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var sector by remember { mutableStateOf("") }
    var constructionType by remember { mutableStateOf("reconstruction") }
    var budgetPlanned by remember { mutableStateOf("") }
    var engineerConsultantContractAmount by remember { mutableStateOf("") }
    var technicalSupervisionAmount by remember { mutableStateOf("") }
    var projectType by remember { mutableStateOf("project") }
    var parentProjectUuid by remember { mutableStateOf<String?>(null) }
    var subprojectContractAmount by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(currentIsoDate()) }
    var contractSignedDate by remember { mutableStateOf(currentIsoDate()) }
    var plannedEndDate by remember { mutableStateOf(currentIsoDate()) }
    var parentProjects by remember { mutableStateOf<List<ApiProject>>(emptyList()) }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<ApiUser>>(emptyList()) }
    var managerId by remember { mutableStateOf<Long?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        parentProjects = runCatching { OmsApiClient.projects() }.getOrDefault(emptyList())
        users = runCatching { OmsApiClient.users() }.getOrDefault(emptyList())
            .filter { it.status.equals("active", ignoreCase = true) }
        managerId = users.firstOrNull()?.id
    }

    fun requiredFieldsFilled() = listOf(
        name, siteName, address, region, city, sector, constructionType
    ).all { it.isNotBlank() }

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
                    OutlinedButton(onClick = { projectType = "subproject_part" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (projectType == "subproject_part") Primary else MaterialTheme.colorScheme.surface, contentColor = if (projectType == "subproject_part") MaterialTheme.colorScheme.onPrimary else Primary)) { Text(LocalizationManager.t("subproject_part")) }
                }
                if (projectType != "project") {
                    val eligibleParents = parentProjects.filter { it.projectType == if (projectType == "subproject") "project" else "subproject" }
                    InlineOptionPicker(
                        options = eligibleParents,
                        selected = eligibleParents.firstOrNull { it.uuid == parentProjectUuid },
                        prompt = LocalizationManager.t(if (projectType == "subproject") "select_parent_project" else "select_parent_subproject"),
                        onSelect = { parentProjectUuid = it.uuid },
                        itemLabel = { it.name }
                    )
                }
                OutlinedTextField(name, { name = it }, label = { Text(LocalizationManager.t("project_name_required")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(siteName, { siteName = it }, label = { Text(LocalizationManager.t("project_code_required")) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(sector, { sector = it }, label = { Text(LocalizationManager.t("sector_required")) }, modifier = Modifier.weight(1f))
                    ConstructionTypeSelector(constructionType, { constructionType = it }, Modifier.weight(1f))
                }
                InlineOptionPicker(
                    options = users,
                    selected = users.firstOrNull { it.id == managerId },
                    prompt = LocalizationManager.t("manager_id_required"),
                    onSelect = { managerId = it.id },
                    itemLabel = { user ->
                        listOf(user.firstName, user.lastName).filter { it.isNotBlank() }
                            .joinToString(" ").ifBlank { user.username } + " (${user.username})"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
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
                SectionTitle(Icons.Default.LocationOn, LocalizationManager.t("parameters_and_location"))
                OutlinedTextField(address, { address = it }, label = { Text(LocalizationManager.t("address_required")) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UkraineRegionAutocomplete(region, { region = it }, LocalizationManager.t("region"), Modifier.weight(1f), required = true)
                    OutlinedTextField(city, { city = it }, label = { Text(LocalizationManager.t("city_required")) }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(latitude, { value -> if (value.matches(Regex("-?[0-9.,]*"))) latitude = value }, label = { Text(LocalizationManager.t("latitude_required")) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(longitude, { value -> if (value.matches(Regex("-?[0-9.,]*"))) longitude = value }, label = { Text(LocalizationManager.t("longitude_required")) }, singleLine = true, modifier = Modifier.weight(1f))
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
                    OutlinedTextField(subprojectContractAmount, { value -> if (value.all(Char::isDigit)) subprojectContractAmount = value }, label = { Text(LocalizationManager.t("subproject_contract_amount_required")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OmsDateField(startDate, { startDate = it }, LocalizationManager.t("start_date"), Modifier.weight(1f), true)
                        OmsDateField(contractSignedDate, { contractSignedDate = it }, LocalizationManager.t("contract_signed_date"), Modifier.weight(1f), true)
                        OmsDateField(plannedEndDate, { plannedEndDate = it }, LocalizationManager.t("planned_end_date"), Modifier.weight(1f), true)
                    }
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
                        parsedLatitude == null || parsedLatitude !in -90.0..90.0 -> LocalizationManager.t("error_latitude_range")
                        parsedLongitude == null || parsedLongitude !in -180.0..180.0 -> LocalizationManager.t("error_longitude_range")
                        managerId == null -> LocalizationManager.t("error_valid_manager")
                        engineerConsultantContractAmount.isNotBlank() && parsedEngineerConsultantAmount == null -> LocalizationManager.t("error_engineer_amount")
                        technicalSupervisionAmount.isNotBlank() && parsedTechnicalSupervisionAmount == null -> LocalizationManager.t("error_supervision_amount")
                        projectType != "project" && parentProjectUuid == null -> LocalizationManager.t("error_select_parent")
                        projectType != "project" && (parsedSubprojectContractAmount == null || parsedSubprojectContractAmount <= 0) -> LocalizationManager.t("error_positive_contract_amount")
                        projectType != "project" && listOf(startDate, contractSignedDate, plannedEndDate).any { it.isBlank() } -> LocalizationManager.t("error_contract_dates_required")
                        else -> null
                    }
                    if (errorMessage == null) {
                        isSubmitting = true
                        scope.launch {
                            runCatching {
                                OmsApiClient.createProject(
                                    CreateProjectRequest(
                                        name = name.trim(), siteName = siteName.trim(), siteNumber = siteName.trim(),
                                        address = address.trim(), region = region.trim(), city = city.trim(),
                                        latitude = parsedLatitude!!, longitude = parsedLongitude!!, sector = sector.trim(),
                                        constructionType = constructionType.trim(), budgetPlanned = parsedBudget!!,
                                        engineerConsultantContractAmount = parsedEngineerConsultantAmount,
                                        technicalSupervisionAmount = parsedTechnicalSupervisionAmount,
                                        managerId = managerId!!,
                                        projectType = projectType,
                                        parentProjectUuid = parentProjectUuid,
                                        subprojectContractAmount = parsedSubprojectContractAmount,
                                        startDate = startDate.takeIf { it.isNotBlank() },
                                        contractSignedDate = contractSignedDate.takeIf { it.isNotBlank() },
                                        plannedEndDate = plannedEndDate.takeIf { it.isNotBlank() }
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

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = Primary)
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
