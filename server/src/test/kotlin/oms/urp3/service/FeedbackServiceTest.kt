package oms.urp3.service

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class FeedbackServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-26T22:30:00Z"), ZoneId.of("Europe/Kyiv"))
    private fun request(id: String = "0123456789abcdef") = FeedbackRequest(id, "Projects", "Correction", "=1+1")
    private class FakeSheet : FeedbackSheetGateway {
        override val configured = true
        val rows = mutableListOf<JsonObject>()
        var loseResponse = false
        override fun readIdentifiers() = rows.map {
            val first = it["values"]!!.jsonArray[0].jsonObject
            FeedbackIdentifier(first["userEnteredValue"]!!.jsonObject["numberValue"]!!.jsonPrimitive.int,
                first["note"]!!.jsonPrimitive.content)
        }
        override fun append(row: JsonObject) {
            rows.add(row)
            if (loseResponse) { loseResponse = false; error("Response lost after successful write") }
        }
    }

    @Test fun `date uses Kyiv calendar and description is literal text`() = runBlocking {
        val sheet = FakeSheet()
        val service = FeedbackService(sheet, clock)
        assertEquals("2026-10-27", service.availability().date)
        assertEquals(FeedbackReceipt(1, "2026-10-27"), service.submit(request(), 7))
        val cells = sheet.rows.single()["values"]!!.jsonArray
        assertEquals(11, cells.size)
        assertEquals("=1+1", cells[4].jsonObject["userEnteredValue"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content)
        assertEquals("New", cells[8].jsonObject["userEnteredValue"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content)
        assertTrue(cells[2].jsonObject["userEnteredValue"]!!.jsonObject["numberValue"]!!.jsonPrimitive.long > 46000)
        assertEquals("", cells[6].jsonObject["userEnteredValue"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content)
    }

    @Test fun `retry after lost response and server restart does not append twice`() = runBlocking {
        val sheet = FakeSheet().apply { loseResponse = true }
        assertFailsWith<IllegalStateException> { FeedbackService(sheet, clock).submit(request(), 7) }
        val receipt = FeedbackService(sheet, clock).submit(request(), 7)
        assertEquals(1, receipt.number)
        assertEquals(1, sheet.rows.size)
    }

    @Test fun `concurrent submissions receive different numbers`() = runBlocking {
        val sheet = FakeSheet()
        val service = FeedbackService(sheet, clock)
        val receipts = (1..10).map { async { service.submit(request("0123456789abcdef-$it"), 7) } }.awaitAll()
        assertEquals((1..10).toSet(), receipts.map { it.number }.toSet())
    }

    @Test fun `invalid input never reaches sheet`() = runBlocking {
        val sheet = FakeSheet()
        val service = FeedbackService(sheet, clock)
        listOf(request().copy(description = "  "), request().copy(description = "a".repeat(5001)),
            request().copy(page = "Unknown"), request().copy(type = "Invalid"), request().copy(requestId = "bad"))
            .forEach { assertFailsWith<IllegalArgumentException> { service.submit(it, 7) } }
        assertTrue(sheet.rows.isEmpty())
    }
}
