package oms.urp3

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcurementChartRulesTest {
    @Test
    fun constructionCountExcludesServiceContracts() {
        listOf("W", " Works ", "Роботи / Works", "Works / Роботи", "Будівельні роботи")
            .forEach { assertTrue(isWorksProcurementContract(it), it) }
        listOf(null, "", "TS", "Технічний нагляд / TS", "CSC", "Інженер-консультант / CSC")
            .forEach { assertFalse(isWorksProcurementContract(it), it) }
    }
}
