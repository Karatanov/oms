package oms.umitaf.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

data class EurConversion(val rate: Double, val effectiveDate: LocalDate, val amountEurCents: Long)

/**
 * Retrieves the official daily NBU EUR rate and freezes it with the record.
 * NBU may not publish a rate for weekends/holidays, so the latest prior
 * published business-day rate is used and its actual effective date is saved.
 */
class NbuExchangeRateService {
    private val rateCache = ConcurrentHashMap<LocalDate, Pair<LocalDate, Double>>()

    fun eurRate(date: LocalDate): Pair<LocalDate, Double> = rateCache.computeIfAbsent(date, ::loadRate)

    fun convertToEur(amount: Double, currency: String, actDate: LocalDate): EurConversion {
        if (currency == "EUR") return EurConversion(1.0, actDate, BigDecimal.valueOf(amount).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact())
        require(currency == "UAH") { "Only UAH and EUR financial records are currently supported." }
        val (effectiveDate, rate) = rateCache.computeIfAbsent(actDate, ::loadRate)
        val cents = BigDecimal.valueOf(amount)
            .movePointRight(2)
            .divide(BigDecimal.valueOf(rate), 0, RoundingMode.HALF_UP)
            .longValueExact()
        return EurConversion(rate, effectiveDate, cents)
    }

    private fun loadRate(requestedDate: LocalDate): Pair<LocalDate, Double> {
        for (offset in 0..7) {
            val date = requestedDate.minusDays(offset.toLong())
            val key = date.format(DateTimeFormatter.BASIC_ISO_DATE)
            val url = "https://bank.gov.ua/NBUStatService/v1/statdirectory/exchangeNew?json&valcode=EUR&date=$key"
            val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) continue
                val item = Json.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() })
                    .jsonArray.firstOrNull()?.jsonObject ?: continue
                val rate = item["rate"]?.jsonPrimitive?.doubleOrNull ?: continue
                val effective = item["exchangedate"]?.jsonPrimitive?.contentOrNull
                    ?.let { LocalDate.parse(it, DateTimeFormatter.ofPattern("dd.MM.yyyy")) } ?: date
                return effective to rate
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalArgumentException("The NBU EUR exchange rate is unavailable for $requestedDate and the previous 7 days. Please try again later.")
    }
}
