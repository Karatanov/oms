package oms.umitaf.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import oms.umitaf.domain.FinancialRecord
import oms.umitaf.domain.Project
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat

/**
 * Produces a portable, Unicode-safe project brief.  The bundled DejaVu font is
 * intentional: project names and Ukrainian values must remain legible on a
 * recipient's machine without relying on the server's installed fonts.
 */
class ProjectPdfExportService {
    fun export(project: Project, financialRecords: List<FinancialRecord>): ByteArray =
        PDDocument().use { document ->
            val fontBytes = requireNotNull(javaClass.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
                "PDF export font is unavailable."
            }
            val font = fontBytes.use { PDType0Font.load(document, it, true) }
            val writer = PdfWriter(document, font)

            writer.title("Subproject general information and financials")
            writer.field("Name", project.name)
            writer.field("Code", project.siteNumber)
            writer.field("Tranche", if (project.trancheNumber == 2 || project.trancheNumber == 9) "B" else "A")
            writer.field("Status", project.status.name.lowercase().replace('_', ' '))
            writer.field("Description", project.description)
            writer.field("Address", listOfNotNull(project.address, project.city, project.region).filter(String::isNotBlank).joinToString(", "))
            writer.field("Coordinates", if (project.latitude != null && project.longitude != null) "${project.latitude}, ${project.longitude}" else null)
            writer.field("Sector", project.sector)
            writer.field("Construction type", project.constructionType)

            writer.title("Design information")
            writer.field("Designer", project.designerName)
            writer.field("Contract number", project.designContractNumber)
            writer.field("Contract date", project.designContractSigningDate?.toString())
            writer.field("Start date", project.designStartDate?.toString())
            writer.field("Planned completion", project.designPlannedEndDate?.toString())
            writer.field("Contract term", project.designContractTerm)

            writer.title("Construction contractor information")
            writer.field("Contractor", project.contractorName)
            writer.field("Contract number", project.constructionContractNumber)
            writer.field("Contract date", project.constructionContractSigningDate?.toString())
            writer.field("Construction start", project.constructionStartDate?.toString())
            writer.field("Planned completion", project.projectedCompletionTime?.toString() ?: project.plannedEndDate?.toString())

            writer.title("Technical supervision information")
            writer.field("Organisation", project.technicalSupervisionName)
            writer.field("Contract number", project.technicalSupervisionContractNumber)
            writer.field("Contract date", project.technicalSupervisionContractDate?.toString())
            writer.field("Start date", project.technicalSupervisionStartDate?.toString())
            writer.field("Planned completion", project.technicalSupervisionPlannedEndDate?.toString())

            writer.title("Engineer-consultant information")
            writer.field("Organisation", project.engineerConsultantName)
            writer.field("Contract number", project.engineerConsultantContractNumber)
            writer.field("Contract date", project.engineerConsultantContractDate?.toString())
            writer.field("Start date", project.engineerConsultantStartDate?.toString())
            writer.field("Planned completion", project.engineerConsultantPlannedEndDate?.toString())

            writer.title("Financing and contracts")
            writer.field("Total project cost", money(project.budgetPlanned, project.currency))
            writer.field("Construction contract", project.subprojectContractAmount?.let { money(it, project.currency) })
            writer.field("Technical supervision contract", project.technicalSupervisionAmount?.let { money(it, project.currency) })
            writer.field("Engineer-consultant contract", project.engineerConsultantContractAmount?.let { money(it, project.currency) })
            project.amounts.entries.sortedBy { it.key }.forEach { (key, amount) ->
                writer.field(key.replace('_', ' ').replaceFirstChar { it.uppercase() }, "${amount.amount} ${amount.currency}")
            }

            writer.title("Financial records")
            if (financialRecords.isEmpty()) writer.paragraph("No financial records.")
            else financialRecords.sortedByDescending { it.recordDate }.forEach { record ->
                writer.paragraph("${record.recordDate} · ${record.recordType.name.lowercase()} · ${record.referenceNumber} · ${money(record.amount, record.currency)}${record.description?.let { " · $it" }.orEmpty()}")
            }
            ByteArrayOutputStream().use { output ->
                writer.close()
                document.save(output)
                output.toByteArray()
            }
        }

    private fun money(value: Long, currency: String) = "${DecimalFormat("#,##0.00").format(value.toDouble())} $currency"
    private fun money(value: Double, currency: String) = "${DecimalFormat("#,##0.00").format(value)} $currency"

    private class PdfWriter(private val document: PDDocument, private val font: PDType0Font) {
        private val margin = 42f
        private var page: PDPage? = null
        private var stream: PDPageContentStream? = null
        private var y = 0f

        init { newPage() }

        fun title(value: String) = line(value, 14f, 9f)
        fun field(label: String, value: String?) {
            value?.trim()?.takeIf(String::isNotEmpty)?.let { line("$label: $it", 10f, 5f) }
        }
        fun paragraph(value: String) = line(value, 10f, 5f)

        fun close() { stream?.close() }

        private fun line(value: String, size: Float, spacingAfter: Float) {
            wrap(value, size).forEach { text ->
                if (y < margin + size + 4) newPage()
                stream!!.beginText()
                stream!!.setFont(font, size)
                stream!!.newLineAtOffset(margin, y)
                stream!!.showText(text)
                stream!!.endText()
                y -= size + 3f
            }
            y -= spacingAfter
        }

        private fun newPage() {
            stream?.close()
            page = PDPage(PDRectangle.A4)
            document.addPage(page)
            stream = PDPageContentStream(document, page)
            y = page!!.mediaBox.height - margin
        }

        private fun wrap(value: String, size: Float): List<String> {
            val limit = PDRectangle.A4.width - margin * 2
            val words = value.replace('\n', ' ').trim().split(Regex("\\s+"))
            val lines = mutableListOf<String>()
            var current = ""
            words.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (font.getStringWidth(candidate) / 1000f * size <= limit || current.isEmpty()) current = candidate
                else { lines += current; current = word }
            }
            if (current.isNotEmpty()) lines += current
            return lines
        }
    }
}
