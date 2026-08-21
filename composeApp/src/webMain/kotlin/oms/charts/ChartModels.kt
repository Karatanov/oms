package oms.charts

/*
   Дані для bar chart
*/

data class BarData(
    val label: String,
    val value: Float,
    val groupLabel: String? = null
)
