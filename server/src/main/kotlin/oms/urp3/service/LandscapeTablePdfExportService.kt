package oms.urp3.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Shared, print-oriented A3 landscape table renderer for registry exports.
 * Callers prepare only localized headers and business values; pagination,
 * wrapping, repeated headers and page numbers stay consistent across OMS.
 */
class LandscapeTablePdfExportService {
    data class Column(val label: String, val relativeWidth: Float)

    data class Request(
        val title: String,
        val totalLabel: String,
        val filtersLabel: String,
        val noRecordsLabel: String,
        val exportDateLabel: String,
        val pageLabel: (Int, Int) -> String,
        val columns: List<Column>,
        val rows: List<List<String>>,
        val filterSummary: String? = null
    )

    fun export(request: Request): ByteArray = PDDocument().use { document ->
        val fontBytes = requireNotNull(javaClass.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            "PDF export font is unavailable."
        }
        val font = fontBytes.use { PDType0Font.load(document, it, true) }
        Writer(document, font, request).write()
        ByteArrayOutputStream().use { output -> document.save(output); output.toByteArray() }
    }

    private class Writer(
        private val document: PDDocument,
        private val font: PDType0Font,
        private val request: Request
    ) {
        private val pageSize = PDRectangle(1190.55f, 841.89f) // A3 landscape
        // Print tables use narrow, but still practical, margins. Together with
        // a compact document header this saves meaningful vertical space on
        // multi-page registries without reducing the business data.
        private val margin = 20f
        private val usableWidth = pageSize.width - margin * 2
        private val columns = request.columns.let { source ->
            val total = source.sumOf { it.relativeWidth.toDouble() }.toFloat().coerceAtLeast(1f)
            source.map { it.copy(relativeWidth = it.relativeWidth * usableWidth / total) }
        }
        private var stream: PDPageContentStream? = null
        private var y = 0f

        fun write() {
            newPage()
            request.rows.forEach { row ->
                val cells = columns.indices.map { row.getOrElse(it) { "—" }.ifBlank { "—" } }
                val height = rowHeight(cells)
                if (y - height < margin + 14f) newPage()
                drawRow(cells, height)
            }
            if (request.rows.isEmpty()) text(request.noRecordsLabel, margin, y - 20f, 10f, INK)
            stream?.close()
            addPageNumbers()
        }

        private fun newPage() {
            stream?.close()
            val page = PDPage(pageSize)
            document.addPage(page)
            stream = PDPageContentStream(document, page)
            y = pageSize.height - margin
            drawDocumentHeader()
            drawTableHeader()
        }

        private fun drawDocumentHeader() {
            text(request.title, margin, y - 13f, 14f, PRIMARY)
            val date = "${request.exportDateLabel}: ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}"
            text(date, pageSize.width - margin - textWidth(date, 7.5f), y - 12f, 7.5f, INK)
            val total = "${request.totalLabel}: ${request.rows.size}"
            text(total, margin, y - 27f, 7.5f, MUTED)
            request.filterSummary?.takeIf(String::isNotBlank)?.let { summary ->
                text("${request.filtersLabel}: $summary", margin + 155f, y - 27f, 7.5f, MUTED)
            }
            y -= 52f
        }

        private fun drawTableHeader() {
            val top = y
            stream!!.setNonStrokingColor(PRIMARY[0], PRIMARY[1], PRIMARY[2])
            stream!!.addRect(margin, y - 24f, usableWidth, 24f)
            stream!!.fill()
            var x = margin
            columns.forEach { column ->
                wrap(column.label, 7f, column.relativeWidth - 6f).take(2).forEachIndexed { index, value ->
                    text(value, x + 3f, top - 9f - index * 8f, 7f, WHITE)
                }
                x += column.relativeWidth
            }
            y -= 24f
        }

        private fun drawRow(cells: List<String>, height: Float) {
            val top = y
            stream!!.setStrokingColor(0.76f, 0.83f, 0.86f)
            stream!!.addRect(margin, y - height, usableWidth, height)
            var x = margin
            columns.forEachIndexed { index, column ->
                if (index > 0) { stream!!.moveTo(x, y); stream!!.lineTo(x, y - height) }
                wrap(cells[index], 6.8f, column.relativeWidth - 6f).forEachIndexed { line, value ->
                    text(value, x + 3f, top - 8.5f - line * 7.9f, 6.8f, INK)
                }
                x += column.relativeWidth
            }
            stream!!.stroke()
            y -= height
        }

        private fun rowHeight(cells: List<String>): Float =
            (cells.indices.maxOf { index -> wrap(cells[index], 6.8f, columns[index].relativeWidth - 6f).size } * 7.9f + 7f)
                .coerceAtLeast(17f)

        private fun addPageNumbers() {
            val total = document.numberOfPages
            document.pages.forEachIndexed { index, page ->
                // An appended content stream has no active graphics state by
                // default. Reset it before setting the footer colour; otherwise
                // PDFBox can reject `setNonStrokingColor` with "The color space
                // must be set before setting a color".
                PDPageContentStream(document, page, AppendMode.APPEND, true, true).use { footer ->
                    val value = request.pageLabel(index + 1, total)
                    footer.beginText()
                    footer.setFont(font, 8f)
                    footer.setNonStrokingColor(INK[0], INK[1], INK[2])
                    footer.newLineAtOffset(pageSize.width - margin - textWidth(value, 8f), 15f)
                    footer.showText(value)
                    footer.endText()
                }
            }
        }

        /**
         * Spreadsheet data is external input and can contain control characters
         * or glyphs that are absent from the embedded PDF font. PDFBox throws
         * while measuring or writing such a value, which must not abort an
         * otherwise valid registry export.
         */
        private fun pdfText(value: String): String {
            val result = StringBuilder(value.length)
            var index = 0
            while (index < value.length) {
                val codePoint = value.codePointAt(index)
                val character = String(Character.toChars(codePoint))
                val isAllowedWhitespace = codePoint == '\n'.code || codePoint == '\r'.code || codePoint == '\t'.code
                val printable = codePoint >= 0x20 && codePoint != 0x7f
                if ((printable || isAllowedWhitespace) && runCatching { font.getStringWidth(character) }.isSuccess) {
                    result.append(character)
                } else {
                    result.append(' ')
                }
                index += Character.charCount(codePoint)
            }
            return result.toString().trim().ifBlank { "—" }
        }

        private fun wrap(value: String, size: Float, width: Float): List<String> {
            val lines = mutableListOf<String>()
            var line = ""
            pdfText(value).replace('\n', ' ').trim().split(Regex("\\s+")).filter(String::isNotBlank).forEach { word ->
                splitLongWord(word, size, width).forEach { piece ->
                    val candidate = if (line.isEmpty()) piece else "$line $piece"
                    if (line.isNotEmpty() && textWidth(candidate, size) > width) { lines += line; line = piece } else line = candidate
                }
            }
            if (line.isNotEmpty()) lines += line
            return lines.ifEmpty { listOf("—") }
        }

        private fun splitLongWord(word: String, size: Float, width: Float): List<String> {
            if (textWidth(word, size) <= width) return listOf(word)
            val pieces = mutableListOf<String>()
            var current = ""
            word.forEach { character ->
                val candidate = current + character
                if (current.isNotEmpty() && textWidth(candidate, size) > width) { pieces += current; current = character.toString() } else current = candidate
            }
            if (current.isNotEmpty()) pieces += current
            return pieces
        }

        private fun textWidth(value: String, size: Float) = font.getStringWidth(pdfText(value)) / 1000f * size
        private fun text(value: String, x: Float, baseline: Float, size: Float, color: FloatArray) {
            stream!!.beginText(); stream!!.setNonStrokingColor(color[0], color[1], color[2]); stream!!.setFont(font, size)
            stream!!.newLineAtOffset(x, baseline); stream!!.showText(pdfText(value)); stream!!.endText()
        }

        private companion object {
            val PRIMARY = floatArrayOf(.153f, .553f, .678f)
            val INK = floatArrayOf(.10f, .17f, .22f)
            val MUTED = floatArrayOf(.28f, .36f, .40f)
            val WHITE = floatArrayOf(1f, 1f, 1f)
        }
    }
}
