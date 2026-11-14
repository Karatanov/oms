package oms.urp3.service

import kotlin.math.roundToLong

fun main(args: Array<String>) {
    val source = PaymentMonitoringWorkbookSource.parseWorkbookData(java.io.File(args.single()).readBytes())
    val rows = source.transactions.map {
        DashboardPaymentAmount(it.paymentDate, it.amountEur?.let { eur -> (eur * 100).roundToLong() },
            (it.amountUah * 100).roundToLong(), it.batchId)
    }
    val monthly = dashboardMonthlyPayments(rows)
    check(rows.size == 96)
    check(monthly.sumOf { it.amountUahCents!! } == 31_307_733_460L)
    check(monthly.sumOf { it.amountEurCents } == rows.sumOf { it.eurCents!! })
    check(monthly.first().month == "2025-12" && monthly.last().month == "2026-11")
    check(monthly.single { it.month == "2026-01" }.amountUahCents == 0L)
    check(dashboardMonthlyPayments(rows, 2).isEmpty())
    println("96 source rows reconciled to 313077334.60 UAH; January zero and empty tranche B verified")
}
