package oms.umitaf

import kotlin.test.*
import oms.components.ProjectMoneyDraft
import oms.components.isValidIsoDate
import oms.localization.Language
import oms.localization.LocalizationManager
import oms.screens.ProjectFormState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProjectFormValidationTest {
    private fun valid() = ProjectFormState(today = "2026-09-24").apply {
        this["name"] = "New subproject"
        this["code"] = "TEST01"
        money = money + ("budget" to ProjectMoneyDraft("100.00", "EUR", "4500.00", "45", "2026-09-24"))
    }
    @Test fun validPayloadRetainsManualCoordinatesAndVisibleDates() {
        val state = valid()
        state["latitude"] = "50,0058253"; state["longitude"] = "36,2367038"
        state["constructionContractSigningDate"] = "2025-01-01"
        state["constructionStartDate"] = "2025-01-02"
        state["projectedCompletionTime"] = "2025-12-31"
        assertNull(state.validate(false))
        val payload = state.createRequest()
        assertEquals("2025-01-01", payload.contractSignedDate)
        assertEquals("2025-12-31", payload.plannedEndDate)
        assertEquals(50.0058253, payload.latitude)
        assertEquals(4500L, payload.budgetPlanned)
        assertTrue(Json.encodeToString(payload).contains("TEST01"))
    }
    @Test fun allInvalidFieldsAreReportedTogether() {
        val state = valid()
        state["name"] = ""; state["code"] = ""; state["latitude"] = "91"
        state["longitude"] = "181"; state["constructionContractSigningDate"] = "2026-02-31"
        state.projectType = "subproject"
        state.money = state.money + ("budget" to ProjectMoneyDraft("100"))
        assertNotNull(state.validate(false))
        assertTrue(state.errors().keys.containsAll(listOf("name", "code", "latitude", "longitude", "parent", "budget", "constructionContractSigningDate")))
    }
    @Test fun visibleContractDateOrderIsValidated() {
        val state = valid()
        state["constructionContractSigningDate"] = "2027-01-01"
        assertNotNull(state.validate(false))
        assertTrue("projectedCompletionTime" in state.errors())
    }
    @Test fun realCalendarDatesAndTranslations() {
        assertTrue(isValidIsoDate("2024-02-29"))
        listOf("2026-02-29", "2026-04-31", "31.02.2026", "2026-13-01", "2026-0").forEach { assertFalse(isValidIsoDate(it)) }
        val previous = LocalizationManager.currentLanguage
        try {
            for (language in listOf(Language.UK, Language.EN)) {
                LocalizationManager.currentLanguage = language
                for (key in listOf("field_required", "date_invalid", "geocode_unavailable", "project_validation_summary", "project_save_failed"))
                    assertNotEquals(key, LocalizationManager.t(key))
            }
        } finally { LocalizationManager.currentLanguage = previous }
    }
}
