package oms.ufsi.database.tables

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
     * project або subproject.
     */
    val projectType =
        varchar("project_type", 20)

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

    /**
     * Повна адреса.
     */
    val address =
        varchar("address", 500)

    /**
     * Область.
     */
    val region =
        varchar("region", 100)

    /**
     * Населений пункт.
     */
    val city =
        varchar("city", 100)

    /**
     * Географічна широта.
     */
    val latitude =
        decimal(
            name = "latitude",
            precision = 10,
            scale = 7
        )

    /**
     * Географічна довгота.
     */
    val longitude =
        decimal(
            name = "longitude",
            precision = 10,
            scale = 7
        )

    /**
     * Поточний статус проєкту.
     */
    val status =
        varchar("status", 20)

    /**
     * Галузь.
     */
    val sector =
        varchar("sector", 100)

    /**
     * Тип будівництва.
     */
    val constructionType =
        varchar("construction_type", 100)

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
