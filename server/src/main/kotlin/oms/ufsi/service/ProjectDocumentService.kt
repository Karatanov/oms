package oms.ufsi.service

import oms.ufsi.domain.ProjectDocument
import oms.ufsi.repository.ProjectDocumentRepository
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

class ProjectDocumentService(private val repository: ProjectDocumentRepository) {
    fun list(projectId: Long) = repository.findByProjectId(projectId)
    fun get(projectId: Long, uuid: String) = repository.findByUuid(projectId, uuid)
    fun delete(projectId: Long, uuid: String): Boolean {
        val document = get(projectId, uuid) ?: return false
        val deleted = repository.delete(projectId, uuid)
        if (deleted) Files.deleteIfExists(Path.of(document.storagePath))
        return deleted
    }
    fun upload(projectId: Long, type: String, name: String, contentType: String?, input: InputStream, relatedEntity: String? = null, relatedId: Long? = null, description: String? = null): ProjectDocument {
        val documentType = type.lowercase(); require(documentType in setOf("contract","design","estimate","invoice","act","photo","other")) { "Unsupported document type." }
        val extension = name.substringAfterLast('.', "").lowercase(); require(extension in setOf("pdf","xlsx","jpg","jpeg","png")) { "Allowed formats: PDF, XLSX, JPG, PNG." }
        val uuid = UUID.randomUUID(); val directory = Path.of("uploads", "documents").toAbsolutePath().normalize(); Files.createDirectories(directory); val target = directory.resolve("$uuid.$extension")
        val entity = relatedEntity?.trim()?.takeIf { it.isNotEmpty() }
        require(entity == null || entity in setOf("inspection", "financial_record", "incident")) { "Unsupported related entity." }
        require((entity == null) == (relatedId == null)) { "related_entity and related_id must be provided together." }
        try { Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING); val size = Files.size(target); val limit = when(extension) { "pdf" -> 50_000_000; "xlsx" -> 20_000_000; else -> 10_000_000 }; require(size in 1..limit) { "File size exceeds the limit for this format." }; return ProjectDocument(0, uuid, projectId, entity, relatedId, documentType, description?.trim()?.takeIf { it.isNotEmpty() }, name, target.toString(), contentType ?: "application/octet-stream", size).also(repository::create) } catch (e: Exception) { Files.deleteIfExists(target); throw e }
    }
}
