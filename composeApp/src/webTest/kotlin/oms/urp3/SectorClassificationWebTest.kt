package oms.urp3

import kotlin.test.Test
import kotlin.test.assertEquals
import oms.components.sectorLabel
import oms.localization.Language
import oms.localization.LocalizationManager

class SectorClassificationWebTest {
    @Test fun combinedSourceCaptionDisplaysOnlyTheActiveLanguage() {
        val previous = LocalizationManager.currentLanguage
        try {
            LocalizationManager.currentLanguage = Language.UK
            assertEquals("Водопостачання та водовідведення", "Water supply & Sewerage".sectorLabel())
            LocalizationManager.currentLanguage = Language.EN
            assertEquals("Water supply and sewerage", "Водопостачання та водовідведення".sectorLabel())
        } finally { LocalizationManager.currentLanguage = previous }
    }
    @Test fun detailedSewerageDoesNotBecomeWaterSupplyOrCombinedSector() {
        val previous = LocalizationManager.currentLanguage
        try {
            LocalizationManager.currentLanguage = Language.UK
            assertEquals("Водовідведення", "Водовідведення / Sewerage".sectorLabel())
            LocalizationManager.currentLanguage = Language.EN
            assertEquals("Sewerage", "Водовідведення / Sewerage".sectorLabel())
        } finally { LocalizationManager.currentLanguage = previous }
    }
}
