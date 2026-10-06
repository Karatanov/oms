package oms.umitaf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import oms.components.formatEuroCents
import oms.localization.Language
import oms.localization.LocalizationManager
import oms.localization.Strings
import oms.screens.dashboard.toMonthName
import oms.theme.Primary
import androidx.compose.ui.graphics.Color
import oms.navigation.Screen
import oms.navigation.isGuestAccessible

class ComposeAppWebTest {

    @Test
    fun corporatePrimaryRemainsUnchanged() {
        assertEquals(Color(0xFF278DAD), Primary)
    }

    @Test
    fun financialLabelsPreserveCentsAndLargeAmounts() {
        assertEquals("€ 0.01", formatEuroCents(1))
        assertEquals("€ 0.00", formatEuroCents(0))
        assertEquals("€ 25 000 000.99", formatEuroCents(2_500_000_099))
        assertEquals("−€ 12.34", formatEuroCents(-1234))
    }

    @Test
    fun monthNamesFollowSelectedLanguage() {
        val previous = LocalizationManager.currentLanguage
        try {
            LocalizationManager.currentLanguage = Language.UK
            assertEquals("Серпень", "2026-08".toMonthName())
            LocalizationManager.currentLanguage = Language.EN
            assertEquals("August", "2026-08".toMonthName())
            assertEquals("invalid", "invalid".toMonthName())
        } finally { LocalizationManager.currentLanguage = previous }
    }

    @Test
    fun redesignedControlsHaveBothTranslations() {
        listOf("retry", "no_options", "dashboard", "expand_navigation", "collapse_navigation",
            "reset_filters", "confirm_delete_title", "confirm_delete_message", "table_scroll",
            "admin_search", "reports_search", "documents_search", "portfolio_overview").forEach {
            assertTrue(!Strings.uk[it].isNullOrBlank(), "UK: $it")
            assertTrue(!Strings.en[it].isNullOrBlank(), "EN: $it")
        }
    }

    @Test
    fun guestCanOpenProgrammeInformationAndMapOnly() {
        assertTrue(Screen.GuestInfo.isGuestAccessible())
        assertTrue(Screen.Map.isGuestAccessible())
        assertTrue(!Screen.Dashboard.isGuestAccessible())
        assertTrue(!Screen.Projects.isGuestAccessible())
        assertTrue(!Screen.ProjectDetail.isGuestAccessible())
        assertTrue(!Screen.Admin.isGuestAccessible())
    }
}
