package oms.umitaf.service

import java.lang.reflect.Proxy
import oms.umitaf.repository.InspectionFindingRepository
import oms.umitaf.domain.FindingSeverity
import kotlin.test.*

class InspectionFindingReplacementTest {
    private val calls = mutableListOf<List<Any?>>()
    private val repository = Proxy.newProxyInstance(
        InspectionFindingRepository::class.java.classLoader,
        arrayOf(InspectionFindingRepository::class.java)
    ) { _, method, args ->
        check(method.name == "replaceCategory") { "Unexpected repository operation: ${method.name}" }
        calls.add(args!!.toList())
        null
    } as InspectionFindingRepository

    @Test fun `invalid later entry prevents all writes`() {
        assertFailsWith<IllegalArgumentException> {
            InspectionFindingService(repository).replaceCategory(7, "hse_sir_auto", "medium",
                listOf("Valid" to null, " " to null))
        }
        assertTrue(calls.isEmpty())
    }

    @Test fun `complete validated replacement is one repository operation`() {
        InspectionFindingService(repository).replaceCategory(7, " hse_sir_auto ", "medium",
            listOf(" Issue " to " Remedy "))
        assertEquals(listOf(listOf(7L, "hse_sir_auto", FindingSeverity.MEDIUM,
            listOf("Issue" to "Remedy"))), calls)
    }

    @Test fun `empty replacement still clears automatic findings`() {
        InspectionFindingService(repository).replaceCategory(7, "hse_sir_auto", "medium", emptyList())
        assertEquals(1, calls.size)
        assertEquals(emptyList<Pair<String, String?>>(), calls.single()[3])
    }
}
