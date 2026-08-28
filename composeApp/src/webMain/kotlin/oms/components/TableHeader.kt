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
fun TableHeader(
    currentSort: SortColumn,
    ascending: Boolean,
    onSort: (SortColumn) -> Unit
) {
    Row(
        modifier = Modifier
            .width(1_800.dp)
            .padding(vertical = 8.dp)
    ) {
        Box(modifier = Modifier.width(32.dp))
        Box(modifier = Modifier.width(30.dp))
        SortableHeader(
            LocalizationManager.t("project_code"),
            SortColumn.ID,
            currentSort,
            ascending,
            onSort,
            Modifier.width(130.dp)
        )

        SortableHeader(LocalizationManager.t("tranche"), SortColumn.TRANCHE, currentSort, ascending, onSort, Modifier.width(90.dp))

        SortableHeader(
            LocalizationManager.t("project"),
            SortColumn.NAME,
            currentSort,
            ascending,
            onSort,
            Modifier.weight(1f)
        )

        SortableHeader(
            LocalizationManager.t("region"),
            SortColumn.REGION,
            currentSort,
            ascending,
            onSort,
            Modifier.width(160.dp)
        )

        SortableHeader(LocalizationManager.t("city"), SortColumn.CITY, currentSort, ascending, onSort, Modifier.width(130.dp))
        SortableHeader(LocalizationManager.t("sector"), SortColumn.SECTOR, currentSort, ascending, onSort, Modifier.width(130.dp))
        SortableHeader(LocalizationManager.t("construction_type"), SortColumn.CONSTRUCTION_TYPE, currentSort, ascending, onSort, Modifier.width(180.dp))

        SortableHeader(
            LocalizationManager.t("status"),
            SortColumn.STATUS,
            currentSort,
            ascending,
            onSort,
            Modifier.width(140.dp)
        )

        SortableHeader(LocalizationManager.t("budget"), SortColumn.BUDGET, currentSort, ascending, onSort, Modifier.width(120.dp))
        SortableHeader(LocalizationManager.t("start_date"), SortColumn.START_DATE, currentSort, ascending, onSort, Modifier.width(120.dp))
        SortableHeader(LocalizationManager.t("contractor"), SortColumn.CONTRACTOR, currentSort, ascending, onSort, Modifier.width(150.dp))

        Box(
            modifier = Modifier
                .width(144.dp)
                .height(oms.theme.OmsDimensions.TableHeaderHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(text = LocalizationManager.t("actions"), style = MaterialTheme.typography.labelLarge)
        }
    }
}
