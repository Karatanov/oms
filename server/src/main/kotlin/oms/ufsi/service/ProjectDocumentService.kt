package oms.ufsi.service

import oms.ufsi.domain.ProjectDocument
import oms.ufsi.repository.ProjectDocumentRepository
import oms.ufsi.storage.uploadDirectory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

class ProjectDocumentService(private val repository: ProjectDocumentRepository) {
    fun listAll() = repository.findAll()
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
        val originalName = name.replace(Regex("[\\r\\n\\u0000]"), "").trim()
        require(originalName.isNotBlank() && originalName.length <= 255) { "File name is required and must not exceed 255 characters." }
        val extension = originalName.substringAfterLast('.', "").lowercase(); require(extension in setOf("pdf","xls","xlsx","jpg","jpeg","png")) { "Allowed formats: PDF, XLS, XLSX, JPG, PNG." }
        val uuid = UUID.randomUUID(); val directory = uploadDirectory("documents"); Files.createDirectories(directory); val target = directory.resolve("$uuid.$extension")
        val entity = relatedEntity?.trim()?.takeIf { it.isNotEmpty() }
        require(entity == null || entity in setOf("inspection", "financial_record", "incident")) { "Unsupported related entity." }
        require((entity == null) == (relatedId == null)) { "related_entity and related_id must be provided together." }
        try {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            val size = Files.size(target)
            val limit = when(extension) { "pdf" -> 50_000_000; "xls", "xlsx" -> 20_000_000; else -> 10_000_000 }
            require(size in 1..limit) { "File size exceeds the limit for this format." }
            require(matchesDeclaredFormat(target, extension)) { "File content does not match its declared format." }
            return ProjectDocument(0, uuid, projectId, entity, relatedId, documentType, description?.trim()?.takeIf { it.isNotEmpty() }, originalName, target.toString(), contentType ?: "application/octet-stream", size).also(repository::create)
        } catch (e: Exception) { Files.deleteIfExists(target); throw e }
    }

    private fun matchesDeclaredFormat(path: Path, extension: String): Boolean {
        val header = Files.newInputStream(path).use { it.readNBytes(8) }
        fun starts(vararg bytes: Int) = header.size >= bytes.size && bytes.indices.all { header[it].toInt() and 0xff == bytes[it] }
        return when (extension) {
            "pdf" -> starts(0x25, 0x50, 0x44, 0x46, 0x2d)
            "xlsx" -> starts(0x50, 0x4b, 0x03, 0x04)
            "xls" -> starts(0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1)
            "jpg", "jpeg" -> starts(0xff, 0xd8, 0xff)
            "png" -> starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
            else -> false
        }
    }
}
