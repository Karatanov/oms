package oms.ufsi.service

import oms.ufsi.domain.*
import oms.ufsi.repository.FinancialRecordRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

class FinancialRecordService(private val repository: FinancialRecordRepository) {
    fun getAll(projectId: Long) = repository.findByProjectId(projectId)
    fun get(projectId: Long, uuid: String) = repository.findByUuid(projectId, uuid.trim())
    fun create(projectId: Long, type: String, reference: String, amount: Long, currency: String, date: String, paymentDate: String?, description: String?, milestone: String?) = repository.create(projectId, validatedType(type), required(reference, "Reference number"), positive(amount), currency(currency), date(date), optionalDate(paymentDate), optional(description), optional(milestone), 1L)
    fun update(projectId: Long, uuid: String, type: String, reference: String, amount: Long, currency: String, date: String, paymentDate: String?, description: String?, milestone: String?) = repository.update(projectId, uuid.trim(), validatedType(type), required(reference, "Reference number"), positive(amount), currency(currency), date(date), optionalDate(paymentDate), optional(description), optional(milestone))
    fun delete(projectId: Long, uuid: String) = repository.delete(projectId, uuid.trim())
    fun importXlsx(projectId: Long, input: InputStream): Int {
        XSSFWorkbook(input).use { workbook ->
            val sheet = workbook.getSheetAt(0) ?: throw IllegalArgumentException("Workbook must contain a financials sheet.")
            val headers = sheet.getRow(0)?.mapIndexed { index, cell -> cell.stringCellValue.trim().lowercase() to index }?.toMap()
                ?: throw IllegalArgumentException("The first row must contain headers.")
            fun cell(row: org.apache.poi.ss.usermodel.Row, name: String) = headers[name]?.let { row.getCell(it)?.toString()?.trim() }.orEmpty()
            require(setOf("record_type", "reference_number", "amount", "record_date").all(headers::containsKey)) { "Required columns: record_type, reference_number, amount, record_date." }
            var imported = 0
            for (index in 1..sheet.lastRowNum) {
                val row = sheet.getRow(index) ?: continue
                val reference = cell(row, "reference_number")
                if (reference.isBlank()) continue

                create(
                    projectId = projectId,
                    type = cell(row, "record_type"),
                    reference = reference,
                    amount = cell(row, "amount").toDouble().toLong(),
                    currency = cell(row, "currency").ifBlank { "UAH" },
                    date = cell(row, "record_date"),
                    paymentDate = cell(row, "payment_date").ifBlank { null },
                    description = cell(row, "description").ifBlank { null },
                    milestone = cell(row, "milestone").ifBlank { null }
                )
                imported++
            }
            return imported
        }
    }
    fun exportXlsx(projectId: Long, output: OutputStream) {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("financials")
            val headers = listOf("record_type", "reference_number", "amount", "currency", "record_date", "payment_date", "description", "milestone")
            sheet.createRow(0).apply { headers.forEachIndexed { index, header -> createCell(index).setCellValue(header) } }
            getAll(projectId).forEachIndexed { index, record ->
                sheet.createRow(index + 1).apply {
                    createCell(0).setCellValue(record.recordType.name.lowercase())
                    createCell(1).setCellValue(record.referenceNumber)
                    createCell(2).setCellValue(record.amount.toDouble())
                    createCell(3).setCellValue(record.currency)
                    createCell(4).setCellValue(record.recordDate.toString())
                    createCell(5).setCellValue(record.paymentDate?.toString().orEmpty())
                    createCell(6).setCellValue(record.description.orEmpty())
                    createCell(7).setCellValue(record.milestone.orEmpty())
                }
            }
            headers.indices.forEach(sheet::autoSizeColumn)
            workbook.write(output)
        }
    }
    fun summary(project: Project): FinancialSummary = FinancialSummary(project.budgetPlanned, getAll(project.id).filter { it.recordType == FinancialRecordType.ACT }.sumOf { it.amount })
    private fun validatedType(value: String) = try { FinancialRecordType.valueOf(value.trim().uppercase()) } catch (_: Exception) { throw IllegalArgumentException("Record type must be invoice, act, payment, or advance.") }
    private fun positive(value: Long): Long { require(value > 0) { "Amount must be positive." }; return value }
    private fun required(value: String, field: String): String { val result = value.trim(); require(result.isNotEmpty()) { "$field is required." }; return result }
    private fun currency(value: String): String { val result = value.trim().uppercase(); require(result.matches(Regex("[A-Z]{3}"))) { "Currency must be a three-letter ISO code." }; return result }
    private fun date(value: String): String = try { java.time.LocalDate.parse(value.trim()).toString() } catch (_: Exception) { throw IllegalArgumentException("Date must use YYYY-MM-DD format.") }
    private fun optionalDate(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }?.let(::date)
    private fun optional(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }
}

data class FinancialSummary(val budgetPlanned: Long, val amountSpent: Long) { val budgetRemaining = budgetPlanned - amountSpent; val completionPct = if (budgetPlanned == 0L) 0.0 else amountSpent * 100.0 / budgetPlanned }
