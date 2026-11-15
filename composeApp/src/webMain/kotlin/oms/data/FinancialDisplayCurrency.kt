package oms.data

import oms.urp3.decimalMoneyCents

/** Frozen EUR values take precedence. Legacy EUR rate=1 is not a UAH/EUR rate. */
fun ApiFinancialRecord.displayAmountCents(target: String, historicalRate: Double? = null): Long? {
    if (!amount.isFinite() || target !in setOf("EUR", "UAH")) return null
    if (currency == target) return decimalMoneyCents(amount)
    if (target == "EUR" && amountEurCents != null) return amountEurCents
    // EUR records created before frozen NBU rates stored 1.0 as a sentinel.
    // Keep treating that legacy value as unknown, but prefer a genuine frozen
    // EUR/UAH rate for newer EUR records before falling back to the NBU lookup.
    val rate = eurExchangeRate?.takeIf {
        it.isFinite() && it > 0 && (currency == "UAH" || it != 1.0)
    }
        ?: historicalRate?.takeIf { it.isFinite() && it > 0 }
        ?: return null
    return when {
        currency == "UAH" && target == "EUR" -> decimalMoneyCents(amount, rate, divideByRate = true)
        currency == "EUR" && target == "UAH" -> decimalMoneyCents(amount, rate)
        else -> null
    }
}
