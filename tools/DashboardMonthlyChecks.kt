package oms.urp3.service

import java.time.LocalDate

fun main() {
    fun row(date: String, eur: Long?, uah: Long?, batch: Int = 8) = DashboardPaymentAmount(LocalDate.parse(date), eur, uah, batch)
    val rows = listOf(row("2026-02-03", 200, 10000), row("2025-12-31", 100, 5000))
    val result = dashboardMonthlyPayments(rows)
    check(result.map { it.month } == listOf("2025-12", "2026-01", "2026-02"))
    check(result[1].amountUahCents == 0L)
    check(result.sumOf { it.amountEurCents } == 300L)
    check(result.sumOf { it.amountUahCents!! } == 15000L)
    check(dashboardMonthlyPayments(rows, 2).isEmpty())
    check(dashboardMonthlyPayments(rows, 8) == dashboardMonthlyPayments(rows, 1))
    val incomplete = dashboardMonthlyPayments(listOf(row("2026-02-01", null, 5000), row("2026-02-02", 200, 10000))).single()
    check(incomplete.amountUahCents == 15000L && !incomplete.hasCompleteEurAmounts)
    check(dashboardMonthlyPayments(listOf(row("2026-02-01", 200, null))).single().amountUahCents == null)
    println("Monthly payment totals, gap months, tranche scopes and missing conversions verified")
}
