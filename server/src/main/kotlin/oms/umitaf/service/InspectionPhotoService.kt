package oms.umitaf.service

import oms.umitaf.domain.InspectionPhoto
import oms.umitaf.repository.InspectionPhotoRepository
import oms.umitaf.storage.DurableFileStorage
import oms.umitaf.storage.uploadDirectory
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
        val normalizedBytes = normalizeJpegOrientation(bytes, extension)
        val image = try {
            ImageIO.read(ByteArrayInputStream(normalizedBytes))
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
            Files.write(original, normalizedBytes)
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
                normalizedBytes.size.toLong(),
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

    /**
     * Excel ignores JPEG EXIF orientation while browser and phone viewers do
     * not.  Convert the common camera rotations into real pixels on upload so
     * the same evidence has one orientation everywhere, including XLSX.
     */
    private fun normalizeJpegOrientation(bytes: ByteArray, extension: String): ByteArray {
        if (extension !in setOf("jpg", "jpeg")) return bytes
        val orientation = jpegExifOrientation(bytes) ?: return bytes
        if (orientation !in setOf(3, 6, 8)) return bytes
        val source = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return bytes
        val target = if (orientation in setOf(6, 8)) {
            BufferedImage(source.height, source.width, BufferedImage.TYPE_INT_RGB)
        } else BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        val transform = AffineTransform().apply {
            when (orientation) {
                3 -> { translate(source.width.toDouble(), source.height.toDouble()); rotate(Math.PI) }
                6 -> { translate(source.height.toDouble(), 0.0); rotate(Math.PI / 2) }
                8 -> { translate(0.0, source.width.toDouble()); rotate(-Math.PI / 2) }
            }
        }
        val graphics = target.createGraphics()
        try {
            graphics.drawImage(source, transform, null)
        } finally {
            graphics.dispose()
        }
        return ByteArrayOutputStream().use { output ->
            if (ImageIO.write(target, "jpg", output)) output.toByteArray() else bytes
        }
    }

    /** Minimal EXIF reader for JPEG orientation (tag 0x0112). */
    private fun jpegExifOrientation(bytes: ByteArray): Int? {
        if (bytes.size < 14 || (bytes[0].toInt() and 0xff) != 0xff || (bytes[1].toInt() and 0xff) != 0xd8) return null
        fun unsigned(index: Int) = bytes[index].toInt() and 0xff
        fun short(index: Int, little: Boolean) = if (little) unsigned(index) or (unsigned(index + 1) shl 8) else (unsigned(index) shl 8) or unsigned(index + 1)
        fun int(index: Int, little: Boolean) = if (little) unsigned(index) or (unsigned(index + 1) shl 8) or (unsigned(index + 2) shl 16) or (unsigned(index + 3) shl 24) else (unsigned(index) shl 24) or (unsigned(index + 1) shl 16) or (unsigned(index + 2) shl 8) or unsigned(index + 3)
        var index = 2
        while (index + 4 <= bytes.size) {
            if (unsigned(index) != 0xff) { index++; continue }
            val marker = unsigned(index + 1)
            if (marker == 0xda || marker == 0xd9) break
            val length = short(index + 2, false)
            val data = index + 4
            if (marker == 0xe1 && length >= 10 && data + length - 2 <= bytes.size &&
                String(bytes, data, 4, Charsets.US_ASCII) == "Exif") {
                val tiff = data + 6
                if (tiff + 8 > bytes.size) return null
                val little = unsigned(tiff) == 0x49 && unsigned(tiff + 1) == 0x49
                val count = short(tiff + int(tiff + 4, little), little)
                for (entry in 0 until count) {
                    val offset = tiff + int(tiff + 4, little) + 2 + entry * 12
                    if (offset + 12 > bytes.size) break
                    if (short(offset, little) == 0x0112) return short(offset + 8, little)
                }
            }
            index += length + 2
        }
        return null
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
