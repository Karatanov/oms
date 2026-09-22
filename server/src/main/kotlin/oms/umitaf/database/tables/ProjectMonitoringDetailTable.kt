package oms.umitaf.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object ProjectMonitoringDetailTable : Table("project_monitoring_details") {
    val projectId = reference("project_id", ProjectTable, onDelete = ReferenceOption.CASCADE)
    val sourceBatchId = integer("source_batch_id").nullable()
    val sourceSubprojectId = varchar("source_subproject_id", 100)
    val sourceLotId = varchar("source_lot_id", 100)
    val nameEn = text("name_en").nullable()
    val oblastCode = varchar("oblast_code", 32).nullable()
    val municipalityNameUk = varchar("municipality_name_uk", 500).nullable()
    val municipalityNameEn = varchar("municipality_name_en", 500).nullable()
    val settlementNameEn = varchar("settlement_name_en", 255).nullable()
    val priorityAreaSource = varchar("priority_area_source", 255).nullable()
    val projectManagerNameUk = varchar("project_manager_name_uk", 500).nullable()
    val projectManagerNameEn = varchar("project_manager_name_en", 500).nullable()
    val projectManagerOrgId = varchar("project_manager_org_id", 64).nullable()
    val beneficiaryNameUk = varchar("beneficiary_name_uk", 500).nullable()
    val beneficiaryNameEn = varchar("beneficiary_name_en", 500).nullable()
    val beneficiaryOrgId = varchar("beneficiary_org_id", 64).nullable()
    val dreamProjectId = varchar("dream_project_id", 128).nullable()
    val dreamProjectUrl = varchar("dream_project_url", 1000).nullable()
    val applicationId = varchar("application_id", 128).nullable()
    val dreamApplicationId = varchar("dream_application_id", 128).nullable()
    val constructionProcurementStatus = varchar("construction_procurement_status", 255).nullable()
    val constructionWorkStatus = varchar("construction_work_status", 255).nullable()
    val geocodeAccuracy = varchar("geocode_accuracy", 32).nullable()
    val geocodeQuery = varchar("geocode_query", 1000).nullable()
    val geocodeDisplayName = varchar("geocode_display_name", 1000).nullable()
    val sourceRows = varchar("source_rows", 100)
    val sourceWorkbook = varchar("source_workbook", 500)
    override val primaryKey = PrimaryKey(projectId)
}
