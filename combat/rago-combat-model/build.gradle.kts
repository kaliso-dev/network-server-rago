// Pure Java: the knockback formula, the velocity packet encoding and the client trajectory
// simulator. No Bukkit dependency, so it is tested without a server and reused by the tools.
plugins {
    id("rago.java-conventions")
    `java-library`
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Werror")
}
