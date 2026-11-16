package oms.urp3.service

import oms.urp3.domain.ProjectAmount
import java.math.BigDecimal
import java.math.RoundingMode

/** Parse the source display value without passing financial cents through Double. */
internal fun monitoringBudgetAmount(raw: String): BigDecimal? {
    val compact = raw.replace(Regex("\\s|\u00a0"), "")
    val normalized = when {
        ',' in compact && '.' in compact -> compact.replace(",", "")
        compact.matches(Regex("-?\\d{1,3}(,\\d{3})+")) -> compact.replace(",", "")
        else -> compact.replace(',', '.')
    }
    return normalized.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 && it < MAX_BUDGET }
        ?.setScale(2, RoundingMode.HALF_UP)?.takeIf { it < MAX_BUDGET }
}

private val MAX_BUDGET = BigDecimal("10000000000000000")

/**
 * A source refresh owns UAH funding, not manually entered conversions or EUR
 * originals. Reuse only a coherent frozen conversion; never fetch today's rate.
 */
internal fun refreshedMonitoringBudget(raw: String, saved: ProjectAmount): ProjectAmount? {
    val amount = monitoringBudgetAmount(raw) ?: return null
    if (saved.currency != "UAH" || saved.conversionEdited || saved.uahPerEur.signum() <= 0) return null
    val oldEquivalent = saved.amount.divide(saved.uahPerEur, 2, RoundingMode.HALF_UP)
    if ((oldEquivalent - saved.convertedAmount).abs() > BigDecimal("0.01")) return null
    if (amount.compareTo(saved.amount) == 0) return null
    val equivalent = amount.divide(saved.uahPerEur, 2, RoundingMode.HALF_UP)
    if (equivalent >= MAX_BUDGET) return null
    return saved.copy(amount = amount, convertedAmount = equivalent)
}
