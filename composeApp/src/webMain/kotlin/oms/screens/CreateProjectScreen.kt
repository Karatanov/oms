package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import oms.data.OmsApiClient
import oms.data.ProjectRepository

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
    var constructionType by remember { mutableStateOf("") }
    var budgetPlanned by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var managerId by remember { mutableStateOf("1") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun requiredFieldsFilled() = listOf(
        name, siteName, siteNumber, address, region, city, sector, constructionType
    ).all { it.isNotBlank() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
                OutlinedTextField(name, { name = it }, label = { Text("Назва проєкту *") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(siteName, { siteName = it }, label = { Text("Код майданчика *") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(siteNumber, { siteNumber = it }, label = { Text("Номер майданчика *") }, modifier = Modifier.weight(1f))
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
                    OutlinedTextField(constructionType, { constructionType = it }, label = { Text("Тип будівництва *") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(budgetPlanned, { budgetPlanned = it }, label = { Text("Плановий бюджет, грн *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(latitude, { latitude = it }, label = { Text("Широта *") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(longitude, { longitude = it }, label = { Text("Довгота *") }, singleLine = true, modifier = Modifier.weight(1f))
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
                    errorMessage = when {
                        !requiredFieldsFilled() -> "Заповніть усі поля, позначені * ."
                        parsedBudget == null || parsedBudget <= 0 -> "Бюджет має бути додатним цілим числом."
                        parsedLatitude == null || parsedLatitude !in -90.0..90.0 -> "Широта має бути в межах від -90 до 90."
                        parsedLongitude == null || parsedLongitude !in -180.0..180.0 -> "Довгота має бути в межах від -180 до 180."
                        parsedManagerId == null || parsedManagerId <= 0 -> "Вкажіть коректний ID відповідального."
                        else -> null
                    }
                    if (errorMessage == null) {
                        isSubmitting = true
                        scope.launch {
                            runCatching {
                                OmsApiClient.createProject(
                                    CreateProjectRequest(
                                        name.trim(), siteName.trim(), siteNumber.trim(), address.trim(),
                                        region.trim(), city.trim(), parsedLatitude!!, parsedLongitude!!,
                                        sector.trim(), constructionType.trim(), parsedBudget!!, parsedManagerId!!
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
