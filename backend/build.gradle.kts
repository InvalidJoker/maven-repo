
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(ktorLibs.plugins.ktor)
}

application {
    mainClass = "io.ktor.server.cio.EngineMain"
}

dependencies {
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.cio)
    implementation(ktorLibs.server.sessions)
    implementation(libs.bcrypt)
    implementation(libs.exposed.core)
    implementation(libs.exposed.r2dbc)
    implementation(libs.h2database.h2)
    implementation(libs.h2database.r2dbc)
    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)
    implementation(libs.logback.classic)
    implementation(libs.r2dbc.postgresql)

    //testImplementation(kotlin("test"))
    //testImplementation(ktorLibs.server.testHost)
}

// Bundle the built frontend into the backend's classpath under `web/`, served by Ktor at `/`.
tasks.processResources {
    dependsOn(":frontend:buildFrontend")
    from(rootProject.file("frontend/dist")) {
        into("web")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.test {
    useJUnitPlatform()
}