package oms.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import oms.data.ApiProcurementRecord
import oms.data.OmsApiClient
import oms.localization.LocalizationManager

@Composable
fun ProcurementScreen() {
    var records by remember { mutableStateOf<List<ApiProcurementRecord>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { OmsApiClient.procurements() }
            .onSuccess { records = it.sortedWith(compareBy({ record -> record.batchId }, { record -> record.recordNumber })) }
            .onFailure { error = LocalizationManager.t("procurement_load_error") }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(LocalizationManager.t("procurement_title"), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(LocalizationManager.t("procurement_subtitle"), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            records == null -> CircularProgressIndicator()
            else -> ProcurementTable(records!!)
        }
    }
}

@Composable
private fun ProcurementTable(records: List<ApiProcurementRecord>) {
    Column(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
        ProcurementRow(procurementHeaderLabels(), isHeader = true)
        HorizontalDivider()
        records.forEach { record ->
            ProcurementRow(listOf(
                record.recordNumber.toString(), record.batchId.toString(), record.oblastName, record.oblastId,
                record.subProjectId, record.subProjectLotId, record.purchaseStatus, record.tenderId.orEmpty(),
                record.prozorroTenderId.orEmpty(), record.contractorNameUkr.orEmpty(), record.contractorNameEng.orEmpty(),
                record.contractorId.orEmpty(), record.contractDate.orEmpty(), record.contractEndDate.orEmpty(),
                record.contractDurationMonths?.toString().orEmpty(), record.contractAmountUah.format(0),
                record.contractAmountEur.format(2), record.financingContractDifferencePct?.let { "${(it * 100).format(2)}%" }.orEmpty()
            ))
            HorizontalDivider()
        }
    }
}

@Composable
private fun ProcurementRow(values: List<String>, isHeader: Boolean = false) {
    Row(Modifier.widthIn(min = 3_300.dp).padding(vertical = 10.dp)) {
        values.zip(columnWidths).forEach { (value, width) ->
            Text(value, Modifier.width(width.dp).padding(horizontal = 6.dp), style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

private fun procurementHeaderLabels() = listOf(
    "proc_number", "proc_batch_number", "proc_oblast_name", "proc_oblast_id", "proc_subproject_id",
    "proc_subproject_lot_id", "procurement_status", "proc_tender_id", "proc_prozorro_tender_id",
    "proc_contractor_name_uk", "proc_contractor_name_en", "proc_contractor_edrpou", "proc_contract_date",
    "proc_contract_end_date", "proc_contract_duration", "proc_contract_amount_uah", "proc_contract_amount_eur",
    "proc_financing_difference"
).map(LocalizationManager::t)
private val columnWidths = listOf(55, 85, 180, 100, 145, 160, 220, 185, 250, 260, 260, 160, 120, 160, 160, 190, 180, 180)
private fun Double?.format(decimals: Int): String = this?.let { value ->
    val multiplier = if (decimals == 0) 1.0 else 100.0
    val rounded = kotlin.math.round(value * multiplier) / multiplier
    if (decimals == 0) rounded.toInt().toString() else rounded.toString()
}.orEmpty()
