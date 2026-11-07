// RagoCombatEngine: the Bukkit plugin that applies a knockback profile on 1.8.8 servers.
plugins {
    id("rago.bukkit-plugin-conventions")
}

dependencies {
    implementation(project(":rago-combat-model"))
    compileOnly(libs.spigot.api)
    // The tests read YAML with the API's own YamlConfiguration, as the server does.
    testImplementation(libs.spigot.api)
}
