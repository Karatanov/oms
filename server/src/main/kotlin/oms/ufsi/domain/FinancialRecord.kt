package oms.ufsi.domain

import java.time.LocalDate
import java.util.UUID

data class FinancialRecord(
    val id: Long,
    val uuid: UUID,
    val projectId: Long,
    val recordType: FinancialRecordType,
    val referenceNumber: String,
    val amount: Long,
    val currency: String,
    val recordDate: LocalDate,
    val paymentDate: LocalDate?,
    val description: String?,
    val milestone: String?,
    val paymentPurpose: String = "works",
    val eurExchangeRate: Double? = null,
    val eurExchangeDate: LocalDate? = null,
    val amountEurCents: Long? = null
)
