package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch

@Composable
fun ProjectDetailScreen(
    project: Project,
    onBackToProjects: () -> Unit = {},
    onEdit: (Project) -> Unit = {},
    canDeleteProject: Boolean = false
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
    var loadedResources by remember(project.id) { mutableStateOf<Set<ProjectDetailResource>>(emptySet()) }
    var confirmDeletion by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(project.id, selectedTab) {
        suspend fun load(resource: ProjectDetailResource, block: suspend () -> Unit) {
            if (resource !in loadedResources) {
                block()
                loadedResources = loadedResources + resource
            }
        }
        when (selectedTab) {
            ProjectDetailTab.GeneralInfo -> load(ProjectDetailResource.Details) {
                details.value = runCatching { OmsApiClient.projectDetails(project.id) }.getOrNull()
            }
            ProjectDetailTab.InspectionReports -> load(ProjectDetailResource.Reports) {
                reports.value = runCatching { OmsApiClient.projectReports(project.id) }.getOrDefault(emptyList())
            }
            ProjectDetailTab.Financials -> {
                load(ProjectDetailResource.Financials) {
                    financials.value = runCatching { OmsApiClient.financials(project.id) }.getOrNull()
                }
                load(ProjectDetailResource.Documents) {
                    documents.value = runCatching { OmsApiClient.projectDocuments(project.id) }.getOrDefault(emptyList())
                }
            }
            ProjectDetailTab.Documents -> load(ProjectDetailResource.Documents) {
                documents.value = runCatching { OmsApiClient.projectDocuments(project.id) }.getOrDefault(emptyList())
            }
            ProjectDetailTab.Incidents -> load(ProjectDetailResource.Incidents) {
                healthSafetyObservations.value = runCatching { OmsApiClient.healthSafetyObservations(project.id) }.getOrNull()
            }
        }
    }

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
                Text(LocalizationManager.t("projects"))
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
                text = "${LocalizationManager.t("project")}: $parentName",
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onEdit(project) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = LocalizationManager.t("edit")
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(LocalizationManager.t("edit"))
                        }
                        if (canDeleteProject) {
                            OutlinedButton(
                                onClick = { confirmDeletion = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = LocalizationManager.t("delete_project"))
                                Spacer(Modifier.width(8.dp))
                                Text(LocalizationManager.t("delete"))
                            }
                        }
                    }
                }
            }
        }

        if (confirmDeletion) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(LocalizationManager.t("delete_project"), style = MaterialTheme.typography.titleMedium)
                    Text(LocalizationManager.t("delete_project_confirmation").replace("{name}", project.name))
                    deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        OutlinedButton(onClick = { confirmDeletion = false }) { Text(LocalizationManager.t("cancel")) }
                        Button(
                            onClick = {
                                scope.launch {
                                    if (OmsApiClient.deleteProject(project.id)) {
                                        ProjectRepository.refresh()
                                        onBackToProjects()
                                    } else deleteError = LocalizationManager.t("error_delete_project")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text(LocalizationManager.t("delete")) }
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
                    title = LocalizationManager.t("subproject_contract_amount"),
                    value = details.value?.data?.subprojectContractAmount?.toMoney() ?: "—",
                    modifier = Modifier.weight(1f)
                )
                DetailMetricCard(
                    title = LocalizationManager.t("contract_duration"),
                    value = details.value?.data?.contractDurationDays?.let { LocalizationManager.t("days_value").replace("{count}", it.toString()) } ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DetailMetricCard(
                title = LocalizationManager.t("engineer_consultant_contract"),
                value = details.value?.data?.engineerConsultantContractAmount?.toMoney() ?: "—",
                modifier = Modifier.weight(1f)
            )
            DetailMetricCard(
                title = LocalizationManager.t("technical_supervision"),
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

private enum class ProjectDetailTab(val titleKey: String) {
    GeneralInfo("general_info_tab"),
    InspectionReports("inspection_reports_tab"),
    Financials("financials"),
    Documents("documents"),
    Incidents("incidents_hse")
}

private enum class ProjectDetailResource { Details, Reports, Financials, Documents, Incidents }

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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.observation, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                HseAnswerIndicator(item.answer)
                            }
                            item.comment?.let { comment ->
                                val backgroundColor = when {
                                    item.answer.isNegativeHseAnswer() -> MaterialTheme.colorScheme.errorContainer
                                    item.answer.isPositiveHseAnswer() -> Color(0xFFE8F5E9)
                                    else -> Color(0xFFFFF3E0)
                                }
                                val textColor = when {
                                    item.answer.isNegativeHseAnswer() -> MaterialTheme.colorScheme.onErrorContainer
                                    item.answer.isPositiveHseAnswer() -> Color(0xFF1B5E20)
                                    else -> Color(0xFFE65100)
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = backgroundColor
                                    )
                                ) {
                                    Text(
                                        comment,
                                        modifier = Modifier.padding(10.dp),
                                        color = textColor,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HseAnswerIndicator(answer: String?) {
    when {
        answer.isPositiveHseAnswer() -> HseAnswerBadge(
            icon = Icons.Default.CheckCircle,
            contentDescription = LocalizationManager.t("hse_compliant"),
            iconColor = Color(0xFF1B5E20),
            containerColor = Color(0xFFE8F5E9)
        )
        answer.isNegativeHseAnswer() -> HseAnswerBadge(
            icon = Icons.Default.Cancel,
            contentDescription = LocalizationManager.t("hse_issue"),
            iconColor = MaterialTheme.colorScheme.error,
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
        !answer.isNullOrBlank() -> HseAnswerBadge(
            icon = Icons.Default.Warning,
            contentDescription = LocalizationManager.t("answer"),
            iconColor = Color(0xFFEF6C00),
            containerColor = Color(0xFFFFF3E0)
        )
    }
}

@Composable
private fun HseAnswerBadge(
    icon: ImageVector,
    contentDescription: String,
    iconColor: Color,
    containerColor: Color
) {
    Surface(color = containerColor, shape = MaterialTheme.shapes.extraLarge) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.padding(6.dp).size(20.dp)
        )
    }
}

private fun String?.isPositiveHseAnswer() = this?.trim()?.lowercase()?.let { it == "y" || it == "так" || it.startsWith("yes") } == true
private fun String?.isNegativeHseAnswer() = this?.trim()?.lowercase()?.startsWith("no") == true || this?.trim()?.lowercase() == "ні"

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
                LocalizationManager.t("record_type") to when (data.projectType) {
                    "subproject" -> LocalizationManager.t("subproject")
                    "subproject_part" -> LocalizationManager.t("subproject_part")
                    else -> LocalizationManager.t("project")
                },
                LocalizationManager.t("project_code") to data.siteName,
                LocalizationManager.t("description") to (data.description ?: "—"),
                LocalizationManager.t("address") to data.address,
                LocalizationManager.t("region") to data.region,
                LocalizationManager.t("city") to data.city,
                LocalizationManager.t("coordinates") to "${data.latitude}, ${data.longitude}",
                LocalizationManager.t("status") to LocalizationManager.t("project_status_${data.status}"),
                LocalizationManager.t("sector") to data.sector,
                LocalizationManager.t("construction_type") to data.constructionType.constructionTypeLabel(),
                LocalizationManager.t("contractor") to (data.contractorName ?: "—"),
                LocalizationManager.t("currency") to data.currency,
                LocalizationManager.t("budget") to data.budgetPlanned.toMoney(),
                LocalizationManager.t("engineer_consultant_contract") to (data.engineerConsultantContractAmount?.toMoney() ?: "—"),
                LocalizationManager.t("technical_supervision") to (data.technicalSupervisionAmount?.toMoney() ?: "—"),
                LocalizationManager.t("subproject_contract_amount") to (data.subprojectContractAmount?.toMoney() ?: "—"),
                LocalizationManager.t("start_date") to data.startDate.toOmsDate(),
                LocalizationManager.t("end_date") to data.endDate.toOmsDate(),
                LocalizationManager.t("contract_signed_date") to data.contractSignedDate.toOmsDate(),
                LocalizationManager.t("planned_end_date") to data.plannedEndDate.toOmsDate(),
                LocalizationManager.t("design_contract_date") to (data.designContractSigningDate ?: "—"),
                LocalizationManager.t("construction_contract_date") to (data.constructionContractSigningDate ?: "—"),
                LocalizationManager.t("construction_start_date") to (data.constructionStartDate ?: "—"),
                LocalizationManager.t("projected_completion_date") to (data.projectedCompletionTime ?: "—"),
                LocalizationManager.t("contract_duration") to (data.contractDurationDays?.let { LocalizationManager.t("days_value").replace("{count}", it.toString()) } ?: "—")
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
    Card(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (reports.isEmpty()) Text(LocalizationManager.t("no_reports"))
            reports.forEach { report ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    Button(onClick = { uriHandler.openUri(oms.data.omsApiUrl("/inspection-reports/${report.uuid}/source-file")) }) {
                        Text(LocalizationManager.t("upload_xls"))
                    }
                }
                Text(report.summary ?: LocalizationManager.t("inspection_report"), style = MaterialTheme.typography.titleMedium)
                Text("${report.inspectionDate} • ${LocalizationManager.t("${report.status}_status")}")
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
                    Button(onClick = { uriHandler.openUri(oms.data.omsApiUrl("/projects/$projectUuid/documents/${document.uuid}/download")) }) {
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
                Text("${document.docType} • ${document.fileSizeBytes} ${LocalizationManager.t("bytes")}")
                Button(onClick = { uriHandler.openUri(oms.data.omsApiUrl("/projects/$projectUuid/documents/${document.uuid}/download")) }) { Text(LocalizationManager.t("open_document")) }
                HorizontalDivider()
            }
        }
    }
}

private fun Long.toMoney(): String = "${toString().reversed().chunked(3).joinToString(" ").reversed()} UAH"
