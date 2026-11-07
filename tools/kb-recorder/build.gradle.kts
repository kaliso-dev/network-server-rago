plugins {
    java
    id("gg.essential.loom") version "0.10.0.5"
    id("dev.architectury.architectury-pack200") version "0.1.3"
}

group = "gg.rago"
version = "0.1.0"

base {
    archivesName.set("rago-kb-recorder")
}

java {
    // Minecraft 1.8.9 and Forge run on Java 8.
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

// The end-to-end test (run-e2e-test.sh) starts the dev client with these properties: it joins the
// test server at start-up and records from the first connection, in its own game directory.
val e2eServer: String? = providers.gradleProperty("rago.e2e.server").orNull
val e2eLabel: String? = providers.gradleProperty("rago.e2e.label").orNull
/** Game directory of the test client, relative to this project (run-e2e-test.sh uses the same). */
val e2eClientDir = "build/e2e-client"

loom {
    forge {
        // Forge 1.8.9 ships its patches packed with pack200, which recent JDKs no longer include.
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
    }
    if (e2eServer != null) {
        runConfigs.named("client") {
            val host = e2eServer.substringBeforeLast(':')
            val port = e2eServer.substringAfterLast(':')
            programArgs("--server", host, "--port", port)
            property("rago.kbrecorder.autostart", e2eLabel ?: "e2e")
            runDir = e2eClientDir
        }
    }
}

if (e2eServer != null) {
    // Loom 0.10's runClient fails Gradle 8's task validation (its "main" property has no input
    // annotation), so the test starts the same launch, with the same classpath and arguments, from
    // a plain JavaExec task.
    tasks.register<JavaExec>("runE2eClient") {
        val runClient = tasks.getByName<JavaExec>("runClient")
        dependsOn(runClient.dependsOn)
        classpath = runClient.classpath
        mainClass.set(providers.provider {
            runCatching { runClient.javaClass.getMethod("getMain").invoke(runClient) as String }
                .getOrElse { runClient.mainClass.get() }
        })
        args(runClient.args)
        jvmArgs(runClient.jvmArgs)
        workingDir = file(e2eClientDir)
        doFirst { workingDir.mkdirs() }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}
