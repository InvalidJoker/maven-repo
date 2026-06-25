
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(ktorLibs.plugins.ktor)
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.netty)
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
