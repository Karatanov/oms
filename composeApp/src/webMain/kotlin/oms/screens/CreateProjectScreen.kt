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
import oms.data.ApiProject
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.theme.Primary

@Composable
fun CreateProjectScreen(
    onCancel: () -> Unit = {},
    onCreated: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var siteName by remember { mutableStateOf("") }
    var siteNumber by remember { mutableStateOf("") }
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
    var startDate by remember { mutableStateOf("") }
    var contractSignedDate by remember { mutableStateOf("") }
    var plannedEndDate by remember { mutableStateOf("") }
    var parentProjects by remember { mutableStateOf<List<ApiProject>>(emptyList()) }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var managerId by remember { mutableStateOf("1") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        parentProjects = runCatching { OmsApiClient.projects() }.getOrDefault(emptyList())
    }

    fun requiredFieldsFilled() = listOf(
        name, siteName, siteNumber, address, region, city, sector, constructionType
    ).all { it.isNotBlank() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Створити проєкт", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Після створення проєкт одразу з’явиться в переліку та на карті.",
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
                Text("Основна інформація", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { projectType = "project" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (projectType == "project") Primary else MaterialTheme.colorScheme.surface, contentColor = if (projectType == "project") MaterialTheme.colorScheme.onPrimary else Primary)) { Text("Проєкт") }
                    OutlinedButton(onClick = { projectType = "subproject" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (projectType == "subproject") Primary else MaterialTheme.colorScheme.surface, contentColor = if (projectType == "subproject") MaterialTheme.colorScheme.onPrimary else Primary)) { Text("Субпроєкт") }
                    OutlinedButton(onClick = { projectType = "subproject_part" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (projectType == "subproject_part") Primary else MaterialTheme.colorScheme.surface, contentColor = if (projectType == "subproject_part") MaterialTheme.colorScheme.onPrimary else Primary)) { Text("Частина субпроєкту") }
                }
                if (projectType != "project") {
                    val eligibleParents = parentProjects.filter { it.projectType == if (projectType == "subproject") "project" else "subproject" }
                    InlineOptionPicker(
                        options = eligibleParents,
                        selected = eligibleParents.firstOrNull { it.uuid == parentProjectUuid },
                        prompt = if (projectType == "subproject") "Оберіть батьківський проєкт *" else "Оберіть батьківський субпроєкт *",
                        onSelect = { parentProjectUuid = it.uuid },
                        itemLabel = { it.name }
                    )
                }
                OutlinedTextField(name, { name = it }, label = { Text("Назва проєкту *") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(siteName, { siteName = it }, label = { Text("Код майданчика *") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(siteNumber, { siteNumber = it }, label = { Text("Номер майданчика *") }, modifier = Modifier.weight(1f))
                }
                if (projectType != "project") {
                    OutlinedTextField(subprojectContractAmount, { value -> if (value.all(Char::isDigit)) subprojectContractAmount = value }, label = { Text("Сума контракту субпроєкту, грн *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OmsDateField(startDate, { startDate = it }, "Дата початку", Modifier.weight(1f), true)
                        OmsDateField(contractSignedDate, { contractSignedDate = it }, "Дата укладення контракту", Modifier.weight(1f), true)
                        OmsDateField(plannedEndDate, { plannedEndDate = it }, "Планова дата завершення", Modifier.weight(1f), true)
                    }
                }
                OutlinedTextField(address, { address = it }, label = { Text("Адреса *") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(region, { region = it }, label = { Text("Область *") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(city, { city = it }, label = { Text("Населений пункт *") }, modifier = Modifier.weight(1f))
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
                Text("Параметри та розташування", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(sector, { sector = it }, label = { Text("Сектор *") }, modifier = Modifier.weight(1f))
                    ConstructionTypeSelector(constructionType, { constructionType = it }, Modifier.weight(1f))
                }
                OutlinedTextField(budgetPlanned, { value -> if (value.matches(Regex("[0-9.,]*"))) budgetPlanned = value }, label = { Text("Плановий бюджет, грн *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(engineerConsultantContractAmount, { value -> if (value.all(Char::isDigit)) engineerConsultantContractAmount = value }, label = { Text("Договір інженера-консультанта, грн") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(technicalSupervisionAmount, { value -> if (value.all(Char::isDigit)) technicalSupervisionAmount = value }, label = { Text("Технічний нагляд, грн") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(latitude, { value -> if (value.matches(Regex("-?[0-9.,]*"))) latitude = value }, label = { Text("Широта *") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(longitude, { value -> if (value.matches(Regex("-?[0-9.,]*"))) longitude = value }, label = { Text("Довгота *") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(managerId, { managerId = it }, label = { Text("ID відповідального *") }, supportingText = { Text("1 — Admin") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        }

        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onCancel, enabled = !isSubmitting) { Text("Скасувати") }
            Button(
                enabled = !isSubmitting,
                onClick = {
                    val parsedBudget = budgetPlanned.toLongOrNull()
                    val parsedLatitude = latitude.replace(',', '.').toDoubleOrNull()
                    val parsedLongitude = longitude.replace(',', '.').toDoubleOrNull()
                    val parsedManagerId = managerId.toLongOrNull()
                    val parsedEngineerConsultantAmount = engineerConsultantContractAmount.takeIf { it.isNotBlank() }?.toLongOrNull()
                    val parsedTechnicalSupervisionAmount = technicalSupervisionAmount.takeIf { it.isNotBlank() }?.toLongOrNull()
                    val parsedSubprojectContractAmount = subprojectContractAmount.takeIf { it.isNotBlank() }?.toLongOrNull()
                    errorMessage = when {
                        !requiredFieldsFilled() -> "Заповніть усі поля, позначені * ."
                        parsedBudget == null || parsedBudget <= 0 -> "Бюджет має бути додатним цілим числом."
                        parsedLatitude == null || parsedLatitude !in -90.0..90.0 -> "Широта має бути в межах від -90 до 90."
                        parsedLongitude == null || parsedLongitude !in -180.0..180.0 -> "Довгота має бути в межах від -180 до 180."
                        parsedManagerId == null || parsedManagerId <= 0 -> "Вкажіть коректний ID відповідального."
                        engineerConsultantContractAmount.isNotBlank() && parsedEngineerConsultantAmount == null -> "Сума договору інженера-консультанта має бути цілим числом."
                        technicalSupervisionAmount.isNotBlank() && parsedTechnicalSupervisionAmount == null -> "Сума технічного нагляду має бути цілим числом."
                        projectType != "project" && parentProjectUuid == null -> "Оберіть батьківський запис."
                        projectType != "project" && (parsedSubprojectContractAmount == null || parsedSubprojectContractAmount <= 0) -> "Вкажіть додатну суму контракту."
                        projectType != "project" && listOf(startDate, contractSignedDate, plannedEndDate).any { it.isBlank() } -> "Заповніть усі дати контракту."
                        else -> null
                    }
                    if (errorMessage == null) {
                        isSubmitting = true
                        scope.launch {
                            runCatching {
                                OmsApiClient.createProject(
                                    CreateProjectRequest(
                                        name = name.trim(), siteName = siteName.trim(), siteNumber = siteNumber.trim(),
                                        address = address.trim(), region = region.trim(), city = city.trim(),
                                        latitude = parsedLatitude!!, longitude = parsedLongitude!!, sector = sector.trim(),
                                        constructionType = constructionType.trim(), budgetPlanned = parsedBudget!!,
                                        engineerConsultantContractAmount = parsedEngineerConsultantAmount,
                                        technicalSupervisionAmount = parsedTechnicalSupervisionAmount,
                                        managerId = parsedManagerId!!,
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
                                errorMessage = "Не вдалося створити проєкт: ${it.message ?: "невідома помилка"}"
                            }
                            isSubmitting = false
                        }
                    }
                }
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                else Text("Створити проєкт")
            }
        }
    }
}
