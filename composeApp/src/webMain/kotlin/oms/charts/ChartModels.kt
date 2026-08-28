package oms.charts

/*
   Дані для bar chart
*/

data class BarData(
    val label: String,
    val value: Float,
    val groupLabel: String? = null,
    val color: androidx.compose.ui.graphics.Color? = null,
    val tooltip: String? = null,
    val formattedValue: String? = null
)
