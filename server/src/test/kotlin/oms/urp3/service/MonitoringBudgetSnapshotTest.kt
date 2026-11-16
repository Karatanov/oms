package oms.urp3.service

import oms.urp3.domain.ProjectAmount
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.*

class MonitoringBudgetSnapshotTest {
    @Test fun `dashboard and saved budget parsing agree on supported localized source amounts`() {
        for (raw in listOf("1,234.56", "1.234,56", "1\u202f234,56", "+1,234.56")) {
            assertEquals(BigDecimal("1234.56"), monitoringBudgetAmount(raw), raw)
            assertEquals(1234.56, monitoringSourceAmount(raw), raw)
        }
        assertEquals(BigDecimal("0.995"), monitoringSourceAmount("0,995")?.let(BigDecimal::valueOf))
        assertNull(monitoringSourceAmount("12,34.56"))
        assertNull(monitoringBudgetAmount("12,34.56"))
        assertNull(monitoringBudgetAmount("-1.234,56"))
    }
    private val saved = ProjectAmount(BigDecimal("960.00"), "UAH", BigDecimal("20.00"),
        BigDecimal("48.00000000"), LocalDate.of(2026, 1, 1), false)

    @Test fun `refresh preserves frozen metadata and uses exact source cents`() {
        val updated = assertNotNull(refreshedMonitoringBudget("1 200,25", saved))
        assertEquals(BigDecimal("1200.25"), updated.amount)
        assertEquals(BigDecimal("25.01"), updated.convertedAmount)
        assertEquals(saved.copy(amount = updated.amount, convertedAmount = updated.convertedAmount), updated)
        assertNull(refreshedMonitoringBudget("1200.25", updated))
    }

    @Test fun `protect manual conversions EUR originals and inconsistent saved rates`() {
        for (protected in listOf(saved.copy(conversionEdited = true), saved.copy(currency = "EUR"),
            saved.copy(uahPerEur = BigDecimal.ZERO), saved.copy(convertedAmount = BigDecimal("99")))) {
            assertNull(refreshedMonitoringBudget("1200", protected))
        }
    }

    @Test fun `blank invalid and out of range source are not invented as zero`() {
        for (raw in listOf("", "-", "NaN", "Infinity", "-12", "10000000000000000", "9999999999999999.999")) {
            assertNull(refreshedMonitoringBudget(raw, saved), raw)
        }
        assertEquals(BigDecimal("0.00"), assertNotNull(refreshedMonitoringBudget("0", saved)).amount)
    }

    @Test fun `grouping and large amounts retain every cent`() {
        assertEquals(BigDecimal("217916883.20"), monitoringBudgetAmount("217,916,883.20"))
        assertEquals(BigDecimal("1234567.00"), monitoringBudgetAmount("1,234,567"))
        assertEquals(BigDecimal("9999999999999999.99"), monitoringBudgetAmount("9999999999999999.99"))
    }

    @Test fun `source corroboration repairs stale original without changing its saved equivalent`() {
        val stale = saved.copy(amount = BigDecimal("3959211.85"), convertedAmount = BigDecimal("83721.00"))
        val updated = assertNotNull(refreshedMonitoringBudget("4018608", stale))
        assertEquals(stale.copy(amount = BigDecimal("4018608.00")), updated)
        assertNull(refreshedMonitoringBudget("4018608", updated))
    }

    @Test fun `one cent near match does not corroborate an inconsistent snapshot or override a manual edit`() {
        for (protected in listOf(saved.copy(convertedAmount = BigDecimal("25.00")),
            saved.copy(convertedAmount = BigDecimal("25.02")),
            saved.copy(convertedAmount = BigDecimal("25.01"), conversionEdited = true),
            saved.copy(convertedAmount = BigDecimal("25.01"), currency = "EUR"))) {
            assertNull(refreshedMonitoringBudget("1200.25", protected))
        }
    }
}
