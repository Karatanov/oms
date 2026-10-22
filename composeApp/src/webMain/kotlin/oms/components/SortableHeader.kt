package oms.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import oms.theme.OmsDimensions
import oms.screens.SortColumn

@Composable
fun SortableHeader(title: String, column: SortColumn, currentSort: SortColumn, ascending: Boolean,
    onSort: (SortColumn) -> Unit, modifier: Modifier,
    height: Dp = OmsDimensions.TableHeaderHeight, maxLines: Int = 2) {
    SortableTableHeader(title, currentSort == column, ascending, { onSort(column) }, modifier, height = height, maxLines = maxLines)
}
