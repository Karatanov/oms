package oms.screens

import androidx.compose.foundation.layout.*
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

@Composable
fun EditProjectScreen(
    project: Project,
    onCancel: () -> Unit = {},
    onSaved: (Project) -> Unit = {}
) {
    var name by remember { mutableStateOf(project.name) }
    var siteName by remember { mutableStateOf("") }
    var siteNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(project.region) }
    var city by remember { mutableStateOf("") }
    var sector by remember { mutableStateOf("") }
    var constructionType by remember { mutableStateOf("") }
    var budgetPlanned by remember { mutableStateOf("") }
    var engineerConsultantContractAmount by remember { mutableStateOf("") }
    var technicalSupervisionAmount by remember { mutableStateOf("") }
    var projectType by remember { mutableStateOf("project") }
    var subprojectContractAmount by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var contractSignedDate by remember { mutableStateOf("") }
    var plannedEndDate by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf(project.latitude.toString()) }
    var longitude by remember { mutableStateOf(project.longitude.toString()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(project.id) {
        runCatching { OmsApiClient.projectDetails(project.id).data }
            .onSuccess { details ->
                name = details.name; siteName = details.siteName; siteNumber = details.siteNumber
                address = details.address; region = details.region; city = details.city
                latitude = details.latitude.toString(); longitude = details.longitude.toString()
                sector = details.sector; constructionType = details.constructionType
                budgetPlanned = details.budgetPlanned.toString()
                engineerConsultantContractAmount = details.engineerConsultantContractAmount?.toString().orEmpty()
                technicalSupervisionAmount = details.technicalSupervisionAmount?.toString().orEmpty()
                projectType = details.projectType
                subprojectContractAmount = details.subprojectContractAmount?.toString().orEmpty()
                startDate = details.startDate.orEmpty(); contractSignedDate = details.contractSignedDate.orEmpty(); plannedEndDate = details.plannedEndDate.orEmpty()
            }
            .onFailure { errorMessage = "Не вдалося завантажити дані проєкту." }
        isLoading = false
    }

    val allRequiredFilled = listOf(name, siteName, siteNumber, address, region, city, sector, constructionType).all { it.isNotBlank() }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Редагувати проєкт", style = MaterialTheme.typography.headlineMedium)
        if (isLoading) {
            CircularProgressIndicator()
            return@Column
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Назва проєкту *") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(siteName, { siteName = it }, label = { Text("Код майданчика *") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(siteNumber, { siteNumber = it }, label = { Text("Номер майданчика *") }, modifier = Modifier.weight(1f))
                }
                if (projectType == "subproject") {
                    Text("Дані субпроєкту", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(subprojectContractAmount, { value -> if (value.all(Char::isDigit)) subprojectContractAmount = value }, label = { Text("Сума контракту субпроєкту, грн *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(startDate, { startDate = it }, label = { Text("Дата початку * (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(contractSignedDate, { contractSignedDate = it }, label = { Text("Дата укладення контракту * (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(plannedEndDate, { plannedEndDate = it }, label = { Text("Планова дата завершення * (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Text("Тривалість контракту буде розрахована після збереження дат.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(address, { address = it }, label = { Text("Адреса *") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(region, { region = it }, label = { Text("Область *") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(city, { city = it }, label = { Text("Населений пункт *") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(sector, { sector = it }, label = { Text("Сектор *") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(constructionType, { constructionType = it }, label = { Text("Тип будівництва *") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(budgetPlanned, { value -> if (value.matches(Regex("[0-9.,]*"))) budgetPlanned = value }, label = { Text("Плановий бюджет, грн *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(engineerConsultantContractAmount, { value -> if (value.all(Char::isDigit)) engineerConsultantContractAmount = value }, label = { Text("Договір інженера-консультанта, грн") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(technicalSupervisionAmount, { value -> if (value.all(Char::isDigit)) technicalSupervisionAmount = value }, label = { Text("Технічний нагляд, грн") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(latitude, { latitude = it }, label = { Text("Широта *") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(longitude, { longitude = it }, label = { Text("Довгота *") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            OutlinedButton(onClick = onCancel, enabled = !isSaving) { Text("Скасувати") }
            Button(enabled = !isSaving, onClick = {
                val budget = budgetPlanned.toLongOrNull()
                val lat = latitude.replace(',', '.').toDoubleOrNull()
                val lon = longitude.replace(',', '.').toDoubleOrNull()
                val engineerAmount = engineerConsultantContractAmount.takeIf { it.isNotBlank() }?.toLongOrNull()
                val supervisionAmount = technicalSupervisionAmount.takeIf { it.isNotBlank() }?.toLongOrNull()
                val subprojectAmount = subprojectContractAmount.takeIf { it.isNotBlank() }?.toLongOrNull()
                errorMessage = when {
                    !allRequiredFilled -> "Заповніть усі поля, позначені * ."
                    budget == null || budget <= 0 -> "Бюджет має бути додатним цілим числом."
                    lat == null || lat !in -90.0..90.0 -> "Широта має бути в межах від -90 до 90."
                    lon == null || lon !in -180.0..180.0 -> "Довгота має бути в межах від -180 до 180."
                    engineerConsultantContractAmount.isNotBlank() && engineerAmount == null -> "Сума договору інженера-консультанта має бути цілим числом."
                    technicalSupervisionAmount.isNotBlank() && supervisionAmount == null -> "Сума технічного нагляду має бути цілим числом."
                    projectType == "subproject" && (subprojectAmount == null || subprojectAmount <= 0) -> "Вкажіть додатну суму контракту субпроєкту."
                    projectType == "subproject" && listOf(startDate, contractSignedDate, plannedEndDate).any { it.isBlank() } -> "Заповніть усі дати субпроєкту."
                    else -> null
                }
                if (errorMessage == null) {
                    isSaving = true
                    scope.launch {
                        runCatching {
                            OmsApiClient.updateProject(project.id, UpdateProjectRequest(
                                name = name.trim(), siteName = siteName.trim(), siteNumber = siteNumber.trim(),
                                address = address.trim(), region = region.trim(), city = city.trim(),
                                latitude = lat!!, longitude = lon!!, sector = sector.trim(),
                                constructionType = constructionType.trim(), budgetPlanned = budget!!,
                                engineerConsultantContractAmount = engineerAmount,
                                technicalSupervisionAmount = supervisionAmount,
                                subprojectContractAmount = subprojectAmount,
                                startDate = startDate.takeIf { it.isNotBlank() },
                                contractSignedDate = contractSignedDate.takeIf { it.isNotBlank() },
                                plannedEndDate = plannedEndDate.takeIf { it.isNotBlank() }
                            ))
                        }.onSuccess {
                            ProjectRepository.refresh()
                            onSaved(ProjectRepository.projects.firstOrNull { it.id == project.id } ?: project)
                        }.onFailure { errorMessage = "Не вдалося зберегти зміни: ${it.message ?: "невідома помилка"}" }
                        isSaving = false
                    }
                }
            }) { if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Зберегти зміни") }
        }
    }
}
