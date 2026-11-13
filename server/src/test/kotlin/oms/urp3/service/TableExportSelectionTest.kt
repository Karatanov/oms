package oms.urp3.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class TableExportSelectionTest {
    private data class Row(val id: Long)
    private val rows = listOf(Row(1), Row(2), Row(3))

    @Test fun omittedSelectionRetainsLegacyFullExport() {
        assertSame(rows, selectTableExportRows(rows, null) { it.id })
        assertNull(procurementExportIds(null, null))
        assertNull(procurementExportIds(null, "false"))
    }

    @Test fun explicitEmptySelectionNeverExpandsToTheRegister() {
        assertEquals(emptyList(), selectTableExportRows(rows, emptyList<Long>()) { it.id })
        assertEquals(emptyList(), procurementExportIds(null, "true"))
        assertEquals(emptyList(), procurementExportIds(emptyList(), null))
    }

    @Test fun selectionPreservesClientOrderAndDeduplicatesWithoutMutatingRows() {
        assertEquals(listOf(Row(3), Row(1)), selectTableExportRows(rows, listOf(3L, 1L, 3L)) { it.id })
        assertEquals(listOf(Row(1), Row(2), Row(3)), rows)
        assertEquals(listOf(3L, 1L), procurementExportIds(listOf("3", "1", "3"), null))
    }

    @Test fun missingOrDeletedKeysDoNotRestoreUnselectedRows() {
        assertEquals(emptyList(), selectTableExportRows(rows, listOf(99L)) { it.id })
        assertEquals(listOf(Row(2)), selectTableExportRows(rows, listOf(99L, 2L)) { it.id })
    }

    @Test fun malformedProcurementIdsAreRejectedEvenAlongsideValidIds() {
        for (bad in listOf("", "oops", "0", "-1", "1.5", "9223372036854775808")) {
            assertFailsWith<IllegalArgumentException>(bad) { procurementExportIds(listOf(bad), null) }
            assertFailsWith<IllegalArgumentException>(bad) { procurementExportIds(listOf("1", bad), null) }
        }
        assertFailsWith<IllegalArgumentException> { procurementExportIds(null, "unknown") }
    }

    @Test fun stringKeysUseTheSameExplicitSelectionSemantics() {
        val codes = listOf("KH08_01", "VN08_01")
        assertEquals(emptyList(), selectTableExportRows(codes, emptyList<String>()) { it })
        assertEquals(listOf("VN08_01"), selectTableExportRows(codes, listOf("VN08_01")) { it })
    }
}
