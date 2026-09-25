package oms.data

import kotlin.math.roundToLong

/** Frozen EUR values take precedence. Legacy EUR rate=1 is not a UAH/EUR rate. */
fun ApiFinancialRecord.displayAmountCents(target: String, historicalRate: Double? = null): Long? {
    if (!amount.isFinite() || target !in setOf("EUR", "UAH")) return null
    if (currency == target) return (amount * 100).roundToLong()
    if (target == "EUR" && amountEurCents != null) return amountEurCents
    val rate = eurExchangeRate?.takeIf { currency == "UAH" && it.isFinite() && it > 0 }
        ?: historicalRate?.takeIf { it.isFinite() && it > 0 }
        ?: return null
    return when {
        currency == "UAH" && target == "EUR" -> (amount * 100 / rate).roundToLong()
        currency == "EUR" && target == "UAH" -> (amount * 100 * rate).roundToLong()
        else -> null
    }
}
