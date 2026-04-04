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
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        SortableHeader(
            LocalizationManager.t("id"),
            SortColumn.ID,
            currentSort,
            ascending,
            onSort,
            Modifier.width(80.dp)
        )

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

        SortableHeader(
            LocalizationManager.t("status"),
            SortColumn.STATUS,
            currentSort,
            ascending,
            onSort,
            Modifier.width(140.dp)
        )

        Box(
            modifier = Modifier
                .width(100.dp)
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(24.dp))
                Text(
                    text = LocalizationManager.t("action"),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}