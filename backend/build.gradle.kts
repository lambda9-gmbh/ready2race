import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.postgresql:postgresql:42.7.7")
        classpath("org.flywaydb:flyway-database-postgresql:11.3.4")
    }
}

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    application
    id("com.gradleup.shadow") version "8.3.8"
    id("org.flywaydb.flyway") version "11.3.4"
    id("nu.studer.jooq") version "9.0"
}

group = "de.lambda9.ready2race"
version = "0.0.1"
description = "REST-API Backend for Ready2Race"

base {
    archivesName = "ready2race-api"
}

val ktorVersion = "3.1.1"
val slf4jVersion = "2.0.17"
val log4j2Version = "2.24.3"
val postgresVersion = "42.7.7"
val flywayVersion = "11.3.4"
val jooqVersion = "3.19.20" // jOOQ 3.20 requires a newer Java version
val jacksonVersion = "2.18.3"
val skipCodegen = providers.gradleProperty("skipCodegen")
    .orElse(providers.systemProperty("skipCodegen"))
    .map(String::toBoolean)
    .getOrElse(false)

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-host-common-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("de.lambda9.tailwind:tailwind-kio:0.2.0")
    implementation("de.lambda9.tailwind:tailwind-kio-jooq:0.2.0")
    implementation("io.ktor:ktor-server-swagger-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-compression-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-double-receive-jvm:$ktorVersion")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.simplejavamail:simple-java-mail:8.12.4")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4j2Version")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4j2Version")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.4")
    implementation("org.jooq:jooq:$jooqVersion")
    implementation("org.jooq:jooq-kotlin:$jooqVersion")
    implementation("org.jooq:jooq-kotlin-coroutines:$jooqVersion")
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
    implementation("com.password4j:password4j:1.8.2")
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("com.opencsv:opencsv:5.11.2")
    implementation("org.apache.poi:poi:5.4.1")
    implementation("org.apache.poi:poi-ooxml:5.4.1")

    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("com.zaxxer:HikariCP:6.2.1")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.10.1")

    jooqGenerator("org.postgresql:postgresql:$postgresVersion")
}

application {
    mainClass = "de.lambda9.ready2race.backend.ApplicationKt"
}

kotlin {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets.main {
        kotlin.srcDir(layout.buildDirectory.dir("generated-sources/jooq"))
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.test {
    useJUnitPlatform()
}

flyway {
    url = "jdbc:postgresql://localhost:7652/ready2race-build"
    user = "developer"
    password = "sql"
    schemas = arrayOf("ready2race")
    cleanDisabled = false
}

jooq {
    version.set(jooqVersion)
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(!skipCodegen)
            jooqConfiguration.apply {
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = "jdbc:postgresql://localhost:7652/ready2race-build"
                    user = "developer"
                    password = "sql"
                }
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        includes = ".*"
                        excludes = ""
                        inputSchema = "ready2race"
                        isOutputSchemaToDefault = false
                        isIncludeSystemSequences = true
                    }
                    generate.apply {
                        isPojos = false
                        isPojosAsKotlinDataClasses = true
                        isDaos = false
                        isValidationAnnotations = false
                        isJavaTimeTypes = true
                        isKotlinNotNullRecordAttributes = true
                    }
                    target.apply {
                        packageName = "de.lambda9.ready2race.backend.database.generated"
                        directory = layout.buildDirectory.dir("generated-sources/jooq").get().asFile.path
                    }
                }
            }
        }
    }
}

tasks.named("generateJooq") {
    enabled = !skipCodegen
    dependsOn(tasks.named("flywayMigrate"))
}

tasks.named("flywayMigrate") {
    enabled = !skipCodegen
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = "jar-with-dependencies"
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
