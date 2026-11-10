package oms.urp3

import kotlin.test.Test
import kotlin.test.assertEquals
import oms.components.sectorLabel
import oms.localization.Language
import oms.localization.LocalizationManager

class SectorLocalizationWebTest {
    @Test
    fun bilingualAndDescriptiveSectorValuesUseTheActiveLanguage() {
        withLanguage(Language.UK) {
            assertEquals("Водовідведення", "Водовідведення / Sewerage".sectorLabel())
            assertEquals("Водопостачання", "Water supply / Водопостачання".sectorLabel())
            assertEquals("Теплопостачання", "Heating / Теплопостачання".sectorLabel())
            assertEquals("Укриття", "Civil protection shelters".sectorLabel())
        }
        withLanguage(Language.EN) {
            assertEquals("Sewerage", "Водовідведення / Sewerage".sectorLabel())
            assertEquals("Water supply", "Water supply / Водопостачання".sectorLabel())
            assertEquals("Heat supply", "Heating / Теплопостачання".sectorLabel())
            assertEquals("Shelter", "Civil protection shelters".sectorLabel())
        }
    }

    private fun withLanguage(language: Language, block: () -> Unit) {
        val previous = LocalizationManager.currentLanguage
        try {
            LocalizationManager.currentLanguage = language
            block()
        } finally {
            LocalizationManager.currentLanguage = previous
        }
    }
}
