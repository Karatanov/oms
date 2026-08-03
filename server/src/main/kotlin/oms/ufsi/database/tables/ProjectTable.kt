package oms.ufsi.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

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
        varchar("name", 255)

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
