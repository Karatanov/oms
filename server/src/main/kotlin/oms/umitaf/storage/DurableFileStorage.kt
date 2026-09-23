package oms.umitaf.storage

import oms.umitaf.database.tables.StoredFileBlobTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.time.Clock
import java.time.LocalDateTime

/**
 * Keeps the filesystem as the primary upload store and mirrors every file to
 * the application database.  The mirror is required by free Render services,
 * whose local filesystem is replaced on every deployment.
 */
object DurableFileStorage {
    fun persist(path: Path) {
        val normalizedPath = path.toAbsolutePath().normalize()
        val bytes = Files.readAllBytes(normalizedPath)
        val now = LocalDateTime.now(Clock.systemUTC())
        transaction {
            StoredFileBlobTable.deleteWhere { StoredFileBlobTable.storagePath eq normalizedPath.toString() }
            StoredFileBlobTable.insert {
                it[storagePath] = normalizedPath.toString()
                it[content] = ExposedBlob(bytes)
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }

    /** Returns the local file, restoring it from the durable mirror when needed. */
    fun resolve(storagePath: String): Path? {
        val path = Path.of(storagePath).toAbsolutePath().normalize()
        if (Files.isRegularFile(path)) return path
        val bytes = transaction {
            StoredFileBlobTable.selectAll()
                .where { StoredFileBlobTable.storagePath eq path.toString() }
                .firstOrNull()
                ?.get(StoredFileBlobTable.content)
                ?.bytes
        } ?: return null
        Files.createDirectories(path.parent)
        // Readers must not see a partly restored XLSX/image. Write to a
        // sibling temporary file and publish the completed copy in one move.
        val temporary = Files.createTempFile(path.parent, "restore-", ".tmp")
        try {
            Files.write(temporary, bytes)
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return path.takeIf { Files.isRegularFile(it) }
    }

    fun delete(storagePath: String) {
        val path = Path.of(storagePath).toAbsolutePath().normalize()
        Files.deleteIfExists(path)
        transaction {
            StoredFileBlobTable.deleteWhere { StoredFileBlobTable.storagePath eq path.toString() }
        }
    }
}
