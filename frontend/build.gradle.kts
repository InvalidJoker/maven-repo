import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node)
}

node {
    // Use the Node toolchain already installed on the machine.
    download = false
    nodeProjectDir = layout.projectDirectory
}

/** Produces the production bundle in `frontend/dist`. */
val buildFrontend = tasks.register<NpmTask>("buildFrontend") {
    dependsOn(tasks.named("npmInstall"))
    args = listOf("run", "build")

    inputs.dir("src")
    inputs.dir("public")
    inputs.files(
        "package.json",
        "vite.config.ts",
        "index.html",
        "tsconfig.json",
        "tsconfig.app.json",
        "tsconfig.node.json",
    )
    outputs.dir(layout.projectDirectory.dir("dist"))
}

tasks.named("assemble") {
    dependsOn(buildFrontend)
}
