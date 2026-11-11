// Pure Java: the catalogue of game modes, the queues and the matchmaking. No Bukkit dependency, so
// it is tested without a server, and the Velocity matchmaker (rago-proxy) can reuse it later.
plugins {
    id("rago.java-conventions")
    `java-library`
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Werror")
}
