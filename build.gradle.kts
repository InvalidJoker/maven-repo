plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinx.serialization)
}

allprojects {
    group = "de.joker.template"
    version = "0.0.1"

    repositories {
        mavenCentral()
    }
}