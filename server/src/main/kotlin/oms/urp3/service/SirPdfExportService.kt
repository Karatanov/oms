package oms.urp3.service

import oms.urp3.domain.InspectionReport
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Comparator
import java.util.concurrent.TimeUnit

/**
 * Produces a PDF from the approved SIR workbook instead of maintaining a
 * second, potentially divergent, PDF layout.  LibreOffice renders the same
 * spreadsheet template that users download, including its print settings and
 * the `Photo Attachment` sheet generated for the report.
 */
class SirPdfExportService(
    private val reportFileService: InspectionReportFileService
) {
    data class ExportedSirPdf(val fileName: String, val bytes: ByteArray)

    fun export(report: InspectionReport, fileBaseName: String): ExportedSirPdf {
        val file = reportFileService.getFile(report.id)
            ?: throw IllegalArgumentException("Original SIR file not found.")
        val extension = file.originalName.substringAfterLast('.', "").lowercase()
        require(extension in setOf("xls", "xlsx")) { "Only XLS and XLSX SIR files can be exported as PDF." }
        val source = reportFileService.resolveFile(file)
            ?: throw IllegalArgumentException("Original SIR file is unavailable.")

        val workDirectory = Files.createTempDirectory("oms-sir-pdf-")
        try {
            // Do not pass a user-controlled filename to the converter.  Apart
            // from making command construction predictable, this also keeps
            // a report's original filename out of temporary process output.
            val workbook = workDirectory.resolve("report.$extension")
            Files.copy(source, workbook, StandardCopyOption.REPLACE_EXISTING)
            val profileDirectory = workDirectory.resolve("profile")
            Files.createDirectories(profileDirectory)

            val command = listOf(
                converterExecutable(),
                "--headless",
                "--nologo",
                "--nodefault",
                "--nofirststartwizard",
                "-env:UserInstallation=${profileDirectory.toUri()}",
                "--convert-to", "pdf",
                "--outdir", workDirectory.toString(),
                workbook.toString()
            )
            val process = try {
                // LibreOffice only writes diagnostic output here. Discard it
                // so a verbose converter cannot fill a pipe and deadlock the
                // request while the process is still running.
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
            } catch (exception: IOException) {
                throw PdfExportUnavailableException("The PDF export service is unavailable.", exception)
            }
            val completed = process.waitFor(CONVERSION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                throw PdfExportUnavailableException("The PDF export timed out.")
            }
            if (process.exitValue() != 0) {
                throw PdfExportUnavailableException("The PDF export service could not convert this SIR.")
            }

            val pdf = workDirectory.resolve("report.pdf")
            if (!Files.isRegularFile(pdf) || Files.size(pdf) == 0L) {
                throw PdfExportUnavailableException("The PDF export service could not create a PDF.")
            }
            return ExportedSirPdf(
                fileName = "${safeFileComponent(fileBaseName)}.pdf",
                bytes = Files.readAllBytes(pdf)
            )
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw PdfExportUnavailableException("The PDF export was interrupted.", exception)
        } finally {
            deleteRecursively(workDirectory)
        }
    }

    private fun converterExecutable(): String =
        System.getenv("OMS_LIBREOFFICE_BIN")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "libreoffice"

    private fun safeFileComponent(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "report" }

    private fun deleteRecursively(path: Path) {
        runCatching {
            Files.walk(path).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    class PdfExportUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

    private companion object {
        val CONVERSION_TIMEOUT: Duration = Duration.ofSeconds(90)
    }
}
