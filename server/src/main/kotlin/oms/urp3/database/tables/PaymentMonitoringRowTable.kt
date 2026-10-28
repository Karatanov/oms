package oms.urp3.database.tables

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime

/** Persisted snapshot of the separate UR III payment-monitoring source. */
object PaymentMonitoringRowTable : LongIdTable("payment_monitoring_rows") {
    val sourceCode = varchar("source_code", 100).uniqueIndex()
    val totalInvestmentUah = decimal("total_investment_uah", 20, 2).nullable()
    val eibInvestmentUah = decimal("eib_investment_uah", 20, 2).nullable()
    val localCoFinancingUah = decimal("local_co_financing_uah", 20, 2).nullable()
    val paidAndInProgressUah = decimal("paid_and_in_progress_uah", 20, 2).nullable()
    val paidUah = decimal("paid_uah", 20, 2).nullable()
    val remainingUah = decimal("remaining_uah", 20, 2).nullable()
    val plannedEibSharePct = decimal("planned_eib_share_pct", 8, 2).nullable()
    val factualEibSharePct = decimal("factual_eib_share_pct", 8, 2).nullable()
    val eibShareCompliance = varchar("eib_share_compliance", 255).nullable()
    val updatedAt = datetime("updated_at")
}

/** Dated source rows used by payment charts and the read-only detail table. */
object PaymentMonitoringTransactionTable : LongIdTable("payment_monitoring_transactions") {
    val sourceReference = varchar("source_reference", 180).uniqueIndex()
    val sourceCode = varchar("source_code", 100)
    val batchId = integer("batch_id").nullable()
    val paymentDate = date("payment_date")
    val amountUah = decimal("amount_uah", 20, 2)
    val amountEur = decimal("amount_eur", 20, 2).nullable()
    val sourceSheet = varchar("source_sheet", 180)
    val updatedAt = datetime("updated_at")
}
