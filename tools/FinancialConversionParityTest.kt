package oms.urp3.service

import java.time.LocalDate
import java.util.UUID
import kotlin.test.*
import oms.data.ApiFinancialRecord
import oms.data.displayAmountCents
import oms.urp3.domain.FinancialRecord
import oms.urp3.domain.FinancialRecordType

/** Run actual client/server conversion functions together, without an HTTP/NBU fallback. */
class FinancialConversionParityTest {
    @Test fun `frozen conversion agrees across client and server in both directions`() {
        for (currency in listOf("UAH", "EUR")) for (target in listOf("UAH", "EUR")) {
            for (amount in listOf(0.0, 0.84, 1.48, 100.0, -100.0, 1000000.01)) {
                for (rate in listOf(null, 0.0, 1.0, 48.125, Double.NaN)) for (saved in listOf(null, 0L, 123L)) {
                    val server = FinancialRecord(1, UUID.randomUUID(), 1, FinancialRecordType.PAYMENT, "test", amount,
                        currency, LocalDate.of(2026, 11, 15), null, null, null, "works", rate, null, saved)
                    val client = ApiFinancialRecord("test", "payment", "test", amount, currency, "2026-11-15",
                        eurExchangeRate = rate, amountEurCents = saved)
                    assertEquals(client.displayAmountCents(target), financialDisplayCents(server, target),
                        "$currency -> $target, amount=$amount, rate=$rate, saved=$saved")
                }
            }
        }
    }
}
