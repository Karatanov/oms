package oms.ufsi.database.tables


import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date

/**
 * Опис таблиці inspection_reports
 * для бібліотеки Exposed.
 */
object InspectionReportTable :
    LongIdTable("inspection_reports") {

    /**
     * Публічний UUID звіту.
     */
    val uuid =
        varchar("uuid", 36)
            .uniqueIndex()

    /**
     * Проєкт, до якого належить інспекція.
     */
    val projectId =
        reference(
            name = "project_id",
            foreign = ProjectTable,
            onDelete = ReferenceOption.CASCADE
        )

    /**
     * Дата проведення інспекції.
     */
    val inspectionDate =
        date("inspection_date")

    /**
     * Відсоток виконання робіт.
     */
    val completionPct =
        decimal(
            name = "completion_pct",
            precision = 5,
            scale = 2
        )

    /**
     * Підсумок інспекції.
     */
    val summary =
        text("summary")
            .nullable()

    /**
     * Автор звіту.
     */
    val createdBy =
        reference(
            name = "created_by",
            foreign = UserTable,
            onDelete = ReferenceOption.RESTRICT
        )
}