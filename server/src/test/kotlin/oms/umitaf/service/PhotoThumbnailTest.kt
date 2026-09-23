package oms.umitaf.service

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.*

class PhotoThumbnailTest {
    private fun encoded(width: Int, height: Int, format: String): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try { graphics.color = Color.RED; graphics.fillRect(0, 0, width, height) }
        finally { graphics.dispose() }
        return ByteArrayOutputStream().use { output ->
            assertTrue(ImageIO.write(image, format, output))
            output.toByteArray()
        }.also { image.flush() }
    }

    @Test fun `portrait landscape and extreme aspect ratios stay bounded and nonzero`() {
        for (format in listOf("png", "jpg")) {
            for ((width, height) in listOf(2400 to 1600, 1600 to 2400, 10000 to 1, 1 to 10000, 10 to 20)) {
                val bytes = encoded(width, height, format)
                val original = bytes.copyOf()
                val preview = createPhotoThumbnail(bytes)
                assertTrue(preview.width in 1..320 && preview.height in 1..320)
                assertTrue(preview.width <= width && preview.height <= height)
                assertContentEquals(original, bytes)
                assertTrue(Color(preview.getRGB(0, 0)).red > 200)
                preview.flush()
            }
        }
    }

    @Test fun `invalid data produces validation error`() {
        assertFailsWith<IllegalArgumentException> { createPhotoThumbnail(byteArrayOf(1, 2, 3)) }
    }

    @Test fun `repeated decoding produces stable previews`() {
        val bytes = encoded(2400, 1600, "jpg")
        repeat(50) {
            val preview = createPhotoThumbnail(bytes)
            assertEquals(320, preview.width)
            assertEquals(213, preview.height)
            preview.flush()
        }
    }

    @Test fun `measure full decode versus sampled preview on the same JPEG`() {
        val bytes = encoded(4000, 3000, "jpg")
        fun baseline(): BufferedImage {
            val image = ImageIO.read(ByteArrayInputStream(bytes))
            val preview = BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB)
            val graphics = preview.createGraphics()
            try {
                graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                graphics.drawImage(image, 0, 0, 320, 240, null)
            } finally { graphics.dispose(); image.flush() }
            return preview
        }
        fun measure(create: () -> BufferedImage): List<Double> {
            repeat(3) { create().flush() }
            return List(20) {
                val start = System.nanoTime()
                create().also { assertEquals(320, it.width); assertEquals(240, it.height) }.flush()
                (System.nanoTime() - start) / 1_000_000.0
            }.sorted()
        }
        val before = measure(::baseline)
        val after = measure { createPhotoThumbnail(bytes) }
        val output = java.io.File("build/reports/performance/photo-thumbnail.txt")
        output.parentFile.mkdirs()
        output.writeText("Synthetic 4000x3000 JPEG; 20 samples after 3 warm-ups; CI only.\n" +
            listOf("before" to before, "after" to after).joinToString("\n") { (label, values) ->
                "$label mean=${values.average()} p50=${values[9]} p95=${values[18]} p99=${values[19]} ms"
            })
    }
}
