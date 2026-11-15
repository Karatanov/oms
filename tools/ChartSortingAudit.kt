package oms.charts

import java.text.Collator
import java.util.Locale

/** Reconcile label order with Ukrainian collation and retain numeric/tie invariants. */
fun main() {
    data class Row(val label: String, val value: Double)
    val rows = listOf(Row("Z", 10.0), Row("B", 2.0), Row("A", 2.0), Row("C", 0.0))
    val original = rows.toList()
    fun sort(criterion: ChartSortCriterion, asc: Boolean) =
        sortChartCategories(rows, criterion, asc, Row::label, Row::value)
    check(sort(ChartSortCriterion.Value, true).map { it.label } == listOf("C", "A", "B", "Z"))
    check(sort(ChartSortCriterion.Value, false).map { it.label } == listOf("Z", "A", "B", "C"))
    check(sort(ChartSortCriterion.Label, true).map { it.label } == listOf("A", "B", "C", "Z"))
    check(sort(ChartSortCriterion.Label, false).map { it.label } == listOf("Z", "C", "B", "A"))
    check(rows == original)
    val ukrainian = listOf("Харківська", "Івано-Франківська", "Київська")
    val actual = sortChartCategories(ukrainian, ChartSortCriterion.Label, true, { it }, { 0.0 })
    val collator = Collator.getInstance(Locale.forLanguageTag("uk"))
    val expected = ukrainian.sortedWith { a, b -> collator.compare(a, b) }
    check(actual == expected) { "Ukrainian locale order differs: $actual vs $expected" }
    println("5 numerical/direction/tie/source-immutability checks passed")
    println("Current label order: $actual")
    println("Ukrainian collation order: $expected")
}
