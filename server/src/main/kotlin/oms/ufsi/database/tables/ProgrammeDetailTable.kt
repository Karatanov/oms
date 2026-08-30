package oms.ufsi.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date

object ProgrammeDetailTable : Table("programme_details") {
    val projectId = reference("project_id", ProjectTable, onDelete = ReferenceOption.CASCADE)
    val implementor = varchar("implementor", 500)
    val financingInstitution = varchar("financing_institution", 255)
    val financeContractNumber = varchar("finance_contract_number", 100)
    val serapisNumber = varchar("serapis_number", 100)
    val agreementDate = date("agreement_date")
    val loanAmount = decimal("loan_amount", 18, 2)
    val loanCurrency = varchar("loan_currency", 3)
    val sourceWorkbook = varchar("source_workbook", 500)
    val sourceSnapshotDate = date("source_snapshot_date").nullable()
    override val primaryKey = PrimaryKey(projectId)
}
