package oms.components

import androidx.compose.ui.unit.dp
import oms.screens.SortColumn

/** One geometry for filters, headings and data, independent of available row actions. */
object ProjectTableColumns {
    val selection = 32.dp
    val hierarchy = 30.dp
    val actions = 144.dp
    /** Registry headings have deliberately long source-data labels. */
    val headerHeight = 68.dp
    private val widths = linkedMapOf(
        SortColumn.ID to 130.dp,
        SortColumn.POOL to 72.dp,
        SortColumn.REGION to 150.dp,
        SortColumn.DREAM to 86.dp,
        SortColumn.NAME to 280.dp,
        SortColumn.COMMUNITY to 180.dp,
        SortColumn.PRIORITY to 160.dp,
        SortColumn.CONSTRUCTION_TYPE to 170.dp,
        SortColumn.IMPLEMENTER to 210.dp,
        SortColumn.BENEFICIARY to 220.dp,
        SortColumn.FUNDING_TOTAL to 150.dp,
        SortColumn.FUNDING_WORKS to 150.dp,
        SortColumn.FUNDING_TS to 150.dp,
        SortColumn.FUNDING_CSC to 150.dp,
        SortColumn.EIB_COST to 150.dp,
        SortColumn.LOCAL_COST to 160.dp,
        SortColumn.CONTRACTED_TOTAL to 160.dp,
        SortColumn.CONTRACTED_EIB to 150.dp,
        SortColumn.CONTRACTED_LOCAL to 150.dp,
        SortColumn.APPROVED_FUNDING_PCT to 120.dp,
        SortColumn.CONTRACTOR to 220.dp,
        SortColumn.CONTRACT_DATE to 120.dp,
        SortColumn.CONTRACT_END_DATE to 145.dp,
        SortColumn.CONTRACT_DURATION to 125.dp,
        SortColumn.CONSTRUCTION_AMOUNT to 170.dp,
        SortColumn.CONSTRUCTION_EIB to 170.dp,
        SortColumn.CONSTRUCTION_LOCAL to 170.dp,
        SortColumn.PAID_CONSTRUCTION to 170.dp,
        SortColumn.DED to 130.dp,
        SortColumn.ESTIMATE to 145.dp,
        SortColumn.EXPERTISE to 145.dp,
        SortColumn.PERMIT to 155.dp,
        SortColumn.WORK_STATUS to 190.dp,
        SortColumn.ACT_NUMBER to 140.dp,
        SortColumn.ACT_DATE to 120.dp,
        SortColumn.COMPLETED_WORK_PCT to 140.dp,
        SortColumn.FINANCING_USED_PCT to 150.dp,
        SortColumn.PAID_FINANCING_PCT to 160.dp,
        SortColumn.WORK_SUMMARY to 260.dp,
        SortColumn.WORK_COMMENTS to 260.dp,
        /* Retained only for source datasets created before the registry. */
        SortColumn.TRANCHE to 1.dp,
        SortColumn.CITY to 1.dp,
        SortColumn.SECTOR to 1.dp,
        SortColumn.STATUS to 1.dp,
        SortColumn.BUDGET to 1.dp,
        SortColumn.START_DATE to 1.dp,
        SortColumn.PLANNED_END_DATE to 1.dp,
        SortColumn.IMPLEMENTATION_DURATION to 1.dp
    )
    val columns = widths.filterValues { it > 1.dp }.keys.toList()
    /** Selection/tree controls followed by the stable subproject code column. */
    val frozenStartWidth = selection + hierarchy + widths.getValue(SortColumn.ID)
    val totalWidth = columns.fold(selection + hierarchy + actions) { total, column -> total + widths.getValue(column) }
    fun width(column: SortColumn) = widths.getValue(column)
}
