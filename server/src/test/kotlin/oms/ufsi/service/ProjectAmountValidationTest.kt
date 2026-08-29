package oms.ufsi.service

import oms.ufsi.dto.ProjectAmountDto
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectAmountValidationTest {
    private val date = LocalDate.now().minusDays(1).toString()

    @Test
    fun `automatic EUR conversion is normalized and projected to legacy UAH`() {
        val value = validateProjectAmounts(mapOf("budget" to ProjectAmountDto("100.00", "EUR", "5000.01", "50.00000000", date)))
            .getValue("budget")
        assertEquals("5000.00", value.convertedAmount.toPlainString())
        assertEquals(5000L, value.legacyUah())
    }

    @Test
    fun `manually edited equivalent is preserved`() {
        val value = validateProjectAmounts(mapOf("budget" to ProjectAmountDto("100.00", "EUR", "5100.00", "50.00000000", date, true)))
            .getValue("budget")
        assertEquals("5100.00", value.convertedAmount.toPlainString())
    }

    @Test
    fun `unknown amount kinds and currencies are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            validateProjectAmounts(mapOf("other" to ProjectAmountDto("1", "EUR", "50", "50", date)))
        }
        assertFailsWith<IllegalArgumentException> {
            validateProjectAmounts(mapOf("budget" to ProjectAmountDto("1", "USD", "50", "50", date)))
        }
    }
}
