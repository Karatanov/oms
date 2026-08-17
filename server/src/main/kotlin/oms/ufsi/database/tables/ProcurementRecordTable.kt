package oms.ufsi.database.tables

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date

object ProcurementRecordTable : LongIdTable("procurement_records") {
    val recordNumber = integer("record_number")
    val batchId = integer("batch_id")
    val oblastName = varchar("oblast_name", 255)
    val oblastId = varchar("oblast_id", 32)
    val subProjectId = varchar("sub_project_id", 64)
    val subProjectLotId = varchar("sub_project_lot_id", 64)
    val purchaseStatus = varchar("purchase_status", 255)
    val tenderId = varchar("tender_id", 128).nullable()
    val prozorroTenderId = varchar("prozorro_tender_id", 512).nullable()
    val contractorNameUkr = varchar("contractor_name_ukr", 500).nullable()
    val contractorNameEng = varchar("contractor_name_eng", 500).nullable()
    val contractorId = varchar("contractor_id", 32).nullable()
    val contractDate = date("contract_date").nullable()
    val contractEndDate = date("contract_end_date").nullable()
    val contractDurationMonths = integer("contract_duration_months").nullable()
    val contractAmountUah = decimal("contract_amount_uah", 18, 2).nullable()
    val contractAmountEur = decimal("contract_amount_eur", 18, 4).nullable()
    val financingContractDifferencePct = decimal("financing_contract_difference_pct", 14, 10).nullable()
}
