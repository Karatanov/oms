package oms.ufsi.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date

object ProjectAmountTable : LongIdTable("project_amounts") {
    val projectId = reference("project_id", ProjectTable, onDelete = ReferenceOption.CASCADE)
    val kind = varchar("amount_kind", 40)
    val amount = decimal("amount", 18, 2)
    val currency = varchar("currency", 3)
    val convertedAmount = decimal("converted_amount", 18, 2)
    val uahPerEur = decimal("uah_per_eur", 18, 8)
    val rateDate = date("rate_date")
    val conversionEdited = bool("conversion_edited")
}
