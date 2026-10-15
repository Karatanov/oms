package oms.charts

/** The two supported ways of ordering independent chart categories. */
enum class ChartSortCriterion { Label, Value }

/**
 * Sorts only a chart's rendered rows. The source collection is never mutated.
 * Labels are compared case-insensitively against their displayed values.
 */
internal fun <T> sortChartCategories(
    items: List<T>,
    criterion: ChartSortCriterion,
    ascending: Boolean,
    labelOf: (T) -> String,
    valueOf: (T) -> Double
): List<T> = items.sortedWith { left, right ->
    val labelComparison = compareChartLabels(labelOf(left), labelOf(right))
    val primary = when (criterion) {
        ChartSortCriterion.Label -> labelComparison
        ChartSortCriterion.Value -> valueOf(left).compareTo(valueOf(right))
    }
    // Numeric ties are always made deterministic by the displayed category
    // label. Direction reverses the requested primary criterion only.
    when {
        primary != 0 -> if (ascending) primary else -primary
        else -> labelComparison
    }
}

internal fun sortBarData(
    data: List<BarData>,
    criterion: ChartSortCriterion,
    ascending: Boolean
): List<BarData> = sortChartCategories(data, criterion, ascending, BarData::label) { it.value.toDouble() }

private fun compareChartLabels(left: String, right: String): Int =
    left.lowercase().compareTo(right.lowercase())
