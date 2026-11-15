package oms.screens

import oms.data.ApiFinancialRecord

fun main() {
    fun row(id: String, purpose: String, date: String, rate: Double?) = FinancialChartRecord(
        ApiFinancialRecord(id, "payment", id, 100.0, "EUR", date,
            paymentPurpose = purpose, eurExchangeRate = rate, amountEurCents = 10000), id)
    val works = row("W", "works", "2026-04-24", 50.0)
    val supervision = row("TS", "technical_supervision", "2026-04-24", 50.0)
    check(aggregateMonthlyPayments(listOf(works, supervision), "works", "UAH").payments.single().amountCents == 500000L)
    check(aggregateMonthlyPayments(listOf(works, supervision), "technical_supervision", "UAH").payments.single().amountCents == 500000L)
    check(aggregateMonthlyPayments(listOf(works, supervision), "engineer_consultant", "UAH").payments.isEmpty())
    val months = aggregateMonthlyPayments(listOf(works, works.copy(record = works.record.copy(recordDate = "2026-06-24"))), "works", "UAH").payments
    check(months.map { it.month } == listOf("2026-04", "2026-05", "2026-06"))
    check(months[1].amountCents == 0L)
    val unknown = row("unknown", "works", "2026-04-24", null)
    val incomplete = aggregateMonthlyPayments(listOf(works, unknown), "works", "UAH")
    check(incomplete.payments.isEmpty())
    check(incomplete.unavailableRecords == 1)
    check(incomplete.tooltipByMonth.isEmpty())
    println("Works/TS/CSC separation and genuine gap zero passed")
    println("Missing conversion is explicit: no partial bar, tooltip or fake gap zero")
}
