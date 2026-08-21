package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable data class ProjectDocumentResponse(val uuid: String, val docType: String, val fileName: String, val contentType: String, val fileSizeBytes: Long, val relatedEntity: String? = null, val relatedId: Long? = null, val description: String? = null)

@Serializable data class ProjectDocumentListItemResponse(val projectUuid: String, val document: ProjectDocumentResponse)
