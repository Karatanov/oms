package oms.umitaf.service

import oms.umitaf.domain.ProcurementRecord
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcurementTablePdfExportServiceTest {
    @Test fun `exports the complete procurement register as two readable sections`() {
        val record = ProcurementRecord(
            id = 1L, batchId = 8, oblastName = "Харківська", oblastId = "UA63",
            subProjectId = "KH08_01", subProjectLotId = null, purchaseStatus = "Договір укладено / Contract signed",
            tenderId = "T-1", prozorroTenderId = "UA-2026-01-01-000001-a",
            contractorNameUkr = "Підрядник", contractorNameEng = "Contractor", contractorId = "C-1",
            contractDate = null, contractEndDate = null, contractDurationMonths = null,
            contractAmountUah = 1_250_000.0, contractAmountEur = null, financingContractDifferencePct = null,
            subprojectNameUk = "Тестовий субпроєкт", subprojectNameEn = "Test subproject",
            promotorName = "Promotor", fbName = "ФБ", procurementMethod = "Відкриті торги / Open tender",
            tenderAttemptCount = 2, pigViolations = "Немає / None", contractAmountUahWithoutVat = 1_041_666.67,
            dreamCoFinancingPct = 10.0, realLocalCoFinancingPct = 12.5, realEibFinancingUah = 1_100_000.0,
            bankGuarantee = 5.0, contractLink = "https://example.test/contract"
        )

        val pdf = ProcurementTablePdfExportService().export(
            records = listOf(record),
            language = ProcurementTablePdfExportService.Language.EN,
            filterSummary = "Tranche: A"
        )

        assertTrue(pdf.decodeToString(0, 4).startsWith("%PDF"))
        PDDocument.load(pdf).use { document ->
            assertEquals(2, document.numberOfPages)
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("Contract link"))
            assertTrue(text.contains("https://example.test/contract"))
        }
    }
}
