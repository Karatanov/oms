package oms.urp3

import oms.charts.*
import oms.localization.Language
import oms.localization.LocalizationManager
import kotlin.test.*

class ChartSortingLocaleTest {
    @Test fun sortingUsesDisplayedTranslationsNotSourceKeys() {
        val source = listOf("alpha", "zulu")
        val translations = mapOf("alpha" to "Харківська", "zulu" to "Івано-Франківська")
        assertEquals(listOf("zulu", "alpha"), sortChartCategories(source, ChartSortCriterion.Label, true,
            { translations.getValue(it) }, { 0.0 }, "uk"))
        assertEquals(listOf("alpha", "zulu"), source)
    }
    private data class Row(val label: String, val value: Double)
    private fun labels(values: List<String>, ascending: Boolean = true, locale: String = "uk") =
        sortChartCategories(values, ChartSortCriterion.Label, ascending, { it }, { 0.0 }, locale)

    @Test fun ukrainianLettersFollowTheAlphabetRatherThanUnicodeCodePoints() {
        val source = listOf("Харківська", "Івано-Франківська", "Київська")
        assertEquals(listOf("Івано-Франківська", "Київська", "Харківська"), labels(source))
        assertEquals(listOf("Харківська", "Київська", "Івано-Франківська"), labels(source, false))
        assertEquals(listOf("Е", "Є", "Ж", "И", "І", "Ї", "Й"), labels(listOf("Ї", "Є", "Й", "И", "Е", "І", "Ж")))
        assertEquals(listOf("Г", "Ґ", "Д"), labels(listOf("Д", "Ґ", "Г")))
    }
    @Test fun englishAccentsAndCaseAreComparedAsDisplayedLabels() {
        assertEquals(listOf("alpha", "Beta", "éducation", "Energy", "Zoo"),
            labels(listOf("Zoo", "Energy", "éducation", "Beta", "alpha"), locale = "en"))
        assertEquals(listOf("Alpha", "alpha"), labels(listOf("Alpha", "alpha"), locale = "en"))
    }
    @Test fun numericDirectionsAndAlphabeticalTiesRemainUnchangedWithoutMutation() {
        val source = listOf(Row("Z", 10.0), Row("B", 2.0), Row("A", 2.0), Row("C", 0.0))
        val original = source.toList()
        fun sorted(ascending: Boolean) = sortChartCategories(source, ChartSortCriterion.Value, ascending, Row::label, Row::value, "en").map { it.label }
        assertEquals(listOf("C", "A", "B", "Z"), sorted(true))
        assertEquals(listOf("Z", "A", "B", "C"), sorted(false))
        assertEquals(original, source)
    }
    @Test fun localeSwitchDoesNotReuseThePreviousLanguagesCollator() {
        val previous = LocalizationManager.currentLanguage
        try {
            val source = listOf("Kyiv", "Київ")
            LocalizationManager.currentLanguage = Language.UK
            assertEquals(listOf("Київ", "Kyiv"), sortChartCategories(source, ChartSortCriterion.Label, true, { it }, { 0.0 }))
            LocalizationManager.currentLanguage = Language.EN
            assertEquals(listOf("Kyiv", "Київ"), sortChartCategories(source, ChartSortCriterion.Label, true, { it }, { 0.0 }))
        } finally { LocalizationManager.currentLanguage = previous }
    }
    @Test fun barDataUsesTheSameComparatorAndKeepsItsPayload() {
        val source = listOf(BarData("Київська", 2f, id = "kyiv"), BarData("Івано-Франківська", 1f, id = "if"))
        val previous = LocalizationManager.currentLanguage
        try {
            LocalizationManager.currentLanguage = Language.UK
            val sorted = sortBarData(source, ChartSortCriterion.Label, true)
            assertEquals(listOf("if", "kyiv"), sorted.map { it.id })
            assertSame(source[1], sorted[0])
            assertEquals(listOf("kyiv", "if"), source.map { it.id })
        } finally { LocalizationManager.currentLanguage = previous }
    }
}
