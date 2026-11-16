package oms.urp3.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.*
import oms.urp3.domain.*

class RegistryCurrencyFallbackTest {
    private fun amount(currency: String, original: String, converted: String, edited: Boolean = false) =
        ProjectAmount(BigDecimal(original), currency, BigDecimal(converted), BigDecimal("50"), LocalDate.of(2026, 1, 1), edited)
    private fun project(amounts: Map<String, ProjectAmount>) = Project(1, UUID.randomUUID(), ProjectType.SUBPROJECT, 1, null,
        "Test", "TEST", "TEST", null, null, null, null, null, null, ProjectStatus.ACTIVE,
        null, null, 5001, null, null, 1000, null, null, null, null, null, null, null, "UAH", null, amounts = amounts)
    private fun source(values: Map<String, String>) = ProjectMonitoringDetails(
        sourceBatchId = 8, sourceSubprojectId = "TEST", sourceLotId = "TEST", nameEn = null, oblastCode = null,
        municipalityNameUk = null, municipalityNameEn = null, settlementNameEn = null, priorityAreaSource = null,
        projectManagerNameUk = null, projectManagerNameEn = null, projectManagerOrgId = null,
        beneficiaryNameUk = null, beneficiaryNameEn = null, beneficiaryOrgId = null, dreamProjectId = null,
        dreamProjectUrl = null, applicationId = null, dreamApplicationId = null, constructionProcurementStatus = null,
        constructionWorkStatus = null, geocodeAccuracy = null, geocodeQuery = null, geocodeDisplayName = null,
        sourceRows = "16", sourceWorkbook = "test.xlsx", registryValues = values)

    @Test fun `every currency labelled registry fallback uses frozen UAH not raw EUR`() {
        val fields = mapOf("budget" to "fundingTotal", "planned_works" to "fundingWorks",
            "planned_supervision" to "fundingTs", "planned_engineer" to "fundingCsc",
            "eib_financing" to "eibCost", "local_financing" to "localCost", "contracted_eib" to "contractedEib",
            "contracted_local" to "contractedLocal", "construction_eib" to "constructionEib", "construction_local" to "constructionLocal")
        val original = fields.keys.associateWith { amount("EUR", "100.01", "5000.50") }
        val model = project(original)
        val response = model.toResponse()
        for (field in fields.values) assertEquals("5000.5", response.registry[field], field)
        assertEquals("100.01", response.amounts.getValue("budget").amount)
        assertEquals("EUR", response.amounts.getValue("budget").currency)
        assertEquals(original, model.amounts)
    }
    @Test fun `UAH source and genuine zero keep their exact amounts`() {
        val response = project(mapOf("budget" to amount("UAH", "1234.56", "24.69"),
            "eib_financing" to amount("UAH", "0.00", "0.00"))).toResponse()
        assertEquals("1234.56", response.registry["fundingTotal"])
        assertEquals("0", response.registry["eibCost"])
    }
    @Test fun `manual saved conversion is not recomputed from the rate`() {
        val response = project(mapOf("budget" to amount("EUR", "100", "4999.25", true))).toResponse()
        assertEquals("4999.25", response.registry["fundingTotal"])
        assertEquals("50", response.amounts.getValue("budget").uahPerEur)
    }
    @Test fun `explicit source values win and absent snapshot preserves legacy fallback`() {
        val model = project(mapOf("budget" to amount("EUR", "100", "5000")))
        assertEquals("3000.25", model.toResponse(monitoring = source(mapOf("fundingTotal" to "3000.25"))).registry["fundingTotal"])
        assertEquals("0", model.toResponse(monitoring = source(mapOf("fundingTotal" to "0"))).registry["fundingTotal"])
        assertEquals("5001", project(emptyMap()).toResponse().registry["fundingTotal"])
    }
}
