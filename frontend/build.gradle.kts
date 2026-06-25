plugins {
    base
}

/** Resolves the `bun` executable, preferring an absolute install path over the daemon's PATH. */
fun bunExecutable(): String {
    val candidates = listOfNotNull(
        System.getenv("BUN_INSTALL")?.let { "$it/bin/bun" },
        "${System.getProperty("user.home")}/.bun/bin/bun",
    )
    return candidates.firstOrNull { file(it).exists() } ?: "bun"
}

val bunInstall = tasks.register<Exec>("bunInstall") {
    inputs.files("package.json", "bun.lock")
    outputs.dir(layout.projectDirectory.dir("node_modules"))
    commandLine(bunExecutable(), "install")
}

/** Produces the production bundle in `frontend/dist` via `bun run build` (tsc + vite). */
val buildFrontend = tasks.register<Exec>("buildFrontend") {
    dependsOn(bunInstall)
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
    commandLine(bunExecutable(), "run", "build")
}

tasks.named("assemble") {
    dependsOn(buildFrontend)
}
