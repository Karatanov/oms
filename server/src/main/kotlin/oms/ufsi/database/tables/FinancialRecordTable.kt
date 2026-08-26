package oms.ufsi.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date

object FinancialRecordTable : LongIdTable("financial_records") {
    val uuid = varchar("uuid", 36).uniqueIndex()
    val projectId = reference("project_id", ProjectTable, onDelete = ReferenceOption.RESTRICT)
    val recordType = varchar("record_type", 20)
    val referenceNumber = varchar("reference_number", 100)
    val amount = long("amount")
    val currency = varchar("currency", 3)
    val recordDate = date("record_date")
    val paymentDate = date("payment_date").nullable()
    val description = text("description").nullable()
    val milestone = varchar("milestone", 255).nullable()
    /** Business purpose of a payment: works or equipment. */
    val paymentPurpose = varchar("payment_purpose", 20).default("works")
    val createdBy = reference("created_by", UserTable, onDelete = ReferenceOption.RESTRICT)
}
