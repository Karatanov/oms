package oms.umitaf.service

import oms.umitaf.domain.InspectionPhoto
import oms.umitaf.repository.InspectionPhotoRepository
import kotlin.test.*

class PhotoExifTest {
    private val service = InspectionPhotoService(object : InspectionPhotoRepository {
        override fun list(reportId: Long): List<InspectionPhoto> = error("Unexpected database access")
        override fun get(reportId: Long, uuid: String): InspectionPhoto? = error("Unexpected database access")
        override fun create(photo: InspectionPhoto): Unit = error("Unexpected database access")
        override fun setMain(reportId: Long, uuid: String): InspectionPhoto? = error("Unexpected database access")
        override fun delete(reportId: Long, uuid: String): Boolean = error("Unexpected database access")
    })

    private fun jpeg(little: Boolean, orientation: Int): ByteArray {
        val bytes = ByteArray(40)
        fun put(at: Int, value: Int, size: Int) {
            repeat(size) { i -> bytes[at + i] = (value ushr (8 * if (little) i else size - i - 1)).toByte() }
        }
        bytes[0] = 0xff.toByte(); bytes[1] = 0xd8.toByte()
        bytes[2] = 0xff.toByte(); bytes[3] = 0xe1.toByte(); bytes[5] = 34
        "Exif\u0000\u0000".toByteArray().copyInto(bytes, 6)
        bytes[12] = if (little) 0x49 else 0x4d; bytes[13] = bytes[12]
        put(14, 42, 2); put(16, 8, 4); put(20, 1, 2)
        put(22, 0x112, 2); put(24, 3, 2); put(26, 1, 4); put(30, orientation, 2)
        bytes[38] = 0xff.toByte(); bytes[39] = 0xd9.toByte()
        return bytes
    }

    @Test fun `reads both byte orders and all valid orientations`() {
        for (little in listOf(true, false)) for (orientation in 1..8) {
            assertEquals(orientation, service.jpegExifOrientation(jpeg(little, orientation)))
        }
    }

    @Test fun `truncated metadata and invalid offsets do not throw`() {
        val source = jpeg(true, 6)
        for (size in 0 until 38) assertNull(service.jpegExifOrientation(source.copyOf(size)))
        for (offset in listOf(0, 7, 26, Int.MAX_VALUE, -1)) {
            val bytes = source.copyOf()
            repeat(4) { bytes[16 + it] = (offset ushr (8 * it)).toByte() }
            assertNull(service.jpegExifOrientation(bytes))
        }
    }

    @Test fun `rejects invalid TIFF fields and does not read outside APP1`() {
        for ((index, value) in listOf(5 to 16, 12 to 0, 14 to 0, 20 to 127, 24 to 4, 26 to 2, 30 to 9)) {
            val bytes = jpeg(true, 6)
            bytes[index] = value.toByte()
            assertNull(service.jpegExifOrientation(bytes), "field $index")
        }
    }
}
