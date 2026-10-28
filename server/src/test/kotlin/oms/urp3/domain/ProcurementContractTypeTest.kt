package oms.urp3.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ProcurementContractTypeTest {
    @Test
    fun `source identifier wins over an incorrect spreadsheet type cell`() {
        assertEquals(
            ProcurementContractType.TECHNICAL_SUPERVISION,
            ProcurementContractType.resolve("DP08_01_TS\$1", "W")
        )
    }

    @Test
    fun `known type suffixes resolve to their approved contract classes`() {
        assertEquals(ProcurementContractType.WORKS, ProcurementContractType.resolve("VN08_01_W\$1", null))
        assertEquals(ProcurementContractType.TECHNICAL_SUPERVISION, ProcurementContractType.resolve("VN08_01_TS\$1", null))
        assertEquals(ProcurementContractType.ENGINEER_CONSULTANT, ProcurementContractType.resolve("VN08_01_CSC\$1", null))
    }
}
