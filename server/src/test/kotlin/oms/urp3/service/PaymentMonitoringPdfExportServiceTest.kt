package oms.urp3.service

import java.time.LocalDate
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentMonitoringPdfExportServiceTest {
    private fun item() = PaymentMonitoringWorkbookSource.Item("CV08_03", 6643430.0, 5115749.11, 1527680.89,
        4655537.72, 4655537.72, 460211.39, 77.0, 70.0, "DEPLETING")
    private fun payments(count: Int) = (1..count).map { index -> PaymentMonitoringWorkbookSource.Transaction(
        "payment-$index", "CV08_03#Lot${index}_TS", 8, LocalDate.of(2026, 6, 1).plusDays(index.toLong()), 100.25, 2.0, "June") }

    fun preview(language: PaymentMonitoringPdfExportService.Language, count: Int = 2) = PaymentMonitoringPdfExportService().export(
        listOf(item()), language, "CV08_03", payments(count), "2026-06")

    @Test fun twoTablesShareAPageAndShowOriginalPaymentAmountsInBothLanguages() {
        for (language in PaymentMonitoringPdfExportService.Language.entries) {
            PDDocument.load(preview(language)).use { pdf ->
                assertEquals(1, pdf.numberOfPages)
                val text = PDFTextStripper().getText(pdf)
                assertTrue(text.contains(if (language == PaymentMonitoringPdfExportService.Language.UK) "Реєстр платежів" else "Payment register"))
                assertTrue(text.contains("CV08_03#Lot1_TS"))
                assertTrue(text.contains("CV08_03#Lot2_TS"))
                assertTrue(text.contains("100.25"))
                assertTrue(text.contains("200.50"))
                assertTrue(text.contains("4.00"))
                assertTrue(text.contains("2026-06"))
            }
        }
    }

    @Test fun longPaymentRegisterRepeatsItsHeaderAndExportsEveryPayment() {
        PDDocument.load(preview(PaymentMonitoringPdfExportService.Language.EN, 110)).use { pdf ->
            assertTrue(pdf.numberOfPages > 1)
            val stripper = PDFTextStripper()
            val text = stripper.getText(pdf)
            assertTrue(text.contains("CV08_03#Lot110_TS"))
            assertTrue(text.contains("11027.50"))
            stripper.startPage = pdf.numberOfPages
            assertTrue(stripper.getText(pdf).contains("Payment register"))
            assertTrue(stripper.getText(pdf).contains("Page ${pdf.numberOfPages} of ${pdf.numberOfPages}"))
        }
    }

    @Test fun missingPaymentsProduceAnExplicitEmptySection() {
        PDDocument.load(PaymentMonitoringPdfExportService().export(listOf(item()), PaymentMonitoringPdfExportService.Language.EN)).use { pdf ->
            assertTrue(PDFTextStripper().getText(pdf).contains("No payments match the selected filters"))
        }
    }

    @Test fun missingEurNeverProducesAMisleadingPartialTotal() {
        val rows = payments(2).mapIndexed { index, row -> if (index == 0) row.copy(amountEur = null) else row }
        val bytes = PaymentMonitoringPdfExportService().export(listOf(item()), PaymentMonitoringPdfExportService.Language.EN,
            transactions = rows)
        PDDocument.load(bytes).use { pdf ->
            val total = PDFTextStripper().getText(pdf).lineSequence().first { it.startsWith("Total ") && it.contains("200.50") }
            assertTrue(total.endsWith("—"))
        }
    }

    @Test fun sharedRendererRetainsSingleTablePagination() {
        val bytes = LandscapeTablePdfExportService().export(LandscapeTablePdfExportService.Request(
            title = "Single table", totalLabel = "Records", filtersLabel = "Filters", noRecordsLabel = "Empty",
            exportDateLabel = "Export date", pageLabel = { current, total -> "Page $current of $total" },
            columns = listOf(LandscapeTablePdfExportService.Column("Code", 1f)), rows = (1..90).map { listOf("CODE-$it") }
        ))
        PDDocument.load(bytes).use { pdf ->
            assertEquals(3, pdf.numberOfPages)
            val text = PDFTextStripper().getText(pdf)
            assertTrue(text.contains("CODE-90"))
            assertTrue(text.contains("Page 3 of 3"))
        }
    }
}
