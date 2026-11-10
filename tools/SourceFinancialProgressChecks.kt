package oms.urp3.service

import java.time.LocalDate
import kotlin.math.abs

fun main(args: Array<String>) {
    val transactions = PaymentMonitoringWorkbookSource.parseWorkbookData(java.io.File(args.single()).readBytes()).transactions
    val ids = transactions.map { it.code.paymentMonitoringSubprojectCode().uppercase() }.distinct()
        .mapIndexed { index, code -> code to index.toLong() + 1 }.toMap()
    fun noLegacy(): Map<Long, Double> = error("Unexpected legacy read")
    val totals = financialProgressPayments(transactions, ids, null, ::noLegacy)
    check(abs(totals.values.sum() - 307_988_578.28) < 0.001)
    check(financialProgressPayments(emptyList(), ids, null, ::noLegacy).isEmpty())
    check(financialProgressPayments(transactions, ids, 2000, ::noLegacy).isEmpty())
    check(financialProgressPayments(transactions, emptyMap(), null, ::noLegacy).isEmpty())
    check(financialProgressPayments(null, ids, null) { mapOf(1L to 12.5) } == mapOf(1L to 12.5))
    val lots = listOf("KH08_07#Lot1_W" to 10.25, "KH08_07#Lot2_W" to 20.5, "KH08_07_TS" to 300.0,
        "KH08_07_CSC" to 400.0).map { (code, value) ->
        PaymentMonitoringWorkbookSource.Transaction(code, code, 8, LocalDate.of(2026, 2, 1), value, null, "test")
    }
    check(financialProgressPayments(lots, mapOf("KH08_07" to 1L), 2026, ::noLegacy) == mapOf(1L to 30.75))
    println("Works payments reconciled: 307988578.28 UAH; empty snapshot, year and scope cannot revive legacy records; lot/service checks passed")
}
