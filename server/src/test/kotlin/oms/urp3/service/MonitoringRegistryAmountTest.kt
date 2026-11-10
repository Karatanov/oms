package oms.urp3.service

import org.apache.poi.ss.usermodel.DataFormatter
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class MonitoringRegistryAmountTest {
    // The workbook reader is deliberately file-private; exercise it without exposing it as an API.
    private fun number(value: String, key: String = "fundingTotal"): Double {
        val type = Class.forName("oms.urp3.service.MonitoringWorkbookSource\$Row")
        val constructor = type.getDeclaredConstructor(String::class.java, String::class.java, Map::class.java,
            String::class.java, String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val row = constructor.newInstance("VN08_01", "VN08_01",
            mapOf("fundingTotal" to value, "contractedTotal" to value), "16", "test.xlsx", false)
        return type.getDeclaredMethod("number", String::class.java).apply { isAccessible = true }
            .invoke(row, key) as Double
    }

    @Test fun `Excel grouped amounts survive the actual registry row parser`() {
        val formatted = DataFormatter(Locale.US).formatRawCellContents(1_234_567.89, 4, "#,##0.00")
        assertEquals("1,234,567.89", formatted)
        assertEquals(1_234_567.89, number(formatted))
        assertEquals(1_234_567.89, number(formatted, "contractedTotal"))
        assertEquals(6_484_316.0, number("6,484,316"))
    }

    @Test fun `decimal comma and spaced amounts remain supported`() {
        listOf("1234567,89", "1 234 567,89", "1\u00a0234\u00a0567,89")
            .forEach { assertEquals(1_234_567.89, number(it)) }
        listOf("0", "", "#VALUE!", "NaN", "Infinity")
            .forEach { assertEquals(0.0, number(it)) }
    }
}
