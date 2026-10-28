package oms.urp3.service

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class InspectionImportFileNameTest {
    @Test
    fun `adds current date when imported sir name has no date`() {
        val (name, date) = InspectionReportFileService.normalizedImportName(
            "Borodyanka inspection.xlsx",
            LocalDate.of(2026, 10, 28)
        )

        assertEquals("Borodyanka inspection_28102026.xlsx", name)
        assertEquals(LocalDate.of(2026, 10, 28), date)
    }

    @Test
    fun `keeps a valid inspection date from source name`() {
        val (name, date) = InspectionReportFileService.normalizedImportName(
            "SIR-USIF_22092026.xlsx",
            LocalDate.of(2026, 10, 28)
        )

        assertEquals("SIR-USIF_22092026.xlsx", name)
        assertEquals(LocalDate.of(2026, 9, 22), date)
    }
}
