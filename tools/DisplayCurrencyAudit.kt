package oms.urp3.service

import oms.data.ApiFinancialRecord
import oms.data.displayAmountCents
import java.math.BigDecimal

/** Reconcile the actual frontend conversion against independent JVM decimal arithmetic. */
fun main() {
    fun record(amount: Double, currency: String, rate: Double? = null, eur: Long? = null) =
        ApiFinancialRecord("audit", "payment", "audit", amount, currency, "2026-04-24",
            eurExchangeRate = rate, amountEurCents = eur)
    var compared = 0
    val mismatches = mutableListOf<String>()
    for (cents in 1..1000) {
        val amount = cents/100.0
        val expected = moneyCents(BigDecimal.valueOf(amount), BigDecimal("48.5"))
        val actual = record(amount, "EUR", 48.5).displayAmountCents("UAH")
        compared++
        if (actual != expected) mismatches += "$amount EUR: client=$actual UAH cents, decimal=$expected"
    }
    check(mismatches.isEmpty()) { mismatches.take(5).joinToString("\n") }
    println("Compared $compared valid two-decimal EUR inputs at a synthetic rate 48.5; ${mismatches.size} one-cent discrepancies")
    mismatches.take(5).forEach(::println)
    val zeroFrozen = record(100.0, "UAH", 50.0, 0L).displayAmountCents("EUR")
    check(zeroFrozen == 0L) { "Saved zero conversion must remain authoritative" }
    check(record(100.0, "EUR", 1.0).displayAmountCents("UAH") == null)
    check(record(100.0, "EUR", null).displayAmountCents("UAH") == null)
    check(record(100.0, "UAH", 50.0).displayAmountCents("EUR") == 200L)
    check(record(100.0, "UAH", 50.0, 123L).displayAmountCents("EUR") == 123L)
    println("Missing/sentinel rates, frozen EUR precedence and genuine zero checks passed")
}
