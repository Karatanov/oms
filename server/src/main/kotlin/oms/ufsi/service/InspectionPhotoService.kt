package oms.ufsi.service

import oms.ufsi.domain.InspectionPhoto
import oms.ufsi.repository.InspectionPhotoRepository
import oms.ufsi.storage.DurableFileStorage
import oms.ufsi.storage.uploadDirectory
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO

class InspectionPhotoService(private val repository: InspectionPhotoRepository) {
    fun list(reportId: Long) = repository.list(reportId)
    fun get(reportId: Long, uuid: String) = repository.get(reportId, uuid)
    fun setMain(reportId: Long, uuid: String) = repository.setMain(reportId, uuid)

    fun upload(reportId: Long, name: String, contentType: String?, bytes: ByteArray): InspectionPhoto {
        require(repository.list(reportId).size < 30) { "An inspection report can contain no more than 30 photos." }
        require(bytes.size in 1..10_000_000) { "Photo size must not exceed 10 MB." }
        val originalName = name.replace(Regex("[\\r\\n\\u0000]"), "").trim()
        require(originalName.isNotBlank() && originalName.length <= 255) { "File name is invalid." }
        val extension = originalName.substringAfterLast('.', "").lowercase()
        require(extension in setOf("jpg", "jpeg", "png")) { "Allowed photo formats: JPG, PNG." }
        require(matchesDeclaredFormat(bytes, extension)) { "Photo content does not match its declared format." }
        val image = try {
            ImageIO.read(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            null
        } ?: throw IllegalArgumentException("Invalid image file.")
        require(image.width > 0 && image.height > 0) { "Invalid image dimensions." }

        val uuid = UUID.randomUUID()
        val directory = uploadDirectory("inspection-photos")
        Files.createDirectories(directory)
        val original = directory.resolve("$uuid.$extension")
        val thumbnail = directory.resolve("$uuid-thumb.jpg")
        try {
            Files.write(original, bytes)
            val ratio = minOf(1.0, 320.0 / image.width)
            val width = (image.width * ratio).toInt()
            val height = (image.height * ratio).toInt()
            val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val graphics = target.createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                graphics.drawImage(image, 0, 0, width, height, null)
            } finally {
                graphics.dispose()
            }
            ImageIO.write(target, "jpg", thumbnail.toFile())
            DurableFileStorage.persist(original)
            DurableFileStorage.persist(thumbnail)
            val photo = InspectionPhoto(
                0,
                uuid,
                reportId,
                originalName,
                original.toString(),
                thumbnail.toString(),
                contentType ?: "image/$extension",
                bytes.size.toLong(),
                repository.list(reportId).isEmpty()
            )
            repository.create(photo)
            return photo
        } catch (exception: Exception) {
            runCatching { DurableFileStorage.delete(original.toString()) }
            runCatching { DurableFileStorage.delete(thumbnail.toString()) }
            throw exception
        }
    }

    fun resolveFile(photo: InspectionPhoto, thumbnail: Boolean): Path? =
        DurableFileStorage.resolve(if (thumbnail) photo.thumbnailPath else photo.storagePath)

    private fun matchesDeclaredFormat(bytes: ByteArray, extension: String): Boolean {
        fun starts(vararg values: Int): Boolean = bytes.size >= values.size && values.indices.all { index ->
            (bytes[index].toInt() and 0xff) == values[index]
        }
        return when (extension) {
            "jpg", "jpeg" -> starts(0xff, 0xd8, 0xff)
            "png" -> starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
            else -> false
        }
    }

    fun delete(reportId: Long, uuid: String): Boolean {
        val photo = get(reportId, uuid) ?: return false
        val removed = repository.delete(reportId, uuid)
        if (removed) {
            DurableFileStorage.delete(photo.storagePath)
            DurableFileStorage.delete(photo.thumbnailPath)
        }
        return removed
    }
}
