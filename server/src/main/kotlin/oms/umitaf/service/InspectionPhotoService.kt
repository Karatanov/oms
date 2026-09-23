package oms.umitaf.service

import oms.umitaf.domain.InspectionPhoto
import oms.umitaf.repository.InspectionPhotoRepository
import oms.umitaf.storage.DurableFileStorage
import oms.umitaf.storage.uploadDirectory
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
        val target = createPhotoThumbnail(normalizedBytes)

        val uuid = UUID.randomUUID()
        val directory = uploadDirectory("inspection-photos")
        Files.createDirectories(directory)
        val original = directory.resolve("$uuid.$extension")
        val thumbnail = directory.resolve("$uuid-thumb.jpg")
        try {
            Files.write(original, normalizedBytes)
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
    internal fun jpegExifOrientation(bytes: ByteArray): Int? {
        if (bytes.size < 14 || (bytes[0].toInt() and 0xff) != 0xff || (bytes[1].toInt() and 0xff) != 0xd8) return null
        fun unsigned(index: Int) = bytes[index].toInt() and 0xff
        fun short(index: Int, little: Boolean) = if (little) unsigned(index) or (unsigned(index + 1) shl 8) else (unsigned(index) shl 8) or unsigned(index + 1)
        fun int(index: Int, little: Boolean) = if (little) unsigned(index) or (unsigned(index + 1) shl 8) or (unsigned(index + 2) shl 16) or (unsigned(index + 3) shl 24) else (unsigned(index) shl 24) or (unsigned(index + 1) shl 16) or (unsigned(index + 2) shl 8) or unsigned(index + 3)
        var index = 2
        while (index + 4 <= bytes.size) {
            if (unsigned(index) != 0xff) { index++; continue }
            val marker = unsigned(index + 1)
            if (marker == 0xda || marker == 0xd9) break
            if (marker == 0xff || marker == 0x01 || marker in 0xd0..0xd8) { index += if (marker == 0xff) 1 else 2; continue }
            val length = short(index + 2, false)
            if (length < 2 || length > bytes.size - index - 2) return null
            val data = index + 4
            val segmentEnd = index + 2 + length
            if (marker == 0xe1 && length >= 16 &&
                String(bytes, data, 6, Charsets.US_ASCII) == "Exif\u0000\u0000") {
                val tiff = data + 6
                val little = unsigned(tiff) == 0x49 && unsigned(tiff + 1) == 0x49
                val big = unsigned(tiff) == 0x4d && unsigned(tiff + 1) == 0x4d
                if ((!little && !big) || short(tiff + 2, little) != 42) return null
                val relative = int(tiff + 4, little).toLong() and 0xffffffffL
                if (relative < 8 || relative > segmentEnd.toLong() - tiff - 2) return null
                val directory = tiff + relative.toInt()
                val count = short(directory, little)
                if (count > (segmentEnd - directory - 2) / 12) return null
                for (entry in 0 until count) {
                    val offset = directory + 2 + entry * 12
                    if (short(offset, little) == 0x0112) {
                        if (short(offset + 2, little) != 3 || int(offset + 4, little) != 1) return null
                        return short(offset + 8, little).takeIf { it in 1..8 }
                    }
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
