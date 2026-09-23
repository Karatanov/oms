package oms.umitaf.service

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

/** Decode only enough pixels for the preview; the original bytes are untouched. */
internal fun createPhotoThumbnail(bytes: ByteArray): BufferedImage {
    try {
        MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val readers = ImageIO.getImageReaders(input)
            require(readers.hasNext()) { "Invalid image file." }
            val reader = readers.next()
            try {
                reader.input = input
                val sourceWidth = reader.getWidth(0)
                val sourceHeight = reader.getHeight(0)
                require(sourceWidth > 0 && sourceHeight > 0) { "Invalid image dimensions." }
                val step = (maxOf(sourceWidth, sourceHeight) / 320).coerceAtLeast(1)
                val parameters = reader.defaultReadParam.apply { setSourceSubsampling(step, step, 0, 0) }
                val decoded = reader.read(0, parameters)
                try {
                    val ratio = minOf(1.0, 320.0 / sourceWidth, 320.0 / sourceHeight)
                    val width = (sourceWidth * ratio).toInt().coerceAtLeast(1)
                    val height = (sourceHeight * ratio).toInt().coerceAtLeast(1)
                    return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { target ->
                        val graphics = target.createGraphics()
                        try {
                            graphics.color = Color.WHITE
                            graphics.fillRect(0, 0, width, height)
                            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                            graphics.drawImage(decoded, 0, 0, width, height, null)
                        } finally { graphics.dispose() }
                    }
                } finally { decoded.flush() }
            } finally { reader.dispose() }
        }
    } catch (exception: Exception) {
        throw IllegalArgumentException("Invalid image file.", exception)
    }
}
