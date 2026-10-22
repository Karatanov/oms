package oms.urp3

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import oms.data.ProjectListPayload
import oms.data.ProjectPageMetadata
import oms.data.hasNextProjectPage

class ProjectPaginationTest {
    @Test
    fun `metadata prevents redundant request after exact full page`() {
        val payload = ProjectListPayload(
            data = List(500) { projectStub() },
            meta = ProjectPageMetadata(page = 1, perPage = 500, total = 500, totalPages = 1)
        )

        assertFalse(hasNextProjectPage(page = 1, payload, pageSize = 500))
    }

    @Test
    fun `metadata requests every remaining project page`() {
        val payload = ProjectListPayload(
            data = List(500) { projectStub() },
            meta = ProjectPageMetadata(page = 1, perPage = 500, total = 501, totalPages = 2)
        )

        assertTrue(hasNextProjectPage(page = 1, payload, pageSize = 500))
    }

    @Test
    fun `older API keeps size based pagination fallback`() {
        assertTrue(hasNextProjectPage(1, ProjectListPayload(List(500) { projectStub() }), 500))
        assertFalse(hasNextProjectPage(1, ProjectListPayload(List(499) { projectStub() }), 500))
    }

    private fun projectStub() = oms.data.ApiProject(
        uuid = "stub", name = "Stub", siteNumber = "STUB", region = "", city = "",
        sector = "", budgetPlanned = 0, status = "active", latitude = 0.0, longitude = 0.0
    )
}
