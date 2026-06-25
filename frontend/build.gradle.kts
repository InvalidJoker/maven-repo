plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(libs.coroutines.core)
    api(libs.logback.classic)
}