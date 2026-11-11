// Conventions for a plugin that runs inside a 1.8.8 server (Bukkit API).

plugins {
    id("rago.java-conventions")
}

tasks.processResources {
    val properties = mapOf("version" to project.version.toString())
    inputs.properties(properties)
    filesMatching("plugin.yml") {
        expand(properties)
    }
}

// The server only loads the plugin jar itself, so the Rago modules it uses (for example
// rago-combat-model) are copied into it. Third-party libraries will need relocation
// (Shadow) when the first one is added; there are none yet.
tasks.jar {
    // The jars must be built before they are unpacked here: the lazy `from {}` below does not
    // carry that dependency, so it is declared explicitly (Gradle 9 fails the build otherwise).
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    }) {
        exclude("META-INF/MANIFEST.MF")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
