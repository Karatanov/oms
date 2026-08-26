package oms.ufsi.repository

import oms.ufsi.domain.FinancialRecord
import oms.ufsi.domain.FinancialRecordType

interface FinancialRecordRepository {
    fun findAll(): List<FinancialRecord>
    fun findByProjectId(projectId: Long): List<FinancialRecord>
    fun findByUuid(projectId: Long, uuid: String): FinancialRecord?
    fun create(projectId: Long, type: FinancialRecordType, reference: String, amount: Long, currency: String, recordDate: String, paymentDate: String?, description: String?, milestone: String?, paymentPurpose: String, createdBy: Long): FinancialRecord
    fun update(projectId: Long, uuid: String, type: FinancialRecordType, reference: String, amount: Long, currency: String, recordDate: String, paymentDate: String?, description: String?, milestone: String?, paymentPurpose: String): FinancialRecord?
    fun move(projectId: Long, uuid: String, targetProjectId: Long): Boolean
    fun delete(projectId: Long, uuid: String): Boolean
}
