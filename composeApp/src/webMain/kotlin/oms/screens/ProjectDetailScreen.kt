package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import oms.components.StatusChip
import oms.components.constructionTypeLabel
import oms.data.ApiProjectDetails
import oms.data.ApiInspectionReport
import oms.data.ApiFinancialRecords
import oms.data.ApiProjectDocument
import oms.data.ApiHealthSafetyObservations
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.components.toOmsDate
import oms.localization.LocalizationManager
import oms.model.Project
import oms.navigation.Screen

@Composable
fun ProjectDetailScreen(
    project: Project,
    onBackToProjects: () -> Unit = {},
    onEdit: (Project) -> Unit = {}
) {
    val parentProjectName = project.parentProjectUuid?.let { parentId ->
        ProjectRepository.projects.firstOrNull { it.id == parentId }?.name
    }
    var selectedTab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ProjectDetailTab.GeneralInfo) }
    val details = remember(project.id) { mutableStateOf<ApiProjectDetails?>(null) }
    val reports = remember(project.id) { mutableStateOf<List<ApiInspectionReport>>(emptyList()) }
    val financials = remember(project.id) { mutableStateOf<ApiFinancialRecords?>(null) }
    val documents = remember(project.id) { mutableStateOf<List<ApiProjectDocument>>(emptyList()) }
    val healthSafetyObservations = remember(project.id) { mutableStateOf<ApiHealthSafetyObservations?>(null) }
    LaunchedEffect(project.id) { details.value = runCatching { OmsApiClient.projectDetails(project.id) }.getOrNull() }
    LaunchedEffect(project.id) { reports.value = runCatching { OmsApiClient.projectReports(project.id) }.getOrDefault(emptyList()) }
    LaunchedEffect(project.id) { financials.value = runCatching { OmsApiClient.financials(project.id) }.getOrNull() }
    LaunchedEffect(project.id) { documents.value = runCatching { OmsApiClient.projectDocuments(project.id) }.getOrDefault(emptyList()) }
    LaunchedEffect(project.id) { healthSafetyObservations.value = runCatching { OmsApiClient.healthSafetyObservations(project.id) }.getOrNull() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Breadcrumb
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackToProjects) {
                Text(Screen.Projects.title)
            }

            Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(
                text = project.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }

        parentProjectName?.let { parentName ->
            Text(
                text = "Проєкт: $parentName",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Header
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatusChip(project.status)

                            Text(
                                text = "${LocalizationManager.t("address")}: ${details.value?.data?.address ?: project.region}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Text(
                            text = "${LocalizationManager.t("sector")}: ${details.value?.data?.sector ?: "—"} • ${LocalizationManager.t("construction_type")}: ${details.value?.data?.constructionType?.constructionTypeLabel() ?: "—"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(onClick = { onEdit(project) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = LocalizationManager.t("edit")
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(LocalizationManager.t("edit"))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DetailMetricCard(
                title = LocalizationManager.t("construction_contract"),
                value = details.value?.financialSummary?.constructionContractAmount?.toMoney() ?: "—",
                modifier = Modifier.weight(1f)
            )
            DetailMetricCard(
                title = LocalizationManager.t("acts_of_completed_works"),
                value = details.value?.financialSummary?.amountSpent?.toMoney() ?: "—",
                modifier = Modifier.weight(1f)
            )
            DetailMetricCard(
                title = LocalizationManager.t("financial_completion"),
                value = details.value?.financialSummary?.completionPct?.let { "${it.toInt()}%" } ?: "—",
                modifier = Modifier.weight(1f)
            )
        }

        if (details.value?.data?.projectType == "subproject") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DetailMetricCard(
                    title = "Сума контракту субпроєкту",
                    value = details.value?.data?.subprojectContractAmount?.toMoney() ?: "—",
                    modifier = Modifier.weight(1f)
                )
                DetailMetricCard(
                    title = "Тривалість контракту",
                    value = details.value?.data?.contractDurationDays?.let { "$it днів" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DetailMetricCard(
                title = "Договір інженера-консультанта",
                value = details.value?.data?.engineerConsultantContractAmount?.toMoney() ?: "—",
                modifier = Modifier.weight(1f)
            )
            DetailMetricCard(
                title = "Технічний нагляд",
                value = details.value?.data?.technicalSupervisionAmount?.toMoney() ?: "—",
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                ProjectDetailTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(LocalizationManager.t(tab.titleKey)) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                when (selectedTab) {
                    ProjectDetailTab.GeneralInfo -> ProjectGeneralInfoTab(details.value?.data)
                    ProjectDetailTab.InspectionReports -> ProjectReportsTab(reports.value)
                    ProjectDetailTab.Financials -> ProjectFinancialsTab(project.id, financials.value, documents.value)
                    ProjectDetailTab.Documents -> ProjectDocumentsTab(project.id, documents.value)
                    ProjectDetailTab.Incidents -> ProjectHealthSafetyTab(healthSafetyObservations.value)
                }
            }
        }
    }
}

@Composable
private fun DetailMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

@Composable
private fun PlaceholderTabContent(title: String) {
    Card(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$title — ${LocalizationManager.t("coming_next")}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class ProjectDetailTab(val titleKey: String) {
    GeneralInfo("general_info_tab"),
    InspectionReports("inspection_reports_tab"),
    Financials("financials"),
    Documents("documents"),
    Incidents("incidents_hse")
}

@Composable
private fun ProjectHealthSafetyTab(data: ApiHealthSafetyObservations?) {
    when {
        data == null -> CircularProgressIndicator()
        data.uploadedReportsCount == 0 -> EmptyProjectTab(
            LocalizationManager.t("no_sir_reports_uploaded"),
            LocalizationManager.t("hse_upload_report_hint")
        )
        data.observations.isEmpty() -> EmptyProjectTab(
            LocalizationManager.t("no_hse_observations"),
            LocalizationManager.t("hse_section_hint")
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                LocalizationManager.t("hse_observations_title"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            data.observations.groupBy { it.inspectionDate }.forEach { (date, observations) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("${LocalizationManager.t("inspection_date_prefix")} ${date.toOmsDate()}", style = MaterialTheme.typography.labelLarge)
                        observations.forEach { item ->
                            HorizontalDivider()
                            Text(item.observation, style = MaterialTheme.typography.bodyLarge)
                            item.answer?.let { answer ->
                                Text("${LocalizationManager.t("answer")}: $answer", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                            }
                            item.comment?.let { comment ->
                                Text(comment, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyProjectTab(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProjectGeneralInfoTab(data: oms.data.ApiProjectDetailsData?) {
    Card(Modifier.fillMaxWidth()) {
        if (data == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val fields = listOf(
                "Тип запису" to when (data.projectType) {
                    "subproject" -> LocalizationManager.t("subproject")
                    "subproject_part" -> LocalizationManager.t("subproject_part")
                    else -> LocalizationManager.t("project")
                },
                "Код проєкту" to data.siteName,
                "Опис" to (data.description ?: "—"),
                "Адреса" to data.address,
                "Область" to data.region,
                "Населений пункт" to data.city,
                "Координати" to "${data.latitude}, ${data.longitude}",
                "Статус" to data.status.replace('_', ' '),
                "Сектор" to data.sector,
                LocalizationManager.t("construction_type") to data.constructionType.constructionTypeLabel(),
                "Підрядник" to (data.contractorName ?: "—"),
                "Валюта" to data.currency,
                "Плановий бюджет" to data.budgetPlanned.toMoney(),
                "Договір інженера-консультанта" to (data.engineerConsultantContractAmount?.toMoney() ?: "—"),
                "Технічний нагляд" to (data.technicalSupervisionAmount?.toMoney() ?: "—"),
                "Сума контракту субпроєкту" to (data.subprojectContractAmount?.toMoney() ?: "—"),
                "Дата початку" to data.startDate.toOmsDate(),
                "Дата завершення" to data.endDate.toOmsDate(),
                "Дата підписання контракту" to data.contractSignedDate.toOmsDate(),
                "Планова дата завершення" to data.plannedEndDate.toOmsDate(),
                "Дата договору на проєктування" to (data.designContractSigningDate ?: "—"),
                "Дата договору на будівництво" to (data.constructionContractSigningDate ?: "—"),
                "Початок будівництва" to (data.constructionStartDate ?: "—"),
                "Прогнозована дата завершення" to (data.projectedCompletionTime ?: "—"),
                "Тривалість контракту" to (data.contractDurationDays?.let { "$it днів" } ?: "—")
            )
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fields.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(label, modifier = Modifier.width(260.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ProjectReportsTab(reports: List<ApiInspectionReport>) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var selectedReport by remember { mutableStateOf<ApiInspectionReport?>(null) }
    selectedReport?.let { report ->
        AlertDialog(
            onDismissRequest = { selectedReport = null },
            title = { Text(report.summary ?: LocalizationManager.t("inspection_report")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${LocalizationManager.t("date")}: ${report.inspectionDate}")
                    Text("${LocalizationManager.t("status")}: ${report.status.replace('_', ' ')}")
                    report.rejectionReason?.let { Text("Причина повернення: $it") }
                }
            },
            confirmButton = {
                Button(onClick = { uriHandler.openUri("http://localhost:8080/api/v1/inspection-reports/${report.uuid}/source-file") }) {
                    Text("Завантажити XLS/XLSX")
                }
            },
            dismissButton = { TextButton(onClick = { selectedReport = null }) { Text(LocalizationManager.t("close")) } }
        )
    }
    Card(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (reports.isEmpty()) Text(LocalizationManager.t("no_reports"))
            reports.forEach { report ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = { selectedReport = report }) { Text("Відкрити") }
                    Button(onClick = { uriHandler.openUri("http://localhost:8080/api/v1/inspection-reports/${report.uuid}/source-file") }) {
                        Text("Завантажити XLS/XLSX")
                    }
                }
                Text(report.summary ?: LocalizationManager.t("inspection_report"), style = MaterialTheme.typography.titleMedium)
                Text("${report.inspectionDate} • ${report.status.replace('_', ' ')}")
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ProjectFinancialsTab(
    projectUuid: String,
    financials: ApiFinancialRecords?,
    documents: List<ApiProjectDocument>
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val acts = financials?.data.orEmpty().filter { it.recordType == "act" }
    val actDocuments = documents.filter { it.docType == "act" }
    Card(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${LocalizationManager.t("construction_contract")}: ${financials?.summary?.constructionContractAmount?.toMoney() ?: "—"}", style = MaterialTheme.typography.titleLarge)
            Text("${LocalizationManager.t("completed_works_by_acts")}: ${financials?.summary?.amountSpent?.toMoney() ?: "—"}")
            Text("${LocalizationManager.t("financial_completion")}: ${financials?.summary?.completionPct?.let { "${it.toInt()}%" } ?: "—"}")
            if (acts.isEmpty()) Text(LocalizationManager.t("no_acts"))
            acts.forEach { act ->
                Text("${act.referenceNumber} • ${act.recordDate.toOmsDate()} • ${act.amount.toMoney()}")
                HorizontalDivider()
            }
            if (actDocuments.isNotEmpty()) {
                Text(LocalizationManager.t("act_documents"), style = MaterialTheme.typography.titleMedium)
                actDocuments.forEach { document ->
                    Text(document.fileName)
                    Button(onClick = { uriHandler.openUri("http://localhost:8080/api/v1/projects/$projectUuid/documents/${document.uuid}/download") }) {
                        Text(LocalizationManager.t("open_document"))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectDocumentsTab(projectUuid: String, documents: List<ApiProjectDocument>) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Card(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (documents.isEmpty()) Text(LocalizationManager.t("no_project_documents"))
            documents.forEach { document ->
                Text(document.fileName, style = MaterialTheme.typography.titleMedium)
                Text("${document.docType} • ${document.fileSizeBytes} bytes")
                Button(onClick = { uriHandler.openUri("http://localhost:8080/api/v1/projects/$projectUuid/documents/${document.uuid}/download") }) { Text(LocalizationManager.t("open_document")) }
                HorizontalDivider()
            }
        }
    }
}

private fun Long.toMoney(): String = "${toString().reversed().chunked(3).joinToString(" ").reversed()} UAH"
