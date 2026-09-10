package oms.ufsi.service

import oms.ufsi.database.tables.FinancialRecordTable
import oms.ufsi.database.tables.ProjectDocumentTable
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.Locale

/**
 * Materializes a small, downloadable PDF act for each imported UR III payment.
 * The database remains authoritative: a missing runtime file is restored by
 * DurableFileStorage after a Render restart.
 */
class GeneratedFinancialActService(
    private val documentService: ProjectDocumentService
) {
    private data class Payment(
        val id: Long,
        val projectId: Long,
        val reference: String,
        val sourceId: String,
        val date: LocalDate,
        val amountUah: Double,
        val amountEur: Double?
    )

    fun ensureGeneratedActs(): Int {
        val payments = transaction {
            val linkedFinancialRecordIds = ProjectDocumentTable.selectAll()
                .filter { it[ProjectDocumentTable.relatedEntity] == "financial_record" }
                .mapNotNull { it[ProjectDocumentTable.relatedId] }
                .toSet()
            FinancialRecordTable.selectAll()
                .filter {
                    it[FinancialRecordTable.recordType] == "payment" &&
                        it[FinancialRecordTable.referenceNumber].startsWith("URIII-PMT-") &&
                        it[FinancialRecordTable.id].value !in linkedFinancialRecordIds
                }
                .map { row ->
                    Payment(
                        row[FinancialRecordTable.id].value,
                        row[FinancialRecordTable.projectId].value,
                        row[FinancialRecordTable.referenceNumber],
                        row[FinancialRecordTable.milestone].orEmpty(),
                        row[FinancialRecordTable.paymentDate] ?: row[FinancialRecordTable.recordDate],
                        row[FinancialRecordTable.amount].toDouble(),
                        row[FinancialRecordTable.amountEurCents]?.toDouble()?.div(100)
                    )
                }
        }
        payments.forEach { payment ->
            val fileName = "Act_${payment.reference}.pdf"
            documentService.upload(
                projectId = payment.projectId,
                type = "act",
                name = fileName,
                contentType = "application/pdf",
                input = ByteArrayInputStream(pdfFor(payment)),
                relatedEntity = "financial_record",
                relatedId = payment.id,
                description = "Generated act for ${payment.reference}; source ID: ${payment.sourceId}"
            )
        }
        return payments.size
    }

    private fun pdfFor(payment: Payment): ByteArray {
        val lines = listOf(
            "FINANCIAL ACT",
            "Programme: Ukraine Recovery Programme III",
            "Payment reference: ${payment.reference}",
            "Source payment ID: ${payment.sourceId}",
            "Payment date: ${payment.date}",
            "Amount: ${"%.2f".format(Locale.US, payment.amountUah)} UAH",
            payment.amountEur?.let { "EUR equivalent: ${"%.2f".format(Locale.US, it)} EUR" } ?: "",
            "Generated from UR III Payments.xlsx"
        ).filter(String::isNotBlank)
        val content = buildString {
            append("BT\n/F1 14 Tf\n50 780 Td\n")
            lines.forEachIndexed { index, line ->
                if (index > 0) append("0 -22 Td\n/F1 11 Tf\n")
                append('(').append(line.pdfText()).append(") Tj\n")
            }
            append("ET")
        }
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
            "<< /Length ${content.toByteArray().size} >>\nstream\n$content\nendstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        )
        val pdf = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEachIndexed { index, body ->
            offsets += pdf.toString().toByteArray().size
            pdf.append(index + 1).append(" 0 obj\n").append(body).append("\nendobj\n")
        }
        val xref = pdf.toString().toByteArray().size
        pdf.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { pdf.append("%010d 00000 n \n".format(Locale.US, it)) }
        pdf.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return pdf.toString().toByteArray()
    }

    private fun String.pdfText() =
        replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
            .map { if (it.code in 32..126) it else '?' }.joinToString("")
}
