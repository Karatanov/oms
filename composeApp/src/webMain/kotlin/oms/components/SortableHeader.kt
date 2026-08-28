package oms.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import oms.screens.SortColumn

@Composable
fun SortableHeader(title: String, column: SortColumn, currentSort: SortColumn, ascending: Boolean,
    onSort: (SortColumn) -> Unit, modifier: Modifier) {
    SortableTableHeader(title, currentSort == column, ascending, { onSort(column) }, modifier)
}
