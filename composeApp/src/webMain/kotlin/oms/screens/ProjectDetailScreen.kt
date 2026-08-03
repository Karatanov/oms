package oms.screens

import androidx.compose.foundation.layout.*
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
import oms.data.ApiProjectDetails
import oms.data.ApiInspectionReport
import oms.data.ApiFinancialRecords
import oms.data.ApiProjectDocument
import oms.data.OmsApiClient
import oms.localization.LocalizationManager
import oms.model.Project
import oms.navigation.Screen

@Composable
fun ProjectDetailScreen(
    project: Project,
    onBackToProjects: () -> Unit = {},
    onEdit: (Project) -> Unit = {}
) {
    var selectedTab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ProjectDetailTab.GeneralInfo) }
    val details = remember(project.id) { mutableStateOf<ApiProjectDetails?>(null) }
    val reports = remember(project.id) { mutableStateOf<List<ApiInspectionReport>>(emptyList()) }
    val financials = remember(project.id) { mutableStateOf<ApiFinancialRecords?>(null) }
    val documents = remember(project.id) { mutableStateOf<List<ApiProjectDocument>>(emptyList()) }
    LaunchedEffect(project.id) { details.value = runCatching { OmsApiClient.projectDetails(project.id) }.getOrNull() }
    LaunchedEffect(project.id) { reports.value = runCatching { OmsApiClient.projectReports(project.id) }.getOrDefault(emptyList()) }
    LaunchedEffect(project.id) { financials.value = runCatching { OmsApiClient.financials(project.id) }.getOrNull() }
    LaunchedEffect(project.id) { documents.value = runCatching { OmsApiClient.projectDocuments(project.id) }.getOrDefault(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                            text = "${LocalizationManager.t("sector")}: ${details.value?.data?.sector ?: "—"} • ${LocalizationManager.t("construction_type")}: ${details.value?.data?.constructionType ?: "—"}",
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
                title = LocalizationManager.t("budget_planned"),
                value = details.value?.financialSummary?.budgetPlanned?.toMoney() ?: "—",
                modifier = Modifier.weight(1f)
            )
            DetailMetricCard(
                title = LocalizationManager.t("amount_spent"),
                value = details.value?.financialSummary?.amountSpent?.toMoney() ?: "—",
                modifier = Modifier.weight(1f)
            )
            DetailMetricCard(
                title = LocalizationManager.t("budget_remaining"),
                value = details.value?.financialSummary?.budgetRemaining?.toMoney() ?: "—",
                modifier = Modifier.weight(1f)
            )
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
                        text = { Text(tab.title) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp)
            ) {
                when (selectedTab) {
                    ProjectDetailTab.GeneralInfo -> PlaceholderTabContent(LocalizationManager.t("general_info"))
                    ProjectDetailTab.InspectionReports -> ProjectReportsTab(reports.value)
                    ProjectDetailTab.Financials -> ProjectFinancialsTab(project.id, financials.value, documents.value)
                    ProjectDetailTab.Documents -> ProjectDocumentsTab(project.id, documents.value)
                    ProjectDetailTab.Incidents -> PlaceholderTabContent(LocalizationManager.t("incidents"))
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

private enum class ProjectDetailTab(val title: String) {
    GeneralInfo("General Info"),
    InspectionReports("Inspection Reports"),
    Financials("Financials"),
    Documents("Documents"),
    Incidents("Incidents (HSE)")
}

@Composable
private fun ProjectReportsTab(reports: List<ApiInspectionReport>) {
    Card(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (reports.isEmpty()) Text("No inspection reports found.")
            reports.forEach { report ->
                Text(report.summary ?: "Inspection report", style = MaterialTheme.typography.titleMedium)
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
            Text("Sum of completed works by acts: ${financials?.summary?.amountSpent?.toMoney() ?: "—"}", style = MaterialTheme.typography.titleLarge)
            if (acts.isEmpty()) Text("No acts found.")
            acts.forEach { act ->
                Text("${act.referenceNumber} • ${act.recordDate} • ${act.amount.toMoney()}")
                HorizontalDivider()
            }
            if (actDocuments.isNotEmpty()) {
                Text("Act documents", style = MaterialTheme.typography.titleMedium)
                actDocuments.forEach { document ->
                    Text(document.fileName)
                    Button(onClick = { uriHandler.openUri("http://localhost:8080/api/v1/projects/$projectUuid/documents/${document.uuid}/download") }) {
                        Text("Open document")
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
            if (documents.isEmpty()) Text("No project documents found.")
            documents.forEach { document ->
                Text(document.fileName, style = MaterialTheme.typography.titleMedium)
                Text("${document.docType} • ${document.fileSizeBytes} bytes")
                Button(onClick = { uriHandler.openUri("http://localhost:8080/api/v1/projects/$projectUuid/documents/${document.uuid}/download") }) { Text("Open document") }
                HorizontalDivider()
            }
        }
    }
}

private fun Long.toMoney(): String = "${toString().reversed().chunked(3).joinToString(" ").reversed()} UAH"
