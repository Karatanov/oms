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
    oms.urp3.decimalMoneyCents(amount, rate)

/** Sorting, PDF and dashboard must follow the client's frozen-value policy, never a live rate. */
internal fun financialDisplayCents(record: FinancialRecord, targetCurrency: String): Long? =
    if (!record.amount.isFinite()) null else frozenFinancialCents(
        BigDecimal.valueOf(record.amount), record.currency, targetCurrency, record.amountEurCents,
        record.eurExchangeRate?.takeIf(Double::isFinite)?.let(BigDecimal::valueOf)
    )

/** Accept database decimals directly so dashboard aggregation does not lose precision via Double. */
internal fun frozenFinancialCents(
    amount: BigDecimal,
    currency: String,
    targetCurrency: String,
    savedEurCents: Long?,
    eurRate: BigDecimal?
): Long? {
    val source = currency.trim().uppercase()
    val target = targetCurrency.trim().uppercase()
    if (target !in setOf("EUR", "UAH")) return null
    if (source == target) return moneyCents(amount)
    if (target == "EUR" && savedEurCents != null) return savedEurCents
    val rate = eurRate?.takeIf { it.signum() > 0 && (source != "EUR" || it.compareTo(BigDecimal.ONE) != 0) }
        ?: return null
    return when {
        source == "EUR" && target == "UAH" -> moneyCents(amount, rate)
        source == "UAH" && target == "EUR" -> try {
            amount.divide(rate, 2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (_: ArithmeticException) { null }
        else -> null
    }
}
