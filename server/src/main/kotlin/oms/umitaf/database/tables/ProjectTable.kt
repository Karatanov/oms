package oms.umitaf.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date

/**
 * Опис таблиці проєктів для Exposed.
 *
 * Повинен повністю відповідати
 * структурі Flyway-міграції.
 */
object ProjectTable : LongIdTable("projects") {

    /**
     * Публічний UUID проєкту.
     *
     * Саме його будемо використовувати
     * у REST API замість числового ID.
     */
    val uuid =
        varchar("uuid", 36)
            .uniqueIndex()

    /**
     * Тип запису:
     * project, subproject або subproject_part.
     */
    val projectType =
        varchar("project_type", 20)

    /** Customer-facing tranche number; all records start in tranche 1. */
    val trancheNumber = integer("tranche_number").default(1)

    /**
     * Батьківський проєкт.
     *
     * Null означає кореневий проєкт.
     */
    val parentProjectId =
        optReference(
            name = "parent_project_id",
            foreign = this,
            onDelete = ReferenceOption.RESTRICT
        )

    /**
     * Повна назва проєкту.
     */
    val name =
        text("name")

    /**
     * Короткий код майданчика.
     */
    val siteName =
        varchar("site_name", 100)

    /**
     * Номер майданчика.
     */
    val siteNumber =
        varchar("site_number", 50)

    val description = text("description").nullable()
    val designerName = varchar("designer_name", 255).nullable()
    val designContractNumber = varchar("design_contract_number", 100).nullable()
    val designContractTerm = varchar("design_contract_term", 500).nullable()
    val constructionContractNumber = varchar("construction_contract_number", 100).nullable()
    val technicalSupervisionName = varchar("technical_supervision_name", 255).nullable()
    val technicalSupervisionContractNumber = varchar("technical_supervision_contract_number", 100).nullable()
    val engineerConsultantName = varchar("engineer_consultant_name", 255).nullable()
    val engineerConsultantContractNumber = varchar("engineer_consultant_contract_number", 100).nullable()

    /**
     * Повна адреса.
     */
    val address =
        varchar("address", 500).nullable()

    /**
     * Область.
     */
    val region =
        varchar("region", 100).nullable()

    /**
     * Населений пункт.
     */
    val city =
        varchar("city", 100).nullable()

    /**
     * Географічна широта.
     */
    val latitude =
        decimal(
            name = "latitude",
            precision = 10,
            scale = 7
        ).nullable()

    /**
     * Географічна довгота.
     */
    val longitude =
        decimal(
            name = "longitude",
            precision = 10,
            scale = 7
        ).nullable()

    /**
     * Поточний статус проєкту.
     */
    val status =
        varchar("status", 20)

    /**
     * Галузь.
     */
    val sector =
        varchar("sector", 100).nullable()

    /**
     * Тип будівництва.
     */
    val constructionType =
        varchar("construction_type", 100).nullable()

    /**
     * Плановий бюджет у гривнях.
     */
    val budgetPlanned =
        long("budget_planned")

    /** Optional contract amount for the engineer-consultant, in UAH. */
    val engineerConsultantContractAmount =
        long("engineer_consultant_contract_amount")
            .nullable()

    /** Optional technical-supervision amount, in UAH. */
    val technicalSupervisionAmount =
        long("technical_supervision_amount")
            .nullable()

    val subprojectContractAmount =
        long("subproject_contract_amount")
            .nullable()

    val startDate = date("start_date").nullable()
    val endDate = date("end_date").nullable()
    val contractSignedDate = date("contract_signed_date").nullable()
    val plannedEndDate = date("planned_end_date").nullable()
    val designContractSigningDate = date("design_contract_signing_date").nullable()
    val designStartDate = date("design_start_date").nullable()
    val designPlannedEndDate = date("design_planned_end_date").nullable()
    val technicalSupervisionContractDate = date("technical_supervision_contract_date").nullable()
    val technicalSupervisionStartDate = date("technical_supervision_start_date").nullable()
    val technicalSupervisionPlannedEndDate = date("technical_supervision_planned_end_date").nullable()
    val engineerConsultantContractDate = date("engineer_consultant_contract_date").nullable()
    val engineerConsultantStartDate = date("engineer_consultant_start_date").nullable()
    val engineerConsultantPlannedEndDate = date("engineer_consultant_planned_end_date").nullable()
    val constructionContractSigningDate = date("construction_contract_signing_date").nullable()
    val constructionStartDate = date("construction_start_date").nullable()
    val projectedCompletionTime = date("projected_completion_time").nullable()

    /**
     * Валюта відображення.
     */
    val currency =
        varchar("currency", 3)

    /**
     * Основний підрядник.
     */
    val contractorName =
        varchar("contractor_name", 255)
            .nullable()

    /**
     * Керівник проєкту.
     */
    val managerId =
        optReference(
            name = "manager_id",
            foreign = UserTable,
            onDelete = ReferenceOption.RESTRICT
        )

    /**
     * Користувач, який створив запис.
     */
    val createdBy =
        optReference(
            name = "created_by",
            foreign = UserTable,
            onDelete = ReferenceOption.RESTRICT
        )
}
