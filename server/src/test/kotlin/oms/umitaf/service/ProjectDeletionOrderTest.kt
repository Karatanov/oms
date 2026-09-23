package oms.umitaf.service

import oms.umitaf.repository.projectDeletionOrder
import kotlin.test.*

class ProjectDeletionOrderTest {
    @Test fun `children precede parents and unrelated projects are excluded`() {
        assertEquals(listOf(4L, 2L, 3L, 1L), projectDeletionOrder(1,
            mapOf(1L to listOf(2L, 3L), 2L to listOf(4L), 9L to listOf(10L))))
    }
    @Test fun `cycle fails before an order is returned`() {
        assertFailsWith<IllegalStateException> {
            projectDeletionOrder(1, mapOf(1L to listOf(2L), 2L to listOf(1L)))
        }
    }
    @Test fun `deep synthetic hierarchy does not overflow the stack`() {
        val children: Map<Long?, List<Long>> = (1L until 20_000L).associate { it to listOf(it + 1) }
        val order = projectDeletionOrder(1, children)
        assertEquals(20_000, order.size)
        assertEquals(20_000L, order.first())
        assertEquals(1L, order.last())
    }
}
