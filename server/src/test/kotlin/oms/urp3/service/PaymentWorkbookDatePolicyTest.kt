package oms.urp3.service

import java.time.LocalDate
import kotlin.test.*

class PaymentWorkbookDatePolicyTest {
    @Test fun `status suffixes cannot turn a pending row into paid cash`() {
        for (raw in listOf("2026-11-03 Planned", "2026-11-03 Sent for payment", "2026-11-03cancelled",
            "2026-11-03 (not paid)", "2026-11-03T00:00:00 Planned", "Sent for payment 03.11.2026")) {
            assertNull(parsePaymentWorkbookDate(raw), raw)
        }
    }

    @Test fun `valid dates and complete timestamps retain their source calendar date`() {
        for (raw in listOf("2026-11-03", " 11/3/2026 ", "2026-11-03T00:00:00", "2026-11-03T00:00:00Z",
            "2026-11-03T00:30:00+02:00", "2026-11-03 00:00:00", "2026-11-03T12:34:56.123", "2026-11-03+02:00")) {
            assertEquals(LocalDate.of(2026, 11, 3), parsePaymentWorkbookDate(raw), raw)
        }
    }

    @Test fun `invalid times and incomplete timestamps cannot hide behind a valid date prefix`() {
        for (raw in listOf("2026-11-03T99:00:00", "2026-11-03T00:99:00", "2026-11-03T", "2026-11-03 garbage",
            "2026-02-29T00:00:00", "2026-11-03 12:34:56 trailing")) {
            assertNull(parsePaymentWorkbookDate(raw), raw)
        }
        assertEquals(LocalDate.of(2024, 2, 29), parsePaymentWorkbookDate("2024-02-29T23:59:59Z"))
    }
}
