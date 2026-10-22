package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager
import oms.screens.SortColumn

@Composable
fun TableHeader(currentSort: SortColumn, ascending: Boolean, onSort: (SortColumn) -> Unit) {
    Row(Modifier.width(ProjectTableColumns.totalWidth).padding(vertical = 4.dp)) {
        // Selection and hierarchy controls precede the stable code column.
        Spacer(Modifier.width(ProjectTableColumns.selection + ProjectTableColumns.hierarchy))
        ProjectTableColumns.columns.forEach { column ->
            val labelKey = when (column) {
                SortColumn.ID -> "project_code"
                SortColumn.POOL -> "pool_number"
                SortColumn.DREAM -> "dream_project_link"
                SortColumn.NAME -> "subproject_name"
                SortColumn.REGION -> "region"
                SortColumn.COMMUNITY -> "community"
                SortColumn.PRIORITY -> "priority_area"
                SortColumn.CONSTRUCTION_TYPE -> "construction_type"
                SortColumn.IMPLEMENTER -> "project_implementer"
                SortColumn.BENEFICIARY -> "beneficiary"
                SortColumn.FUNDING_TOTAL -> "funding_total_uah"
                SortColumn.FUNDING_WORKS -> "funding_works_uah"
                SortColumn.FUNDING_TS -> "funding_ts_uah"
                SortColumn.FUNDING_CSC -> "funding_csc_uah"
                SortColumn.EIB_COST -> "eib_financing_uah"
                SortColumn.LOCAL_COST -> "local_financing_uah"
                SortColumn.CONTRACTED_TOTAL -> "contracted_total_uah"
                SortColumn.CONTRACTED_EIB -> "contracted_eib_uah"
                SortColumn.CONTRACTED_LOCAL -> "contracted_local_uah"
                SortColumn.APPROVED_FUNDING_PCT -> "approved_funding_pct"
                SortColumn.CONTRACTOR -> "contractor"
                SortColumn.CONTRACT_DATE -> "contract_date"
                SortColumn.CONTRACT_END_DATE -> "contract_end_date"
                SortColumn.CONTRACT_DURATION -> "contract_duration_months"
                SortColumn.CONSTRUCTION_AMOUNT -> "construction_contract_uah"
                SortColumn.CONSTRUCTION_EIB -> "construction_eib_uah"
                SortColumn.CONSTRUCTION_LOCAL -> "construction_local_uah"
                SortColumn.PAID_CONSTRUCTION -> "paid_construction_uah"
                SortColumn.DED -> "ded_documentation"
                SortColumn.ESTIMATE -> "estimate_documentation"
                SortColumn.EXPERTISE -> "project_expertise"
                SortColumn.PERMIT -> "construction_permit"
                SortColumn.WORK_STATUS -> "construction_work_status"
                SortColumn.ACT_NUMBER -> "act_number"
                SortColumn.ACT_DATE -> "act_date"
                SortColumn.COMPLETED_WORK_PCT -> "completed_work_pct"
                SortColumn.FINANCING_USED_PCT -> "financing_used_pct"
                SortColumn.PAID_FINANCING_PCT -> "paid_financing_pct"
                SortColumn.WORK_SUMMARY -> "construction_work_summary"
                SortColumn.WORK_COMMENTS -> "construction_work_comments"
                // Legacy enum entries are excluded from ProjectTableColumns.columns.
                SortColumn.TRANCHE, SortColumn.CITY, SortColumn.SECTOR, SortColumn.STATUS,
                SortColumn.BUDGET, SortColumn.START_DATE, SortColumn.PLANNED_END_DATE,
                SortColumn.IMPLEMENTATION_DURATION -> ""
            }
            SortableHeader(LocalizationManager.t(labelKey), column, currentSort, ascending, onSort,
                Modifier.width(ProjectTableColumns.width(column)))
        }
        Box(Modifier.width(ProjectTableColumns.actions).height(oms.theme.OmsDimensions.TableHeaderHeight),
            contentAlignment = Alignment.Center) {
            Text(LocalizationManager.t("actions"), style = MaterialTheme.typography.labelLarge)
        }
    }
}
