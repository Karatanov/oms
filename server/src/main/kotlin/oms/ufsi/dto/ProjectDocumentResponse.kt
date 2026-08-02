package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable data class ProjectDocumentResponse(val uuid: String, val docType: String, val fileName: String, val contentType: String, val fileSizeBytes: Long)
