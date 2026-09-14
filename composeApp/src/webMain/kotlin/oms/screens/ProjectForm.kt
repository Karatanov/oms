package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oms.components.*
import oms.data.ApiProject
import oms.localization.LocalizationManager as L

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProjectForm(
    state: ProjectFormState,
    parents: List<ApiProject>,
    editing: Boolean,
    loadParentsOnOpen: (suspend () -> List<ApiProject>)? = null
) {
    var geocoding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun programmeParent(options: List<ApiProject>): ApiProject? =
        options.firstOrNull {
            it.projectType.equals("project", true) &&
                it.name.equals("Ukraine Recovery Programme III", true)
        } ?: options.firstOrNull { it.projectType.equals("project", true) }
    fun field(key: String, label: String, modifier: Modifier = Modifier, lines: Int = 1) = @Composable {
        OutlinedTextField(state[key], { state[key] = it }, label = { Text(L.t(label)) }, modifier = modifier,
            singleLine = lines == 1, minLines = lines)
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormSectionTitle(L.t("basic_information"), Icons.Default.Folder)
            if (!editing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("project", "subproject", "subproject_part").forEach { type ->
                        FilterChip(selected = state.projectType == type, onClick = {
                            state.projectType = type
                            state.parentUuid = null
                            // A newly created subproject belongs to the programme by
                            // default.  Loading happens in the background so changing
                            // the type never blocks the form.
                            if (type == "subproject") scope.launch {
                                val options = loadParentsOnOpen?.invoke() ?: parents
                                state.parentUuid = programmeParent(options)?.uuid
                            }
                        }, label = { Text(L.t(type)) })
                    }
                }
                if (state.projectType != "project") {
                    val parentType = if (state.projectType == "subproject") "project" else "subproject"
                    val choices = parents.filter { it.projectType == parentType }
                    InlineOptionPicker(choices, choices.firstOrNull { it.uuid == state.parentUuid },
                        L.t(if (parentType == "project") "select_parent_project" else "select_parent_subproject"),
                        { state.parentUuid = it.uuid }, { "${it.siteNumber} — ${it.name}" },
                        loadOptionsOnOpen = {
                            (loadParentsOnOpen?.invoke() ?: choices).filter { it.projectType == parentType }
                        })
                }
            }
            FormPair(
                { field("name", "project_name_required", it)() },
                { field("code", if (state.projectType == "subproject_part") "subproject_part_code" else "project_code_required", it)() }
            )
            InlineOptionPicker(
                options = listOf(1, 2),
                selected = state.trancheNumber(),
                prompt = L.t("tranche"),
                onSelect = { state["tranche"] = if (it == 2) "B" else "A" },
                itemLabel = { if (it == 2) L.t("tranche_b") else L.t("tranche_a") },
                modifier = Modifier.widthIn(max = 220.dp),
                fillWidth = false
            )
            field("description", "description", Modifier.fillMaxWidth(), 3)()
            Text(L.t("status"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("planned", "active", "suspended", "completed", "archived", "dlp").forEach { status ->
                    FilterChip(state["status"] == status, { state["status"] = status }, { Text(L.t("project_status_$status")) })
                }
            }
            FormPair(
                { SectorSelector(state["sector"], { value -> state["sector"] = value }, it) },
                { ConstructionTypeSelector(state["constructionType"], { value -> state["constructionType"] = value }, it) }
            )
            FormSectionTitle(L.t("financial_parameters"), Icons.Default.AccountBalanceWallet)
            ProjectMoneyField(L.t("total_project_cost"), state.money.getValue("budget"), state.currentRate) { state.money = state.money + ("budget" to it) }
            ProjectMoneyField(L.t("subproject_cost_eib_financing"), state.money.getValue("eib_financing"), state.currentRate) { state.money = state.money + ("eib_financing" to it) }
            ProjectMoneyField(L.t("subproject_cost_local_financing"), state.money.getValue("local_financing"), state.currentRate) { state.money = state.money + ("local_financing" to it) }
            ProjectMoneyField(L.t("technical_supervision_contract_amount"), state.money.getValue("supervision"), state.currentRate) { state.money = state.money + ("supervision" to it) }
            ProjectMoneyField(L.t("engineer_consultant_contract_amount"), state.money.getValue("engineer"), state.currentRate) { state.money = state.money + ("engineer" to it) }
            ProjectMoneyField(L.t("construction_contract_amount"), state.money.getValue("construction"), state.currentRate) { state.money = state.money + ("construction" to it) }
            FormSectionTitle(L.t("parameters_and_location"), Icons.Default.LocationOn)
            field("address", "address", Modifier.fillMaxWidth())()
            FormPair(
                { UkraineRegionAutocomplete(state["region"], { value -> state["region"] = value }, L.t("region"), it, false) },
                { UkraineCityAutocomplete(state["city"], { value -> state["city"] = value }, L.t("city"), it, false) }
            )
            FormPair(
                { modifier -> OutlinedTextField(state["latitude"], { value -> value.coordinateInputOrNull()?.let { state["latitude"] = it } }, label = { Text(L.t("latitude")) }, enabled = !geocoding, modifier = modifier) },
                { modifier -> OutlinedTextField(state["longitude"], { value -> value.coordinateInputOrNull()?.let { state["longitude"] = it } }, label = { Text(L.t("longitude")) }, enabled = !geocoding, modifier = modifier) }
            )
            AddressCoordinatesCalculator(state["address"], state["city"], state["region"], geocoding, { geocoding = it },
                { lat, lon -> state["latitude"] = lat; state["longitude"] = lon })
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormSectionTitle(L.t("designer_information"), Icons.Default.DesignServices)
            field("designerName", "designer_name", Modifier.fillMaxWidth())()
            FormPair(
                { field("designContractNumber", "contract_number", it)() },
                { OmsDateField(state["designContractSigningDate"], { value -> state["designContractSigningDate"] = value }, L.t("contract_date"), it) }
            )
            FormTriplet(
                { OmsDateField(state["designStartDate"], { value -> state["designStartDate"] = value }, L.t("design_start_date"), it) },
                { OmsDateField(state["designPlannedEndDate"], { value -> state["designPlannedEndDate"] = value }, L.t("design_planned_end_date"), it) },
                { modifier -> OutlinedTextField(state.designDurationLabel(), {}, label = { Text(L.t("design_contract_term")) }, readOnly = true, singleLine = true, modifier = modifier) }
            )
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormSectionTitle(L.t("construction_contractor_information"), Icons.Default.Engineering)
            field("contractorName", "contractor_name", Modifier.fillMaxWidth())()
            FormPair(
                { field("constructionContractNumber", "contract_number", it)() },
                { OmsDateField(state["constructionContractSigningDate"], { value -> state["constructionContractSigningDate"] = value }, L.t("contract_date"), it) }
            )
            FormEqualTriplet(
                { OmsDateField(state["constructionStartDate"], { value -> state["constructionStartDate"] = value }, L.t("construction_start_date"), it) },
                { OmsDateField(state["projectedCompletionTime"], { value -> state["projectedCompletionTime"] = value }, L.t("planned_end_date"), it) },
                { OmsDateField(state["endDate"], { value -> state["endDate"] = value }, L.t("actual_end_date"), it) }
            )
        }
    }
    ContractInformationCard(
        title = L.t("technical_supervision_information"), icon = Icons.Default.Visibility,
        name = state["technicalSupervisionName"], onNameChange = { state["technicalSupervisionName"] = it },
        contractNumber = state["technicalSupervisionContractNumber"], onContractNumberChange = { state["technicalSupervisionContractNumber"] = it },
        contractDate = state["technicalSupervisionContractDate"], onContractDateChange = { state["technicalSupervisionContractDate"] = it },
        startDate = state["technicalSupervisionStartDate"], onStartDateChange = { state["technicalSupervisionStartDate"] = it },
        plannedEndDate = state["technicalSupervisionPlannedEndDate"], onPlannedEndDateChange = { state["technicalSupervisionPlannedEndDate"] = it },
        duration = state.durationLabel("technicalSupervisionStartDate", "technicalSupervisionPlannedEndDate")
    )
    ContractInformationCard(
        title = L.t("engineer_consultant_information"), icon = Icons.Default.SupportAgent,
        name = state["engineerConsultantName"], onNameChange = { state["engineerConsultantName"] = it },
        contractNumber = state["engineerConsultantContractNumber"], onContractNumberChange = { state["engineerConsultantContractNumber"] = it },
        contractDate = state["engineerConsultantContractDate"], onContractDateChange = { state["engineerConsultantContractDate"] = it },
        startDate = state["engineerConsultantStartDate"], onStartDateChange = { state["engineerConsultantStartDate"] = it },
        plannedEndDate = state["engineerConsultantPlannedEndDate"], onPlannedEndDateChange = { state["engineerConsultantPlannedEndDate"] = it },
        duration = state.durationLabel("engineerConsultantStartDate", "engineerConsultantPlannedEndDate")
    )
}

@Composable
private fun ContractInformationCard(
    title: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String, onNameChange: (String) -> Unit,
    contractNumber: String, onContractNumberChange: (String) -> Unit,
    contractDate: String, onContractDateChange: (String) -> Unit,
    startDate: String, onStartDateChange: (String) -> Unit,
    plannedEndDate: String, onPlannedEndDateChange: (String) -> Unit,
    duration: String
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormSectionTitle(title, icon)
            OutlinedTextField(name, onNameChange, label = { Text(L.t("organization_name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            FormPair(
                { modifier -> OutlinedTextField(contractNumber, onContractNumberChange, label = { Text(L.t("contract_number")) }, modifier = modifier, singleLine = true) },
                { modifier -> OmsDateField(contractDate, onContractDateChange, L.t("contract_date"), modifier) }
            )
            FormTriplet(
                { modifier -> OmsDateField(startDate, onStartDateChange, L.t("design_start_date"), modifier) },
                { modifier -> OmsDateField(plannedEndDate, onPlannedEndDateChange, L.t("design_planned_end_date"), modifier) },
                { modifier -> OutlinedTextField(duration, {}, label = { Text(L.t("contract_duration")) }, readOnly = true, singleLine = true, modifier = modifier) }
            )
        }
    }
}

@Composable
private fun FormPair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 700.dp) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            first(Modifier.fillMaxWidth()); second(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            first(Modifier.weight(1f)); second(Modifier.weight(1f))
        }
    }
}

@Composable
private fun FormTriplet(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    third: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 900.dp) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
            third(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
            third(Modifier.width(170.dp))
        }
    }
}

@Composable
private fun FormEqualTriplet(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    third: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 900.dp) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
            third(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
            third(Modifier.weight(1f))
        }
    }
}

/**
 * Coordinates are intentionally short.  Rejecting an oversized clipboard
 * payload before evaluating a regular expression prevents the WASM UI from
 * freezing when somebody pastes a whole spreadsheet or web page into a field.
 */
internal fun String.coordinateInputOrNull(): String? =
    takeIf { length <= 24 && matches(Regex("-?[0-9.,]*")) }
