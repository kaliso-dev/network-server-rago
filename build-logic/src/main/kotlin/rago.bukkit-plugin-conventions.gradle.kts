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
    val runtimeJars = configurations.runtimeClasspath.map { classpath ->
        classpath.filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    }
    from(runtimeJars) {
        exclude("META-INF/MANIFEST.MF")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
