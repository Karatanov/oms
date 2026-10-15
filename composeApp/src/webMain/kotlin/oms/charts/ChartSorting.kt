package oms.charts

import kotlin.js.JsFun
import oms.localization.Language
import oms.localization.LocalizationManager

/** The two supported ways of ordering independent chart categories. */
enum class ChartSortCriterion { Label, Value }

/**
 * Sorts only a chart's rendered rows. The source collection is never mutated.
 * Browser `Intl.Collator` keeps Ukrainian and English category ordering natural
 * for the active OMS language instead of comparing formatted byte strings.
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

@JsFun("(left, right, locale) => new Intl.Collator(locale, { sensitivity: 'base', numeric: true }).compare(left, right)")
private external fun browserLocaleCompare(left: String, right: String, locale: String): Int

private fun compareChartLabels(left: String, right: String): Int =
    browserLocaleCompare(left, right, if (LocalizationManager.currentLanguage == Language.EN) "en" else "uk")
