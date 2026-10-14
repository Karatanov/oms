package oms.umitaf

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import oms.model.Project
import oms.model.ProjectStatus
import oms.screens.matchesMapFilters

class MapFilterTest {
    private fun project(region: String, status: ProjectStatus) = Project(
        id = "$region-$status",
        projectType = "subproject",
        name = "Example $region",
        siteNumber = "CODE-$region",
        region = region,
        city = "City",
        sector = "education",
        budgetPlanned = 1L,
        status = status,
        latitude = 50.0,
        longitude = 30.0
    )

    @Test
    fun `multiple values within a category are alternatives`() {
        val kyivActive = project("Kyiv", ProjectStatus.ACTIVE)
        assertTrue(kyivActive.matchesMapFilters("", setOf("Kyiv", "Lviv"), emptySet()))
        assertFalse(kyivActive.matchesMapFilters("", setOf("Lviv", "Odesa"), emptySet()))
    }

    @Test
    fun `categories combine with and semantics`() {
        val kyivActive = project("Kyiv", ProjectStatus.ACTIVE)
        assertTrue(kyivActive.matchesMapFilters("", setOf("Kyiv", "Lviv"), setOf(ProjectStatus.PLANNED, ProjectStatus.ACTIVE)))
        assertFalse(kyivActive.matchesMapFilters("", setOf("Kyiv"), setOf(ProjectStatus.PLANNED)))
    }

    @Test
    fun `empty selection does not restrict map results`() {
        val kyivActive = project("Kyiv", ProjectStatus.ACTIVE)
        assertTrue(kyivActive.matchesMapFilters("", emptySet(), emptySet()))
    }
}
