// kb-recorder is a Minecraft 1.8.9 Forge client mod. It has its own Gradle build because the
// Forge 1.8.9 toolchain (Essential's fork of Architectury Loom) needs Gradle 8 and Java 8, while
// the server modules build with Gradle 9.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://repo.essential.gg/repository/maven-releases/")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "gg.essential.loom") {
                useModule("gg.essential:architectury-loom:${requested.version}")
            }
        }
    }
}

plugins {
    // Downloads the Java 8 toolchain when none is installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "kb-recorder"
