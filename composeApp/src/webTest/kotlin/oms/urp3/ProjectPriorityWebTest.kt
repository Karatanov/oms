package oms.urp3

import kotlin.test.Test
import kotlin.test.assertEquals
import oms.components.priorityDirectionLabel
import oms.localization.Language
import oms.localization.LocalizationManager
import oms.model.Project
import oms.model.ProjectStatus
import oms.model.matchesPriorityDirection
import oms.model.priorityDirectionKey

class ProjectPriorityWebTest {
    private fun project(code: String, source: String, sector: String = "education") = Project(
        id = code, projectType = "subproject", name = code, siteNumber = code,
        region = "", city = "", sector = sector, budgetPlanned = 0,
        registry = mapOf("priority" to source), status = ProjectStatus.ACTIVE, latitude = 0.0, longitude = 0.0)

    @Test fun filterUsesDisplayedPriorityRatherThanAnOutdatedSectorField() {
        val rows = listOf(project("a", "Sewerage"), project("b", "Водовідведення / Sewerage"), project("c", "Health"))
        assertEquals(listOf("a", "b"), rows.filter { it.matchesPriorityDirection("sewerage") }.map { it.id })
        assertEquals(listOf("a", "b", "c"), rows.filter { it.matchesPriorityDirection(null) }.map { it.id })
        assertEquals("heat_supply", project("d", "", "Heating").priorityDirectionKey())
    }

    @Test fun englishOnlySourceValuesAreTranslatedInBothLanguages() {
        val previous = LocalizationManager.currentLanguage
        try {
            val cases = listOf(
                Triple("Education", "Освіта", "Education"),
                Triple("Housing", "Житло", "Housing"),
                Triple("Health", "Охорона здоров’я", "Healthcare"),
                Triple("Heating", "Теплопостачання", "Heat supply"),
                Triple("Security Centres", "Центри безпеки", "Security Centres"),
                Triple("Water supply", "Водопостачання", "Water supply"),
                Triple("Sewerage", "Водовідведення", "Sewerage")
            )
            LocalizationManager.currentLanguage = Language.UK
            cases.forEach { assertEquals(it.second, it.first.priorityDirectionLabel()) }
            LocalizationManager.currentLanguage = Language.EN
            cases.forEach { assertEquals(it.third, it.first.priorityDirectionLabel()) }
        } finally { LocalizationManager.currentLanguage = previous }
    }

    @Test fun shelterDirectionLabelsAndFilterKeysStayDistinct() {
        val previous = LocalizationManager.currentLanguage
        try {
            val education = "Shelters: Education and health buildings"
            val other = "Укриття: Інші громадські будівлі / Shelters: Other public buildings"
            LocalizationManager.currentLanguage = Language.UK
            assertEquals("Укриття: заклади освіти та охорони здоров’я", education.priorityDirectionLabel())
            assertEquals("Укриття: інші громадські будівлі", other.priorityDirectionLabel())
            LocalizationManager.currentLanguage = Language.EN
            assertEquals(education, education.priorityDirectionLabel())
            assertEquals("Shelters: Other public buildings", other.priorityDirectionLabel())
            assertEquals(2, listOf(project("a", education), project("b", other)).map { it.priorityDirectionKey() }.distinct().size)
        } finally { LocalizationManager.currentLanguage = previous }
    }
}
