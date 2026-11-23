package oms.urp3.config

import oms.urp3.security.PasswordHasher
import java.sql.DriverManager
import java.util.UUID

/** Explicit first-user setup from the deployed service's interactive console. */
fun main() {
    require(!Deployment.legacy) { "Use this command only for an isolated installation." }
    val console = requireNotNull(System.console()) { "An interactive terminal is required." }
    val username = console.readLine("Administrator username: ").trim()
    val email = console.readLine("Administrator email: ").trim()
    require(username.length in 1..100 && email.length in 3..255 && '@' in email)
    val password = console.readPassword("New password (at least 12 characters): ")
    val hash = try {
        require(password.size >= 12 && String(password).toByteArray().size <= 72)
        PasswordHasher.hash(String(password))
    } finally {
        password.fill(' ')
    }
    DriverManager.getConnection(System.getenv("DATABASE_URL"), System.getenv("DATABASE_USER"),
        System.getenv("DATABASE_PASSWORD")).use { db ->
        require(db.catalog == "oms_" + Deployment.brand)
        db.autoCommit = false
        db.createStatement().use { statement ->
            statement.executeQuery("SELECT id FROM roles WHERE code='ADMIN' FOR UPDATE").use { roles ->
                check(roles.next())
                val role = roles.getLong(1)
                db.createStatement().use { checkStatement ->
                    checkStatement.executeQuery("SELECT 1 FROM users LIMIT 1 FOR UPDATE").use {
                        check(!it.next()) { "First-user setup requires an empty user registry." }
                    }
                }
                db.prepareStatement("INSERT INTO users(uuid,username,email,password_hash,role_id) VALUES(?,?,?,?,?)").use {
                    it.setString(1, UUID.randomUUID().toString())
                    it.setString(2, username)
                    it.setString(3, email)
                    it.setString(4, hash)
                    it.setLong(5, role)
                    it.executeUpdate()
                }
            }
        }
        db.commit()
    }
    println("Administrator created.")
}
