package oms.ufsi.storage

import java.nio.file.Path

/** Location for runtime uploads; Render can point this at a paid persistent disk. */
fun uploadDirectory(vararg parts: String): Path {
    val root = System.getenv("OMS_UPLOAD_DIR")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "uploads"
    return Path.of(root, *parts).toAbsolutePath().normalize()
}
