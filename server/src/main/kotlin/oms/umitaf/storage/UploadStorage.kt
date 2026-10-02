package oms.umitaf.storage

import java.nio.file.Path

/** Location for runtime uploads; production hosting may point this at persistent storage. */
fun uploadDirectory(vararg parts: String): Path {
    val root = System.getenv("OMS_UPLOAD_DIR")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "uploads"
    return Path.of(root, *parts).toAbsolutePath().normalize()
}
