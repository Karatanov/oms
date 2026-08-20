import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    application
}

val copyRenderWebAssets by tasks.registering(Copy::class) {
    dependsOn(":composeApp:wasmJsBrowserDistribution")
    from(project(":composeApp").layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    into(layout.buildDirectory.dir("generated/render-web"))
}

// TiDB is MySQL-compatible but does not support MySQL's column-position
// clauses ("AFTER column_name") in ALTER TABLE.  Generate a separate set of
// migrations for it, leaving the canonical MySQL files and their Flyway
// checksums untouched for local installations.
val copyTiDbMigrations by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("src/main/resources/db/migration"))
    into(layout.buildDirectory.dir("generated/tidb-migrations/db/migration-tidb"))
    filter { line: String ->
        line.replace(Regex("\\s+AFTER\\s+`?[A-Za-z0-9_]+`?", RegexOption.IGNORE_CASE), "")
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(copyRenderWebAssets)
    dependsOn(copyTiDbMigrations)
    from(copyRenderWebAssets) {
        into("static")
    }
    from(copyTiDbMigrations)
}

group = "oms.usif.ua.ufsi"
version = "1.0.0"
kotlin {
    jvmToolchain(21)
}
application {
    mainClass.set("oms.ufsi.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)

    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-sessions")
    implementation("com.mysql:mysql-connector-j:9.3.0")
    implementation("org.jetbrains.exposed:exposed-core:1.0.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.0.0")
    implementation("org.jetbrains.exposed:exposed-java-time:1.0.0")
    implementation("org.flywaydb:flyway-mysql:11.12.0")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("org.apache.poi:poi-ooxml:5.4.1")

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
