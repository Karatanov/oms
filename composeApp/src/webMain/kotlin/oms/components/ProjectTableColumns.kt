package oms.components

import androidx.compose.ui.unit.dp
import oms.screens.SortColumn

/** One geometry for filters, headings and data, independent of available row actions. */
object ProjectTableColumns {
    val selection = 32.dp
    val hierarchy = 30.dp
    val actions = 144.dp
    private val widths = linkedMapOf(
        SortColumn.ID to 130.dp,
        SortColumn.TRANCHE to 90.dp,
        SortColumn.NAME to 244.dp,
        SortColumn.REGION to 160.dp,
        SortColumn.CITY to 130.dp,
        SortColumn.SECTOR to 130.dp,
        SortColumn.CONSTRUCTION_TYPE to 180.dp,
        SortColumn.STATUS to 140.dp,
        SortColumn.BUDGET to 120.dp,
        SortColumn.START_DATE to 120.dp,
        SortColumn.CONTRACTOR to 150.dp
    )
    val columns = widths.keys.toList()
    val totalWidth = widths.values.fold(selection + hierarchy + actions) { total, width -> total + width }
    fun width(column: SortColumn) = widths.getValue(column)
}
