package oms.ufsi.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * Опис таблиці inspection_findings
 * для бібліотеки Exposed.
 */
object InspectionFindingTable :
    LongIdTable("inspection_findings") {

    /**
     * Публічний UUID запису.
     */
    val uuid =
        varchar("uuid", 36)
            .uniqueIndex()

    /**
     * Звіт інспекції.
     */
    val inspectionReportId =
        reference(
            name = "inspection_report_id",
            foreign = InspectionReportTable,
            onDelete = ReferenceOption.CASCADE
        )

    /**
     * Категорія проблеми.
     */
    val category =
        varchar("category", 50)

    /**
     * Критичність.
     */
    val severity =
        varchar("severity", 20)

    /**
     * Опис проблеми.
     */
    val description =
        text("description")

    /**
     * Рекомендації щодо усунення.
     */
    val recommendation =
        text("recommendation")
            .nullable()

    /**
     * Ознака усунення проблеми.
     */
    val isResolved =
        bool("is_resolved")
}