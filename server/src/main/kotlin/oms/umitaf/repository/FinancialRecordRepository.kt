package oms.umitaf.repository

import oms.umitaf.domain.FinancialRecord
import oms.umitaf.domain.FinancialRecordType

interface FinancialRecordRepository {
    fun findAll(): List<FinancialRecord>
    fun findByProjectId(projectId: Long): List<FinancialRecord>
    fun findByUuid(projectId: Long, uuid: String): FinancialRecord?
    fun create(projectId: Long, type: FinancialRecordType, reference: String, amount: Double, currency: String, recordDate: String, paymentDate: String?, description: String?, milestone: String?, paymentPurpose: String, eurExchangeRate: Double, eurExchangeDate: String, amountEurCents: Long, createdBy: Long): FinancialRecord
    fun update(projectId: Long, uuid: String, type: FinancialRecordType, reference: String, amount: Double, currency: String, recordDate: String, paymentDate: String?, description: String?, milestone: String?, paymentPurpose: String, eurExchangeRate: Double, eurExchangeDate: String, amountEurCents: Long): FinancialRecord?
    fun move(projectId: Long, uuid: String, targetProjectId: Long): Boolean
    fun updateEurConversion(uuid: String, eurExchangeRate: Double, eurExchangeDate: String, amountEurCents: Long): Boolean
    fun delete(projectId: Long, uuid: String): Boolean
}
