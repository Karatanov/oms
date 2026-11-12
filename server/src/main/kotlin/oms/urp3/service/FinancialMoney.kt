package oms.urp3.service

import oms.urp3.domain.FinancialRecord
import java.math.BigDecimal
import java.math.RoundingMode

/** Round once in decimal arithmetic, matching saved NBU EUR conversions. Unknown/overflow is not zero. */
internal fun moneyCents(amount: BigDecimal, rate: BigDecimal = BigDecimal.ONE): Long? {
    if (rate.signum() <= 0) return null
    return try {
        amount.multiply(rate).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    } catch (_: ArithmeticException) {
        null
    }
}

internal fun moneyCents(amount: Double, rate: Double = 1.0): Long? =
    if (!amount.isFinite() || !rate.isFinite()) null else moneyCents(BigDecimal.valueOf(amount), BigDecimal.valueOf(rate))

/** Sorting and PDF cells must use the same saved conversion and missing-rate policy. */
internal fun financialDisplayCents(record: FinancialRecord, targetCurrency: String): Long? {
    if (!record.amount.isFinite()) return null
    val target = targetCurrency.uppercase()
    if (record.currency.equals(target, ignoreCase = true)) return moneyCents(record.amount)
    if (target == "EUR") return record.amountEurCents
    val rate = record.eurExchangeRate?.takeIf { it.isFinite() && it > 0 && it != 1.0 } ?: return null
    return if (target == "UAH" && record.currency.equals("EUR", true)) moneyCents(record.amount, rate) else null
}
