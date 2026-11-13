package oms.urp3

import kotlin.test.Test
import kotlin.test.assertEquals

class ConstructionTypeClassificationTest {
    @Test fun restorationUsesTheApprovedReconstructionCategory() {
        for (label in listOf("Restoration", "Реставрація", "Restoration/ Реставрація", "restoration")) {
            assertEquals("reconstruction", financialConstructionKey(label))
            assertEquals(financialConstructionKey("Renovation"), financialConstructionKey(label))
        }
    }
}
