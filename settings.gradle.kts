pluginManagement {
    includeBuild("build-logic")
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralMirror"
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // Maven Central rate-limits CI machines; Google's mirror serves the same files.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralMirror"
        }
        mavenCentral()
        // Spigot API 1.8.8 is only published on SpigotMC's Nexus.
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
            name = "SpigotMC"
            content {
                includeGroup("org.spigotmc")
                includeGroup("org.bukkit")
            }
        }
        // PacketEvents is only published on CodeMC's repository.
        maven("https://repo.codemc.io/repository/maven-releases/") {
            name = "CodeMC"
            content {
                includeGroup("com.github.retrooper")
            }
        }
    }
}

rootProject.name = "network-server-rago"

fun module(name: String, dir: String) {
    include(":$name")
    project(":$name").projectDir = file(dir)
}

module("rago-combat-model", "combat/rago-combat-model")
module("rago-combat-engine", "combat/rago-combat-engine")
module("rago-game-model", "game/rago-game-model")
module("rago-lobby", "bukkit/rago-lobby")
module("rago-practice", "bukkit/rago-practice")
module("kb-control", "tools/kb-control")
module("rago-build-tools", "tools/build-tools")
