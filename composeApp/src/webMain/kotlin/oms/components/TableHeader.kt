package oms.components

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
    Row(Modifier.width(ProjectTableColumns.totalWidth).padding(vertical = 8.dp)) {
        Spacer(Modifier.width(ProjectTableColumns.selection + ProjectTableColumns.hierarchy))
        ProjectTableColumns.columns.forEach { column ->
            val labelKey = when (column) {
                SortColumn.ID -> "project_code"
                SortColumn.TRANCHE -> "tranche"
                SortColumn.NAME -> "project"
                SortColumn.REGION -> "region"
                SortColumn.CITY -> "city"
                SortColumn.SECTOR -> "sector"
                SortColumn.CONSTRUCTION_TYPE -> "construction_type"
                SortColumn.STATUS -> "status"
                SortColumn.BUDGET -> "budget"
                SortColumn.START_DATE -> "start_date"
                SortColumn.CONTRACTOR -> "contractor"
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
