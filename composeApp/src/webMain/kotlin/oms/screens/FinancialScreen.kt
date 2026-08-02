package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import oms.data.ApiFinancialRecord
import oms.data.ApiProjectDocument
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.localization.LocalizationManager

private data class ProjectActRow(
    val projectUuid: String,
    val projectName: String,
    val act: ApiFinancialRecord
)

private data class ActDocumentRow(
    val projectUuid: String,
    val projectName: String,
    val document: ApiProjectDocument
)

@Composable
fun FinancialScreen(
    canAccessFinancials: Boolean = true,
    canManageFinancials: Boolean = true
) {
    if (!canAccessFinancials) {
        FinancialAccessDenied()
        return
    }

    val uriHandler = LocalUriHandler.current
    var acts by remember { mutableStateOf<List<ProjectActRow>>(emptyList()) }
    var actDocuments by remember { mutableStateOf<List<ActDocumentRow>>(emptyList()) }

    LaunchedEffect(Unit) {
        ProjectRepository.refresh()
        acts = ProjectRepository.projects.flatMap { project ->
            runCatching { OmsApiClient.financials(project.id) }.getOrNull()?.data.orEmpty()
                .filter { it.recordType == "act" }
                .map { ProjectActRow(project.id, project.name, it) }
        }.sortedByDescending { it.act.recordDate }
        actDocuments = ProjectRepository.projects.flatMap { project ->
            runCatching { OmsApiClient.projectDocuments(project.id) }.getOrDefault(emptyList())
                .filter { it.docType == "act" }
                .map { ActDocumentRow(project.id, project.name, it) }
        }
    }

    val completedWorksTotal = acts.sumOf { it.act.amount }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(LocalizationManager.t("financial_monitoring"), style = MaterialTheme.typography.headlineMedium)
        Text("Only completed works confirmed by acts are included.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sum of completed works by acts", style = MaterialTheme.typography.titleMedium)
                Text(completedWorksTotal.toMoney(), style = MaterialTheme.typography.headlineMedium)
            }
        }

        Text("Acts", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FinancialTableHeader()
                HorizontalDivider()
                if (acts.isEmpty()) Text("No acts found.")
                acts.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(row.act.referenceNumber, Modifier.width(130.dp))
                        Text(row.projectName, Modifier.weight(1.25f))
                        Text(row.act.recordDate, Modifier.width(105.dp))
                        Text(row.act.paymentDate ?: "—", Modifier.width(105.dp))
                        Text(row.act.amount.toMoney(), Modifier.width(130.dp))
                        Text(row.act.currency, Modifier.width(65.dp))
                        Text(row.act.milestone ?: "—", Modifier.weight(1f))
                        Text("admin", Modifier.width(75.dp))
                    }
                    HorizontalDivider()
                }
            }
        }

        Text("Act documents", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (actDocuments.isEmpty()) Text("No act documents found.")
                actDocuments.forEach { row ->
                    Text(row.document.fileName, style = MaterialTheme.typography.titleMedium)
                    Text(row.projectName)
                    Button(onClick = { uriHandler.openUri("http://localhost:8080/api/v1/projects/${row.projectUuid}/documents/${row.document.uuid}/download") }) {
                        Text("Open document")
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FinancialTableHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("Act no.", Modifier.width(130.dp), style = MaterialTheme.typography.labelLarge)
        Text("Project", Modifier.weight(1.25f), style = MaterialTheme.typography.labelLarge)
        Text("Act date", Modifier.width(105.dp), style = MaterialTheme.typography.labelLarge)
        Text("Payment date", Modifier.width(105.dp), style = MaterialTheme.typography.labelLarge)
        Text("Amount", Modifier.width(130.dp), style = MaterialTheme.typography.labelLarge)
        Text("Curr.", Modifier.width(65.dp), style = MaterialTheme.typography.labelLarge)
        Text("Milestone", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text("By", Modifier.width(75.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FinancialAccessDenied() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Card(Modifier.widthIn(max = 520.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LocalizationManager.t("access_restricted"), style = MaterialTheme.typography.headlineSmall)
                Text(LocalizationManager.t("financial_access_restricted"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun Long.toMoney(): String = "${toString().reversed().chunked(3).joinToString(" ").reversed()} UAH"
