import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    application
}

// TiDB is MySQL-compatible but does not support MySQL's column-position
// clauses ("AFTER column_name") in ALTER TABLE.  Generate a separate set of
// migrations for it, leaving the canonical MySQL files and their Flyway
// checksums untouched for local installations.
val copyTiDbMigrations by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("src/main/resources/db/migration")) {
        exclude("V18__align_data_model_with_table_definitions.sql")
        exclude("V28__add_user_activation_tokens.sql")
        exclude("V30__replace_guest_role_with_anonymous_access.sql")
        exclude("V31__add_financial_payment_purpose.sql")
        exclude("V32__store_eur_equivalents_for_financial_records.sql")
        exclude("V33__extend_financial_payment_purposes.sql")
        // TiDB does not support CREATE TEMPORARY TABLE ... AS SELECT.
        exclude("V53__classify_lot_records_as_subproject_parts.sql")
        exclude("V56__inherit_missing_subproject_addresses_from_first_lot.sql")
    }
    // A few TiDB limitations need a full SQL replacement, not a line rewrite.
    from(layout.projectDirectory.dir("src/main/resources/db/tidb-overrides"))
    into(layout.buildDirectory.dir("generated/tidb-migrations/db/migration-tidb"))
    filter { line: String ->
        line.replace(Regex("\\s+AFTER\\s+`?[A-Za-z0-9_]+`?", RegexOption.IGNORE_CASE), "")
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(copyTiDbMigrations)
    from(layout.buildDirectory.dir("generated/tidb-migrations"))
}

group = "oms.umitaf.ua.umitaf"
version = "1.0.0"
kotlin {
    jvmToolchain(21)
}
application {
    mainClass.set("oms.umitaf.ApplicationKt")

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
