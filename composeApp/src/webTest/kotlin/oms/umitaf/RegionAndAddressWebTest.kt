package oms.umitaf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import oms.components.localizedUkraineRegion
import oms.data.ApiFinancialSummary
import oms.data.ApiProjectDetails
import oms.data.ApiProjectDetailsData
import oms.localization.Language
import oms.localization.LocalizationManager
import oms.screens.localizedProjectAddress
import oms.screens.isEarthTemperatureInput
import oms.screens.temperatureInput
import oms.screens.toRegionChartLabel

/** Run on JS as well as Wasm: JVM/Wasm regexes accept inline flags that JS rejects. */
class RegionAndAddressWebTest {
    @Test
    fun fundingChartLabelsStripRegionSuffixesCaseInsensitively() {
        assertEquals("Київська", "Київська область".toRegionChartLabel())
        assertEquals("Київська", "Київська ОБЛАСТЬ".toRegionChartLabel())
        assertEquals("Kyiv", "Kyiv Oblast".toRegionChartLabel())
        assertEquals("Kyiv", "Kyiv REGION".toRegionChartLabel())
        assertEquals("Kyiv City", "Kyiv City".toRegionChartLabel())
        assertEquals("", "".toRegionChartLabel())
    }

    @Test
    fun fundingChartAcceptsLocalizedAndUnknownRegionsInBothLanguages() {
        withLanguage(Language.UK) {
            assertEquals("Київська", localizedUkraineRegion("Kyiv Region").toRegionChartLabel())
        }
        withLanguage(Language.EN) {
            assertEquals("Kyiv", localizedUkraineRegion("Київська область").toRegionChartLabel())
            assertEquals("Custom Region", localizedUkraineRegion("Custom OBLAST"))
            assertEquals("Custom", localizedUkraineRegion("Custom OBLAST").toRegionChartLabel())
            assertEquals("Oblastville", localizedUkraineRegion("Oblastville"))
        }
    }

    @Test
    fun projectAddressExtractionIgnoresEnglishPrefixCase() {
        withLanguage(Language.EN) {
            assertEquals("12 Main Street", localizedProjectAddress(projectDetails("School AT THE ADDRESS: \"12 Main Street\"")))
            assertEquals("12 Main Street", localizedProjectAddress(projectDetails("School Address, 12 Main Street")))
            assertEquals("Kyiv Region, Ukraine", localizedProjectAddress(projectDetails("School renovation")))
        }
        withLanguage(Language.UK) {
            assertEquals("вул. Головна, 12", localizedProjectAddress(projectDetails("School ADDRESS: 12 Main Street")))
        }
    }

    @Test
    fun inspectionTemperatureAcceptsOnlyPracticalEarthRange() {
        assertEquals("-12.5", "-12,5 °C".temperatureInput())
        assertEquals("+60", "+60".temperatureInput())
        assertEquals("", "letters".temperatureInput())
        assertEquals("60", "60".temperatureInput())

        assertTrue("-90".isEarthTemperatureInput())
        assertTrue("+60".isEarthTemperatureInput())
        assertFalse("-91".isEarthTemperatureInput())
        assertFalse("61".isEarthTemperatureInput())
    }

    private fun projectDetails(englishName: String) = ApiProjectDetails(
        data = ApiProjectDetailsData(
            name = "Школа",
            nameEn = englishName,
            siteName = "Школа",
            siteNumber = "1",
            address = "вул. Головна, 12",
            region = "Київська область",
            city = "Київ",
            latitude = 0.0,
            longitude = 0.0,
            sector = "education",
            constructionType = "renovation",
            budgetPlanned = 0L
        ),
        financialSummary = ApiFinancialSummary(budgetPlanned = 0.0, amountSpent = 0.0, budgetRemaining = 0.0)
    )

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
