@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package oms.charts

import kotlin.js.JsAny
import kotlin.js.js

private external interface ChartLabelCollator : JsAny {
    fun compare(left: String, right: String): Int
}

private fun createChartLabelCollator(locale: String): ChartLabelCollator =
    js("new Intl.Collator(locale, {usage: 'sort', sensitivity: 'accent', numeric: false})")

// Reuse the two native collators rather than constructing one for every comparison.
private val chartLabelCollators = mutableMapOf<String, ChartLabelCollator>()

internal fun compareLocalizedChartLabels(left: String, right: String, locale: String): Int =
    chartLabelCollators.getOrPut(locale) { createChartLabelCollator(locale) }.compare(left, right)
