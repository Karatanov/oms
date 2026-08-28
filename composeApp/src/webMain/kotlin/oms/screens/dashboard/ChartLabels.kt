package oms.screens.dashboard

import oms.localization.LocalizationManager

/** Keeps calendar labels localized while API grouping keys remain YYYY-MM. */
fun String.toMonthName(): String {
    val number = split('-').getOrNull(1)?.toIntOrNull() ?: return this
    val names = listOf("january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december")
    return names.getOrNull(number - 1)?.let { LocalizationManager.t("month_$it") } ?: this
}
