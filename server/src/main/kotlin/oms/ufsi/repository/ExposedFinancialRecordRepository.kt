package oms.ufsi.repository

import oms.ufsi.database.tables.FinancialRecordTable
import oms.ufsi.domain.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID

class ExposedFinancialRecordRepository : FinancialRecordRepository {
    override fun findAll() = transaction { FinancialRecordTable.selectAll().map(::map) }
    override fun findByProjectId(projectId: Long) = transaction {
        FinancialRecordTable.selectAll().filter { it[FinancialRecordTable.projectId].value == projectId }.map(::map)
    }
    override fun findByUuid(projectId: Long, uuid: String) = transaction {
        FinancialRecordTable.selectAll().firstOrNull { it[FinancialRecordTable.projectId].value == projectId && it[FinancialRecordTable.uuid] == uuid }?.let(::map)
    }
    override fun create(projectId: Long, type: FinancialRecordType, reference: String, amount: Long, currency: String, recordDate: String, paymentDate: String?, description: String?, milestone: String?, paymentPurpose: String, eurExchangeRate: Double, eurExchangeDate: String, amountEurCents: Long, createdBy: Long) = transaction {
        val uuid = UUID.randomUUID()
        val id = FinancialRecordTable.insertAndGetId {
            it[this.uuid] = uuid.toString(); it[this.projectId] = projectId; it[recordType] = type.name.lowercase(); it[referenceNumber] = reference
            it[this.amount] = amount; it[this.currency] = currency; it[this.recordDate] = LocalDate.parse(recordDate); it[this.paymentDate] = paymentDate?.let(LocalDate::parse)
            it[this.description] = description; it[this.milestone] = milestone; it[this.paymentPurpose] = paymentPurpose; it[this.eurExchangeRate] = java.math.BigDecimal.valueOf(eurExchangeRate); it[this.eurExchangeDate] = LocalDate.parse(eurExchangeDate); it[this.amountEurCents] = amountEurCents; it[this.createdBy] = createdBy
        }
        FinancialRecord(id.value, uuid, projectId, type, reference, amount, currency, LocalDate.parse(recordDate), paymentDate?.let(LocalDate::parse), description, milestone, paymentPurpose, eurExchangeRate, LocalDate.parse(eurExchangeDate), amountEurCents)
    }
    override fun update(projectId: Long, uuid: String, type: FinancialRecordType, reference: String, amount: Long, currency: String, recordDate: String, paymentDate: String?, description: String?, milestone: String?, paymentPurpose: String, eurExchangeRate: Double, eurExchangeDate: String, amountEurCents: Long) = transaction {
        val n = FinancialRecordTable.update({ (FinancialRecordTable.projectId eq projectId) and (FinancialRecordTable.uuid eq uuid) }) {
            it[recordType] = type.name.lowercase(); it[referenceNumber] = reference; it[this.amount] = amount; it[this.currency] = currency; it[this.recordDate] = LocalDate.parse(recordDate); it[this.paymentDate] = paymentDate?.let(LocalDate::parse); it[this.description] = description; it[this.milestone] = milestone; it[this.paymentPurpose] = paymentPurpose; it[this.eurExchangeRate] = java.math.BigDecimal.valueOf(eurExchangeRate); it[this.eurExchangeDate] = LocalDate.parse(eurExchangeDate); it[this.amountEurCents] = amountEurCents
        }; if (n == 0) null else findByUuid(projectId, uuid)
    }
    override fun move(projectId: Long, uuid: String, targetProjectId: Long) = transaction {
        FinancialRecordTable.update({ (FinancialRecordTable.projectId eq projectId) and (FinancialRecordTable.uuid eq uuid) }) {
            it[this.projectId] = targetProjectId
        } > 0
    }
    override fun updateEurConversion(uuid: String, eurExchangeRate: Double, eurExchangeDate: String, amountEurCents: Long) = transaction {
        FinancialRecordTable.update({ FinancialRecordTable.uuid eq uuid }) {
            it[this.eurExchangeRate] = java.math.BigDecimal.valueOf(eurExchangeRate)
            it[this.eurExchangeDate] = LocalDate.parse(eurExchangeDate)
            it[this.amountEurCents] = amountEurCents
        } > 0
    }
    override fun delete(projectId: Long, uuid: String) = transaction { FinancialRecordTable.deleteWhere { (FinancialRecordTable.projectId eq projectId) and (FinancialRecordTable.uuid eq uuid) } > 0 }
    private fun map(r: org.jetbrains.exposed.v1.core.ResultRow) = FinancialRecord(r[FinancialRecordTable.id].value, UUID.fromString(r[FinancialRecordTable.uuid]), r[FinancialRecordTable.projectId].value, FinancialRecordType.valueOf(r[FinancialRecordTable.recordType].uppercase()), r[FinancialRecordTable.referenceNumber], r[FinancialRecordTable.amount], r[FinancialRecordTable.currency], r[FinancialRecordTable.recordDate], r[FinancialRecordTable.paymentDate], r[FinancialRecordTable.description], r[FinancialRecordTable.milestone], r[FinancialRecordTable.paymentPurpose], r[FinancialRecordTable.eurExchangeRate]?.toDouble(), r[FinancialRecordTable.eurExchangeDate], r[FinancialRecordTable.amountEurCents])
}
