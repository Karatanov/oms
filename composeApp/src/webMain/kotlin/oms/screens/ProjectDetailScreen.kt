package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import oms.components.StatusChip
import oms.components.constructionTypeLabel
import oms.components.sectorLabel
import oms.data.ApiProjectDetails
import oms.data.ApiInspectionReport
import oms.data.ApiFinancialRecords
import oms.data.ApiProjectDocument
import oms.data.ApiHealthSafetyObservations
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.components.toOmsDate
import oms.localization.LocalizationManager
import oms.localization.Language
import oms.components.localizedUkraineRegion
import oms.model.Project
import oms.model.localizedName
import oms.navigation.Screen
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

@Composable
fun ProjectDetailScreen(
    project: Project,
    onBackToProjects: () -> Unit = {},
    onEdit: (Project) -> Unit = {},
    canEditProject: Boolean = false,
    canDeleteProject: Boolean = false,
    isGuest: Boolean = false,
    canAccessFinancials: Boolean = false
) {
    val parentProjectName = project.parentProjectUuid?.let { parentId ->
        ProjectRepository.projects.firstOrNull { it.id == parentId }?.localizedName()
    }
    var selectedTab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ProjectDetailTab.GeneralInfo) }
    val details = remember(project.id) { mutableStateOf<ApiProjectDetails?>(null) }
    val reports = remember(project.id) { mutableStateOf<List<ApiInspectionReport>>(emptyList()) }
    val financials = remember(project.id) { mutableStateOf<ApiFinancialRecords?>(null) }
    val documents = remember(project.id) { mutableStateOf<List<ApiProjectDocument>>(emptyList()) }
    val healthSafetyObservations = remember(project.id) { mutableStateOf<ApiHealthSafetyObservations?>(null) }
    var loadedResources by remember(project.id) { mutableStateOf<Set<ProjectDetailResource>>(emptySet()) }
    var loadError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var retryKey by remember { mutableStateOf(0) }
    var confirmDeletion by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val availableTabs = if (isGuest) {
        listOf(ProjectDetailTab.GeneralInfo)
    } else {
        ProjectDetailTab.entries.filter { it != ProjectDetailTab.Financials || canAccessFinancials }
    }
    LaunchedEffect(project.id, selectedTab, retryKey) {
        loadError = false
        loading = false
        suspend fun load(resource: ProjectDetailResource, block: suspend () -> Unit) {
            if (resource !in loadedResources) {
                loading = true
                try {
                    block()
                    loadedResources = loadedResources + resource
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { loadError = true }
                finally { loading = false }
            }
        }
        when (selectedTab) {
            ProjectDetailTab.GeneralInfo -> load(ProjectDetailResource.Details) {
                details.value = OmsApiClient.projectDetails(project.id)
            }
            ProjectDetailTab.InspectionReports -> load(ProjectDetailResource.Reports) {
                reports.value = OmsApiClient.projectReports(project.id)
            }
            ProjectDetailTab.Financials -> {
                load(ProjectDetailResource.Financials) {
                    financials.value = OmsApiClient.financials(project.id)
                }
                load(ProjectDetailResource.Documents) {
                    documents.value = OmsApiClient.projectDocuments(project.id)
                }
            }
            ProjectDetailTab.Documents -> load(ProjectDetailResource.Documents) {
                documents.value = OmsApiClient.projectDocuments(project.id)
            }
            ProjectDetailTab.Incidents -> load(ProjectDetailResource.Incidents) {
                healthSafetyObservations.value = OmsApiClient.healthSafetyObservations(project.id)
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
            TextButton(onClick = onBackToProjects, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)) {
                Text(LocalizationManager.t("projects"))
            }

            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))

            Text(
                text = project.localizedName(),
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
                            text = project.localizedName(),
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatusChip(project.status)

                            if (!project.projectType.equals("project", ignoreCase = true)) {
                                Text(
                                    text = "${LocalizationManager.t("address")}: ${details.value?.let(::localizedProjectAddress) ?: localizedUkraineRegion(project.region)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Text(
                            text = "${LocalizationManager.t("sector")}: ${details.value?.data?.sector?.sectorLabel() ?: "—"} • ${LocalizationManager.t("construction_type")}: ${details.value?.data?.constructionType?.constructionTypeLabel() ?: "—"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (canEditProject || canDeleteProject) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canEditProject) {
                        Button(onClick = { onEdit(project) }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = LocalizationManager.t("edit")
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(LocalizationManager.t("edit"))
                        }
                        }
                        if (canDeleteProject) {
                            OutlinedButton(
                                onClick = { confirmDeletion = true },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true),
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
                        OutlinedButton(
                            onClick = { confirmDeletion = false },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
                        ) { Text(LocalizationManager.t("cancel")) }
                        Button(
                            onClick = {
                                scope.launch {
                                    if (OmsApiClient.deleteProject(project.id)) {
                                        ProjectRepository.refresh(force = true)
                                        onBackToProjects()
                                    } else deleteError = LocalizationManager.t("error_delete_project")
                                }
                            },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text(LocalizationManager.t("delete")) }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            PrimaryScrollableTabRow(selectedTabIndex = availableTabs.indexOf(selectedTab).coerceAtLeast(0)) {
                availableTabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true),
                        text = { Text(LocalizationManager.t(tab.titleKey)) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                if (loading) oms.components.ContentState(LocalizationManager.t("loading_records"), loading = true)
                else if (loadError) oms.components.ContentState(LocalizationManager.t("load_records_error"), error = true, onRetry = { retryKey++ })
                else when (selectedTab) {
                    ProjectDetailTab.GeneralInfo -> ProjectGeneralInfoTab(details.value)
                    ProjectDetailTab.InspectionReports -> ProjectReportsTab(reports.value)
                    ProjectDetailTab.Financials -> ProjectFinancialsTab(financials.value)
                    ProjectDetailTab.Documents -> ProjectDocumentsTab(project.id, documents.value)
                    ProjectDetailTab.Incidents -> ProjectHealthSafetyTab(healthSafetyObservations.value)
                }
            }
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
                                Text(LocalizationManager.hseObservation(item.observation), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
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
                                        LocalizationManager.hseObservation(comment),
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

private fun String?.isPositiveHseAnswer() = this?.trim()?.lowercase()?.let {
    it == "y" || it == "так" || it == "true" || it == "1" || Regex("\\byes\\b").containsMatchIn(it)
} == true
private fun String?.isNegativeHseAnswer() = this?.trim()?.lowercase()?.let {
    it == "ні" || it == "false" || it == "0" || Regex("\\bno\\b").containsMatchIn(it)
} == true

@Composable
private fun EmptyProjectTab(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun localizedProjectAddress(details: oms.data.ApiProjectDetails): String {
    val data = details.data
    if (LocalizationManager.currentLanguage != Language.EN) return data.address.ifBlank { "—" }
    val englishSourceName = data.nameEn?.takeIf(String::isNotBlank) ?: details.monitoringDetails?.nameEn
    val extracted = englishSourceName?.let {
        Regex("(?:at the address|address)\\s*[:,-]\\s*(.+)$", RegexOption.IGNORE_CASE)
            .find(it)?.groupValues?.getOrNull(1)?.trim(' ', '"')
    }
    if (!extracted.isNullOrBlank()) return extracted
    return listOfNotNull(
        details.monitoringDetails?.settlementNameEn?.takeIf(String::isNotBlank),
        localizedUkraineRegion(data.region).takeIf(String::isNotBlank),
        "Ukraine"
    ).distinct().joinToString(", ").ifBlank { data.address.ifBlank { "—" } }
}

private fun localizedOrganisationName(value: String?): String {
    val source = value?.takeIf(String::isNotBlank) ?: return "—"
    if (LocalizationManager.currentLanguage != Language.EN || source.none { it.lowercaseChar() in 'а'..'я' || it in "іїєґІЇЄҐ" }) return source
    val replacements = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "h", 'ґ' to "g", 'д' to "d", 'е' to "e", 'є' to "ie",
        'ж' to "zh", 'з' to "z", 'и' to "y", 'і' to "i", 'ї' to "i", 'й' to "i", 'к' to "k", 'л' to "l",
        'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ь' to "",
        'ю' to "iu", 'я' to "ia", 'ы' to "y", 'э' to "e", 'ъ' to ""
    )
    return buildString {
        source.forEach { char ->
            val mapped = replacements[char.lowercaseChar()]
            if (mapped == null) append(char)
            else append(if (char.isUpperCase()) mapped.replaceFirstChar(Char::uppercaseChar) else mapped)
        }
    }
}

@Composable
private fun ProjectGeneralInfoTab(details: oms.data.ApiProjectDetails?) {
    Card(Modifier.fillMaxWidth()) {
        if (details == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val data = details.data
            val general = listOf(
                LocalizationManager.t("name") to (if (LocalizationManager.currentLanguage == Language.EN) data.nameEn?.takeIf(String::isNotBlank) ?: details.monitoringDetails?.nameEn ?: data.name else data.name),
                LocalizationManager.t("record_type") to when (data.projectType) {
                    "subproject" -> LocalizationManager.t("subproject")
                    "subproject_part" -> LocalizationManager.t("subproject_part")
                    else -> LocalizationManager.t("project")
                },
                LocalizationManager.t("project_code") to data.siteName,
                LocalizationManager.t("description") to (data.description ?: "—"),
                LocalizationManager.t("status") to LocalizationManager.t("project_status_${data.status}"),
                LocalizationManager.t("sector") to data.sector.sectorLabel(),
                LocalizationManager.t("construction_type") to data.constructionType.constructionTypeLabel()
            )
            val location = listOf(
                LocalizationManager.t("address") to localizedProjectAddress(details),
                LocalizationManager.t("region") to localizedUkraineRegion(data.region.ifBlank { "—" }),
                LocalizationManager.t("city") to (if (LocalizationManager.currentLanguage == Language.EN) details.monitoringDetails?.settlementNameEn ?: data.city else data.city).ifBlank { "—" },
                LocalizationManager.t("coordinates") to "${data.latitude}, ${data.longitude}"
            )
            val finance = listOf(
                LocalizationManager.t("currency") to data.currency,
                LocalizationManager.t("budget") to data.budgetPlanned.toMoney(),
                LocalizationManager.t("subproject_cost_eib_financing") to (data.amounts["eib_financing"]?.toMoney() ?: "—"),
                LocalizationManager.t("subproject_cost_local_financing") to (data.amounts["local_financing"]?.toMoney() ?: "—"),
                LocalizationManager.t("subproject_contract_amount") to (data.subprojectContractAmount?.toMoney() ?: "—"),
                LocalizationManager.t("technical_supervision_contract_amount") to (data.technicalSupervisionAmount?.toMoney() ?: "—"),
                LocalizationManager.t("engineer_consultant_contract_amount") to (data.engineerConsultantContractAmount?.toMoney() ?: "—")
            )
            val designer = listOf(
                LocalizationManager.t("designer_name") to (data.designerName ?: "—"),
                LocalizationManager.t("contract_number") to (data.designContractNumber ?: "—"),
                LocalizationManager.t("design_contract_date") to data.designContractSigningDate.toOmsDate(),
                LocalizationManager.t("design_start_date") to data.designStartDate.toOmsDate(),
                LocalizationManager.t("design_planned_end_date") to data.designPlannedEndDate.toOmsDate(),
                LocalizationManager.t("design_contract_term") to (data.designDurationDays?.let(::durationMonthsLabel) ?: "—")
            )
            val contractor = listOf(
                LocalizationManager.t("contractor") to if (LocalizationManager.currentLanguage == Language.EN) {
                    details.contractorNameEn?.takeIf(String::isNotBlank) ?: localizedOrganisationName(data.contractorName)
                } else localizedOrganisationName(data.contractorName),
                LocalizationManager.t("contract_number") to (data.constructionContractNumber ?: "—"),
                LocalizationManager.t("construction_contract_date") to data.constructionContractSigningDate.toOmsDate(),
                LocalizationManager.t("construction_start_date") to data.constructionStartDate.toOmsDate(),
                LocalizationManager.t("projected_completion_date") to data.projectedCompletionTime.toOmsDate(),
                LocalizationManager.t("contract_duration") to (data.contractDurationDays?.let(::durationMonthsLabel) ?: "—")
            )
            val technical = listOf(
                LocalizationManager.t("name") to localizedOrganisationName(data.technicalSupervisionName),
                LocalizationManager.t("contract_number") to (data.technicalSupervisionContractNumber ?: "—"),
                LocalizationManager.t("contract_date") to data.technicalSupervisionContractDate.toOmsDate(),
                LocalizationManager.t("design_start_date") to data.technicalSupervisionStartDate.toOmsDate(),
                LocalizationManager.t("design_planned_end_date") to data.technicalSupervisionPlannedEndDate.toOmsDate(),
                LocalizationManager.t("contract_duration") to (data.technicalSupervisionDurationDays?.let(::durationMonthsLabel) ?: "—")
            )
            val engineer = listOf(
                LocalizationManager.t("name") to localizedOrganisationName(data.engineerConsultantName),
                LocalizationManager.t("contract_number") to (data.engineerConsultantContractNumber ?: "—"),
                LocalizationManager.t("contract_date") to data.engineerConsultantContractDate.toOmsDate(),
                LocalizationManager.t("design_start_date") to data.engineerConsultantStartDate.toOmsDate(),
                LocalizationManager.t("design_planned_end_date") to data.engineerConsultantPlannedEndDate.toOmsDate(),
                LocalizationManager.t("contract_duration") to (data.engineerConsultantDurationDays?.let(::durationMonthsLabel) ?: "—")
            )
            val procurement = details.monitoringDetails?.let {
                listOf(
                    LocalizationManager.t("procurement_status") to (it.constructionProcurementStatus?.let(LocalizationManager::procurementStatus) ?: "—"),
                    LocalizationManager.t("work_status") to (it.constructionWorkStatus ?: "—")
                )
            }.orEmpty()
            val source = buildList {
                details.programmeDetails?.let { add(LocalizationManager.t("implementor") to it.implementor); add(LocalizationManager.t("financing_institution") to it.financingInstitution); add(LocalizationManager.t("finance_contract_number") to it.financeContractNumber); add("Serapis" to it.serapisNumber) }
                details.monitoringDetails?.let { add(LocalizationManager.t("source_subproject_id") to it.sourceSubprojectId); add(LocalizationManager.t("source_lot_id") to it.sourceLotId); add(LocalizationManager.t("english_name") to (it.nameEn ?: "—")); add(LocalizationManager.t("municipality") to (if (LocalizationManager.currentLanguage == Language.EN) it.municipalityNameEn ?: it.municipalityNameUk else it.municipalityNameUk) .orEmpty().ifBlank { "—" }); add(LocalizationManager.t("beneficiary") to (if (LocalizationManager.currentLanguage == Language.EN) it.beneficiaryNameEn ?: it.beneficiaryNameUk else it.beneficiaryNameUk).orEmpty().ifBlank { "—" }); add(LocalizationManager.t("project_manager") to (if (LocalizationManager.currentLanguage == Language.EN) it.projectManagerNameEn ?: it.projectManagerNameUk else it.projectManagerNameUk).orEmpty().ifBlank { "—" }); add(LocalizationManager.t("coordinate_accuracy") to LocalizationManager.t("geocode_accuracy_${it.geocodeAccuracy ?: "unknown"}")) }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CollapsibleProjectSection(LocalizationManager.t("basic_information"), Icons.Default.Info, general, initiallyExpanded = true)
                if (!data.projectType.equals("project", true)) CollapsibleProjectSection(LocalizationManager.t("parameters_and_location"), Icons.Default.LocationOn, location, showMapLink = true, latitude = data.latitude, longitude = data.longitude)
                CollapsibleProjectSection(LocalizationManager.t("section_finance"), Icons.Default.Payments, finance)
                CollapsibleProjectSection(LocalizationManager.t("designer_information"), Icons.Default.Info, designer)
                CollapsibleProjectSection(LocalizationManager.t("construction_contractor_information"), Icons.Default.Info, contractor)
                CollapsibleProjectSection(LocalizationManager.t("technical_supervision_information"), Icons.Default.Info, technical)
                CollapsibleProjectSection(LocalizationManager.t("engineer_consultant_information"), Icons.Default.Info, engineer)
                if (procurement.isNotEmpty()) CollapsibleProjectSection(LocalizationManager.t("procurement_title"), Icons.Default.Info, procurement)
                if (source.isNotEmpty()) CollapsibleProjectSection(LocalizationManager.t("source_information"), Icons.Default.Info, source)
            }
        }
    }
}

@Composable
private fun CollapsibleProjectSection(
    title: String,
    icon: ImageVector,
    fields: List<Pair<String, String>>,
    showMapLink: Boolean = false,
    latitude: Double = 0.0,
    longitude: Double = 0.0,
    initiallyExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val mapUrl = "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=16/$latitude/$longitude"
    Column {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Start)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, LocalizationManager.t(if (expanded) "collapse" else "expand"))
        }
        if (expanded) {
            fields.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, modifier = Modifier.width(210.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()
            }
            if (showMapLink) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(mapUrl)) }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text(LocalizationManager.t("copy_map_link")) }
                TextButton(onClick = { uriHandler.openUri(mapUrl) }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text(LocalizationManager.t("open_in_openstreetmap")) }
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
                    Button(
                        onClick = { uriHandler.openUri(oms.data.omsApiUrl("/inspection-reports/${report.uuid}/source-file")) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
                    ) {
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
private fun ProjectFinancialsTab(financials: ApiFinancialRecords?) {
    val acts = financials?.data.orEmpty().filter { it.recordType == "act" }
    Card(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (acts.isEmpty()) Text(LocalizationManager.t("no_acts"))
            else {
                Text(LocalizationManager.t("acts_of_completed_works"), style = MaterialTheme.typography.titleMedium)
                acts.forEach { act ->
                    Text("${act.referenceNumber} • ${act.recordDate.toOmsDate()} • ${act.amount.toMoney()}")
                    HorizontalDivider()
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
                Button(
                    onClick = { uriHandler.openUri(oms.data.omsApiUrl("/projects/$projectUuid/documents/${document.uuid}/download")) },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
                ) { Text(LocalizationManager.t("open_document")) }
                HorizontalDivider()
            }
        }
    }
}

private fun Double.toMoney(): String = "${formatUiAmount()} UAH"
private fun Long.toMoney(): String = "${formatUiAmount()} UAH"
private fun oms.data.ProjectAmountDto.toMoney(): String =
    "${amount.toDoubleOrNull()?.formatUiAmount() ?: amount} $currency"

/** Locale-independent formatter that also works in Kotlin/Wasm. */
private fun Long.formatUiAmount(): String {
    val sign = if (this < 0) "-" else ""
    val digits = if (this < 0) (-this).toString() else toString()
    return sign + digits.reversed().chunked(3).joinToString(" ").reversed()
}

private fun Double.formatUiAmount(): String {
    val scaled = (this * 100).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val absolute = kotlin.math.abs(scaled)
    val whole = absolute / 100
    val fraction = absolute % 100
    val groupedWhole = whole.toString().reversed().chunked(3).joinToString(" ").reversed()
    return if (fraction == 0L) "$sign$groupedWhole" else "$sign$groupedWhole,${fraction.toString().padStart(2, '0').trimEnd('0')}"
}
