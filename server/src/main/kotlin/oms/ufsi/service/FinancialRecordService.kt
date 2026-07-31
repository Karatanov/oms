package oms.ufsi.service

import oms.ufsi.domain.*
import oms.ufsi.repository.FinancialRecordRepository

class FinancialRecordService(private val repository: FinancialRecordRepository) {
    fun getAll(projectId: Long) = repository.findByProjectId(projectId)
    fun get(projectId: Long, uuid: String) = repository.findByUuid(projectId, uuid.trim())
    fun create(projectId: Long, type: String, reference: String, amount: Long, currency: String, date: String, paymentDate: String?, description: String?, milestone: String?) = repository.create(projectId, validatedType(type), required(reference, "Reference number"), positive(amount), currency(currency), date(date), optionalDate(paymentDate), optional(description), optional(milestone), 1L)
    fun update(projectId: Long, uuid: String, type: String, reference: String, amount: Long, currency: String, date: String, paymentDate: String?, description: String?, milestone: String?) = repository.update(projectId, uuid.trim(), validatedType(type), required(reference, "Reference number"), positive(amount), currency(currency), date(date), optionalDate(paymentDate), optional(description), optional(milestone))
    fun delete(projectId: Long, uuid: String) = repository.delete(projectId, uuid.trim())
    fun summary(project: Project): FinancialSummary = FinancialSummary(project.budgetPlanned, getAll(project.id).filter { it.recordType in setOf(FinancialRecordType.ACT, FinancialRecordType.PAYMENT) }.sumOf { it.amount })
    private fun validatedType(value: String) = try { FinancialRecordType.valueOf(value.trim().uppercase()) } catch (_: Exception) { throw IllegalArgumentException("Record type must be invoice, act, payment, or advance.") }
    private fun positive(value: Long): Long { require(value > 0) { "Amount must be positive." }; return value }
    private fun required(value: String, field: String): String { val result = value.trim(); require(result.isNotEmpty()) { "$field is required." }; return result }
    private fun currency(value: String): String { val result = value.trim().uppercase(); require(result.matches(Regex("[A-Z]{3}"))) { "Currency must be a three-letter ISO code." }; return result }
    private fun date(value: String): String = try { java.time.LocalDate.parse(value.trim()).toString() } catch (_: Exception) { throw IllegalArgumentException("Date must use YYYY-MM-DD format.") }
    private fun optionalDate(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }?.let(::date)
    private fun optional(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }
}

data class FinancialSummary(val budgetPlanned: Long, val amountSpent: Long) { val budgetRemaining = budgetPlanned - amountSpent; val completionPct = if (budgetPlanned == 0L) 0.0 else amountSpent * 100.0 / budgetPlanned }
