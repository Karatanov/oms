package oms.ufsi.service

import oms.ufsi.domain.*
import oms.ufsi.repository.FinancialRecordRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.io.OutputStream

class FinancialRecordService(
    private val repository: FinancialRecordRepository,
    private val exchangeRateService: NbuExchangeRateService
) {
    fun getAll() = repository.findAll()
    fun getAll(projectId: Long) = repository.findByProjectId(projectId)
    fun filtered(projectId: Long, recordType: String?, dateFrom: String?, dateTo: String?): List<FinancialRecord> {
        val type = recordType?.trim()?.takeIf { it.isNotEmpty() }?.let(::validatedType)
        val from = dateFrom?.trim()?.takeIf { it.isNotEmpty() }?.let(::date)?.let(java.time.LocalDate::parse)
        val to = dateTo?.trim()?.takeIf { it.isNotEmpty() }?.let(::date)?.let(java.time.LocalDate::parse)
        require(from == null || to == null || !from.isAfter(to)) { "date_from must not be later than date_to." }
        return getAll(projectId).filter { record ->
            (type == null || record.recordType == type) &&
                (from == null || !record.recordDate.isBefore(from)) &&
                (to == null || !record.recordDate.isAfter(to))
        }
    }
    fun get(projectId: Long, uuid: String) = repository.findByUuid(projectId, uuid.trim())
    fun create(projectId: Long, type: String, reference: String, amount: Long, currency: String, date: String, paymentDate: String?, description: String?, milestone: String?, paymentPurpose: String = "works"): FinancialRecord {
        val normalizedCurrency = currency(currency)
        val normalizedDate = date(date)
        val conversion = exchangeRateService.convertToEur(positive(amount), normalizedCurrency, java.time.LocalDate.parse(normalizedDate))
        return repository.create(projectId, validatedType(type), required(reference, "Reference number"), amount, normalizedCurrency, normalizedDate, optionalDate(paymentDate), optional(description) ?: optional(milestone), null, paymentPurpose(paymentPurpose), conversion.rate, conversion.effectiveDate.toString(), conversion.amountEurCents, 1L)
    }
    fun update(projectId: Long, uuid: String, type: String, reference: String, amount: Long, currency: String, date: String, paymentDate: String?, description: String?, milestone: String?, paymentPurpose: String = "works"): FinancialRecord? {
        val normalizedCurrency = currency(currency)
        val normalizedDate = date(date)
        val conversion = exchangeRateService.convertToEur(positive(amount), normalizedCurrency, java.time.LocalDate.parse(normalizedDate))
        return repository.update(projectId, uuid.trim(), validatedType(type), required(reference, "Reference number"), amount, normalizedCurrency, normalizedDate, optionalDate(paymentDate), optional(description) ?: optional(milestone), null, paymentPurpose(paymentPurpose), conversion.rate, conversion.effectiveDate.toString(), conversion.amountEurCents)
    }
    fun move(projectId: Long, uuid: String, targetProjectId: Long) = repository.move(projectId, uuid.trim(), targetProjectId)
    fun delete(projectId: Long, uuid: String) = repository.delete(projectId, uuid.trim())
    fun importWorkbook(projectId: Long, input: InputStream): FinancialImportResult {
        WorkbookFactory.create(input).use { workbook ->
            val sheet = workbook.getSheetAt(0) ?: throw IllegalArgumentException("Workbook must contain a financials sheet.")
            val formatter = DataFormatter()
            val headers = sheet.getRow(0)?.mapIndexed { index, cell -> formatter.formatCellValue(cell).trim().lowercase() to index }?.toMap()
                ?: throw IllegalArgumentException("The first row must contain headers.")
            fun cell(row: org.apache.poi.ss.usermodel.Row, name: String) = headers[name]?.let { formatter.formatCellValue(row.getCell(it)).trim() }.orEmpty()
            require(setOf("record_type", "reference_number", "amount", "record_date").all(headers::containsKey)) { "Required columns: record_type, reference_number, amount, record_date." }
            var imported = 0
            var skipped = 0
            val errors = mutableListOf<FinancialImportError>()
            val existingReferences = getAll(projectId).map { it.referenceNumber.lowercase() }.toMutableSet()
            require(sheet.lastRowNum <= 1000) { "A financial import may contain at most 1,000 data rows." }
            for (index in 1..sheet.lastRowNum) {
                val row = sheet.getRow(index) ?: continue
                val reference = cell(row, "reference_number")
                if (reference.isBlank()) continue
                if (!existingReferences.add(reference.lowercase())) {
                    skipped++
                    continue
                }
                try {
                    create(projectId, cell(row, "record_type"), reference, cell(row, "amount").toDouble().toLong(), cell(row, "currency").ifBlank { "UAH" }, cell(row, "record_date"), cell(row, "payment_date").ifBlank { null }, cell(row, "description").ifBlank { null }, cell(row, "milestone").ifBlank { null }, cell(row, "payment_purpose").ifBlank { "works" })
                    imported++
                } catch (exception: Exception) { errors += FinancialImportError(index + 1, null, exception.message ?: "Invalid row.") }
            }
            return FinancialImportResult(imported, skipped, errors)
        }
    }
    fun exportXlsx(projectId: Long, output: OutputStream) {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("financials")
            val headers = listOf("record_type", "reference_number", "amount", "currency", "record_date", "payment_date", "description", "milestone", "payment_purpose", "eur_exchange_rate", "eur_exchange_date", "amount_eur")
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
                    createCell(8).setCellValue(record.paymentPurpose)
                    createCell(9).setCellValue(record.eurExchangeRate ?: 0.0)
                    createCell(10).setCellValue(record.eurExchangeDate?.toString().orEmpty())
                    createCell(11).setCellValue(record.amountEurCents?.div(100.0) ?: 0.0)
                }
            }
            headers.indices.forEach(sheet::autoSizeColumn)
            workbook.write(output)
        }
    }
    fun summary(project: Project): FinancialSummary {
        val records = getAll(project.id)
        return FinancialSummary(
            constructionContractAmount = project.subprojectContractAmount ?: project.budgetPlanned,
            amountSpent = records.filter { it.recordType == FinancialRecordType.ACT }.sumOf { it.amount },
            financialDocumentsAmount = records.sumOf { it.amount }
        )
    }
    private fun validatedType(value: String) = try { FinancialRecordType.valueOf(value.trim().uppercase()) } catch (_: Exception) { throw IllegalArgumentException("Record type must be invoice, act, payment, or advance.") }
    private fun positive(value: Long): Long { require(value > 0) { "Amount must be positive." }; return value }
    private fun required(value: String, field: String): String { val result = value.trim(); require(result.isNotEmpty()) { "$field is required." }; return result }
    private fun currency(value: String): String { val result = value.trim().uppercase(); require(result.matches(Regex("[A-Z]{3}"))) { "Currency must be a three-letter ISO code." }; return result }
    private fun date(value: String): String = try { java.time.LocalDate.parse(value.trim()).toString() } catch (_: Exception) { throw IllegalArgumentException("Date must use YYYY-MM-DD format.") }
    private fun optionalDate(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }?.let(::date)
    private fun optional(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }
    private fun paymentPurpose(value: String): String = value.trim().lowercase().also {
        require(it in setOf("works", "equipment")) { "Payment purpose must be works or equipment." }
    }
}

data class FinancialImportError(val row: Int, val field: String?, val message: String)
data class FinancialImportResult(val imported: Int, val skipped: Int, val errors: List<FinancialImportError>)

data class FinancialSummary(
    val constructionContractAmount: Long,
    val amountSpent: Long,
    val financialDocumentsAmount: Long
) {
    // Kept for existing API consumers; it is the construction-contract balance.
    val budgetPlanned get() = constructionContractAmount
    val budgetRemaining get() = constructionContractAmount - amountSpent
    val completionPct get() = if (constructionContractAmount == 0L) 0.0 else amountSpent * 100.0 / constructionContractAmount
    val financialCompletionPct get() = if (constructionContractAmount == 0L) 0.0 else financialDocumentsAmount * 100.0 / constructionContractAmount
}
