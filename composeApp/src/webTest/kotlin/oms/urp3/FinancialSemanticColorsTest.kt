package oms.urp3

import kotlin.test.*
import oms.theme.*
import oms.components.constructionTypeColor
import oms.localization.Strings

class FinancialSemanticColorsTest {
    @Test fun contractTypesAndPaymentPurposesShareSemanticColors() {
        assertEquals(paymentPurposeColor("TS"), paymentPurposeColor("technical_supervision"))
        assertEquals(paymentPurposeColor("W"), paymentPurposeColor("works"))
        assertEquals(paymentPurposeColor("CSC"), paymentPurposeColor("engineer_consultant"))
        assertEquals(3, listOf("W", "TS", "CSC").map(::paymentPurposeColor).distinct().size)
    }
    @Test fun translationAndCategoryOrderDoNotAffectColors() {
        assertEquals(procurementStatusColor("Contract signed"), procurementStatusColor("Договір укладено / Contract signed"))
        assertEquals(constructionTypeColor("reconstruction"), constructionTypeColor("Реконструкція / Reconstruction"))
        assertEquals(3, listOf(InvestmentColors.Total, InvestmentColors.Eib, InvestmentColors.Local).distinct().size)
    }
    @Test fun newChartAndRegisterHaveBothTranslations() {
        for (key in listOf("regional_investment_title", "payment_register_title", "investment_source_difference")) {
            assertFalse(Strings.uk[key].isNullOrBlank()); assertFalse(Strings.en[key].isNullOrBlank())
        }
    }
}
