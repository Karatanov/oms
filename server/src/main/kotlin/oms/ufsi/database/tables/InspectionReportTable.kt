package oms.ufsi.database.tables


import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime

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

    val reportCode = varchar("report_code", 100).nullable()

    val inspectionType = varchar("inspection_type", 20)

    val status = varchar("status", 20)

    val rejectionReason = text("rejection_reason").nullable()

    val submittedAt = datetime("submitted_at").nullable()

    val reviewedAt = datetime("reviewed_at").nullable()

    val latitude = decimal("latitude", 10, 7).nullable()

    val longitude = decimal("longitude", 10, 7).nullable()
}
