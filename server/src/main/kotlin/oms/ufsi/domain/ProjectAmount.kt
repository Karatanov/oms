package oms.ufsi.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class ProjectAmount(
    val amount: BigDecimal,
    val currency: String,
    val convertedAmount: BigDecimal,
    val uahPerEur: BigDecimal,
    val rateDate: LocalDate,
    val conversionEdited: Boolean
) {
    val uah: BigDecimal get() = if (currency == "UAH") amount else convertedAmount
    fun legacyUah(): Long = uah.setScale(0, RoundingMode.HALF_UP).longValueExact()
}
