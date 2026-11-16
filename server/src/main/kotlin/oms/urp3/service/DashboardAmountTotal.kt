package oms.urp3.service

import java.math.BigDecimal

/** Add source decimals exactly; convert only the final total to the existing API type. */
internal fun dashboardAmountTotal(values: Sequence<BigDecimal>): Double =
    values.fold(BigDecimal.ZERO, BigDecimal::add).toDouble().also {
        require(it.isFinite()) { "Dashboard amount total exceeds the API numeric range." }
    }
