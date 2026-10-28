package oms.urp3.repository

import oms.urp3.database.tables.PaymentMonitoringRowTable
import oms.urp3.database.tables.PaymentMonitoringTransactionTable
import oms.urp3.service.PaymentMonitoringWorkbookSource
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentMonitoringSnapshot(
    val updatedAt: LocalDateTime,
    val items: List<PaymentMonitoringWorkbookSource.Item>,
    val transactions: List<PaymentMonitoringWorkbookSource.Transaction> = emptyList()
)

interface PaymentMonitoringRepository {
    fun findSnapshot(): PaymentMonitoringSnapshot?
    fun replaceAll(items: List<PaymentMonitoringWorkbookSource.Item>, transactions: List<PaymentMonitoringWorkbookSource.Transaction>, updatedAt: LocalDateTime): PaymentMonitoringSnapshot
}

class ExposedPaymentMonitoringRepository : PaymentMonitoringRepository {
    override fun findSnapshot(): PaymentMonitoringSnapshot? = transaction {
        val rows = PaymentMonitoringRowTable.selectAll().map(::map)
        val transactions = PaymentMonitoringTransactionTable.selectAll().map(::mapTransaction)
        rows.takeIf { it.isNotEmpty() }?.let { mapped ->
            PaymentMonitoringSnapshot(mapped.maxOf { it.second }, mapped.map { it.first }, transactions)
        }
    }

    override fun replaceAll(items: List<PaymentMonitoringWorkbookSource.Item>, transactions: List<PaymentMonitoringWorkbookSource.Transaction>, updatedAt: LocalDateTime): PaymentMonitoringSnapshot = transaction {
        require(items.isNotEmpty()) { "The payment monitoring workbook contains no usable rows." }
        require(items.map { it.code.uppercase() }.toSet().size == items.size) { "The payment monitoring workbook contains duplicate IDs." }
        PaymentMonitoringRowTable.deleteAll()
        PaymentMonitoringTransactionTable.deleteAll()
        // The workbook replaces the complete read-only snapshot. Use a batch
        // rather than one INSERT per row so a remote database pays one round
        // trip for the replacement transaction instead of hundreds.
        PaymentMonitoringRowTable.batchInsert(items, shouldReturnGeneratedValues = false) { item ->
            applyItem(item, updatedAt)
        }
        if (transactions.isNotEmpty()) PaymentMonitoringTransactionTable.batchInsert(transactions, shouldReturnGeneratedValues = false) { item ->
            this[PaymentMonitoringTransactionTable.sourceReference] = item.sourceReference
            this[PaymentMonitoringTransactionTable.sourceCode] = item.code
            this[PaymentMonitoringTransactionTable.batchId] = item.batchId
            this[PaymentMonitoringTransactionTable.paymentDate] = item.paymentDate
            this[PaymentMonitoringTransactionTable.amountUah] = BigDecimal.valueOf(item.amountUah)
            this[PaymentMonitoringTransactionTable.amountEur] = item.amountEur?.let(BigDecimal::valueOf)
            this[PaymentMonitoringTransactionTable.sourceSheet] = item.sourceSheet
            this[PaymentMonitoringTransactionTable.updatedAt] = updatedAt
        }
        PaymentMonitoringSnapshot(updatedAt, items, transactions)
    }

    private fun UpdateBuilder<*>.applyItem(item: PaymentMonitoringWorkbookSource.Item, updatedAt: LocalDateTime) {
        this[PaymentMonitoringRowTable.sourceCode] = item.code
        this[PaymentMonitoringRowTable.totalInvestmentUah] = item.totalInvestmentUah?.let(BigDecimal::valueOf)
        this[PaymentMonitoringRowTable.eibInvestmentUah] = item.eibInvestmentUah?.let(BigDecimal::valueOf)
        this[PaymentMonitoringRowTable.localCoFinancingUah] = item.localCoFinancingUah?.let(BigDecimal::valueOf)
        this[PaymentMonitoringRowTable.paidAndInProgressUah] = item.paidAndInProgressUah?.let(BigDecimal::valueOf)
        this[PaymentMonitoringRowTable.paidUah] = item.paidUah?.let(BigDecimal::valueOf)
        this[PaymentMonitoringRowTable.remainingUah] = item.remainingUah?.let(BigDecimal::valueOf)
        this[PaymentMonitoringRowTable.plannedEibSharePct] = item.plannedEibSharePct?.let(BigDecimal::valueOf)
        this[PaymentMonitoringRowTable.factualEibSharePct] = item.factualEibSharePct?.let(BigDecimal::valueOf)
        this[PaymentMonitoringRowTable.eibShareCompliance] = item.eibShareCompliance
        this[PaymentMonitoringRowTable.updatedAt] = updatedAt
    }

    private fun map(row: ResultRow): Pair<PaymentMonitoringWorkbookSource.Item, LocalDateTime> =
        PaymentMonitoringWorkbookSource.Item(
            code = row[PaymentMonitoringRowTable.sourceCode],
            totalInvestmentUah = row[PaymentMonitoringRowTable.totalInvestmentUah]?.toDouble(),
            eibInvestmentUah = row[PaymentMonitoringRowTable.eibInvestmentUah]?.toDouble(),
            localCoFinancingUah = row[PaymentMonitoringRowTable.localCoFinancingUah]?.toDouble(),
            paidAndInProgressUah = row[PaymentMonitoringRowTable.paidAndInProgressUah]?.toDouble(),
            paidUah = row[PaymentMonitoringRowTable.paidUah]?.toDouble(),
            remainingUah = row[PaymentMonitoringRowTable.remainingUah]?.toDouble(),
            plannedEibSharePct = row[PaymentMonitoringRowTable.plannedEibSharePct]?.toDouble(),
            factualEibSharePct = row[PaymentMonitoringRowTable.factualEibSharePct]?.toDouble(),
            eibShareCompliance = row[PaymentMonitoringRowTable.eibShareCompliance]
        ) to row[PaymentMonitoringRowTable.updatedAt]

    private fun mapTransaction(row: ResultRow) = PaymentMonitoringWorkbookSource.Transaction(
        sourceReference = row[PaymentMonitoringTransactionTable.sourceReference],
        code = row[PaymentMonitoringTransactionTable.sourceCode],
        batchId = row[PaymentMonitoringTransactionTable.batchId],
        paymentDate = row[PaymentMonitoringTransactionTable.paymentDate],
        amountUah = row[PaymentMonitoringTransactionTable.amountUah].toDouble(),
        amountEur = row[PaymentMonitoringTransactionTable.amountEur]?.toDouble(),
        sourceSheet = row[PaymentMonitoringTransactionTable.sourceSheet]
    )
}
