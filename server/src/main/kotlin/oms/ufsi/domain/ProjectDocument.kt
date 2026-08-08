package oms.ufsi.domain

import java.util.UUID

data class ProjectDocument(val id: Long, val uuid: UUID, val projectId: Long, val relatedEntity: String?, val relatedId: Long?, val docType: String, val description: String?, val originalName: String, val storagePath: String, val contentType: String, val fileSizeBytes: Long)
