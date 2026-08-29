package oms.ufsi.service

import oms.ufsi.domain.ProjectAmount
import oms.ufsi.dto.ProjectAmountDto
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

val projectAmountKinds = setOf("budget", "engineer", "supervision", "construction")

fun validateProjectAmounts(values: Map<String, ProjectAmountDto>): Map<String, ProjectAmount> {
    require(values.keys.all { it in projectAmountKinds }) { "Unknown project amount kind." }
    return values.mapValues { (kind, dto) ->
        fun decimal(value: String, scale: Int): BigDecimal {
            require(value.matches(Regex("[0-9]+([.,][0-9]{1,$scale})?"))) { "Invalid $kind monetary value." }
            val number = value.replace(',', '.').toBigDecimal().setScale(scale)
            require(number.precision() <= 18) { "$kind monetary value is too large." }
            return number
        }
        val amount = decimal(dto.amount, 2)
        val converted = decimal(dto.convertedAmount, 2)
        val rate = decimal(dto.uahPerEur, 8)
        require(dto.currency in setOf("EUR", "UAH")) { "Project amounts support EUR and UAH only." }
        require(rate > BigDecimal.ZERO) { "Exchange rate must be positive." }
        require(kind != "budget" || (amount > BigDecimal.ZERO && converted > BigDecimal.ZERO)) { "Budget must be positive." }
        val date = runCatching { LocalDate.parse(dto.rateDate) }.getOrElse { throw IllegalArgumentException("Invalid exchange rate date.") }
        require(!date.isAfter(LocalDate.now(ZoneId.of("Europe/Kyiv")))) { "Exchange rate date cannot be in the future." }
        val canonicalConverted = if (!dto.conversionEdited) {
            val expected = if (dto.currency == "EUR") amount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                else amount.divide(rate, 2, RoundingMode.HALF_UP)
            require(expected.subtract(converted).abs() <= BigDecimal("0.01")) { "Converted $kind amount does not match the exchange rate." }
            expected
        } else converted
        ProjectAmount(amount, dto.currency, canonicalConverted, rate, date, dto.conversionEdited)
    }
}
