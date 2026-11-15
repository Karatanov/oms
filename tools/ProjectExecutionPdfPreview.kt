package oms.urp3.service

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID
import oms.urp3.domain.*

/** Synthetic regression previews only; no production records or images are read. */
fun main() {
    val code = "KH08_07#Lot1"
    val project = Project(1, UUID.randomUUID(), ProjectType.SUBPROJECT_PART, 1, null,
        "Перевірка виплат окремого лоту / Individual lot payment check", code, code,
        null, null, null, null, null, null, ProjectStatus.ACTIVE, null, null,
        1000, null, null, 100, null, null, null, null, null, null, null, "UAH", null)
    fun payment(code: String, amount: Double) = PaymentMonitoringWorkbookSource.Transaction(
        code, code, 8, LocalDate.of(2026, 11, 15), amount, null, "test")
    val execution = projectFinancialExecution(project, null, approvedPayments = listOf(
        payment("KH08_07#Lot1_W", 30.25), payment("KH08_07#Lot2_W", 60.50))) { error("No fallback") }
    check(execution.paidUah == 30.25)
    val output = Path.of("tmp/pdfs/lot-execution")
    Files.createDirectories(output)
    for (language in ProjectPdfExportService.Language.entries) {
        for ((name, values) in listOf("known" to execution, "unavailable" to ProjectFinancialExecution(null, 100.0))) {
            val pdf = ProjectPdfExportService().export(project, language = language, financialExecution = values)
            val target = output.resolve("${language.name.lowercase()}-$name.pdf")
            Files.write(target, pdf)
            println("$target (${pdf.size} bytes)")
        }
    }
}
