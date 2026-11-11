// RagoCombatEngine: the Bukkit plugin that applies a knockback profile on 1.8.8 servers.
plugins {
    id("rago.bukkit-plugin-conventions")
}

dependencies {
    implementation(project(":rago-combat-model"))
    // spigot-api 1.8.8 still depends on net.md-5:bungeecord-chat:1.8-SNAPSHOT, which neither
    // SpigotMC's repository nor Maven Central serves any more. The plugin only sends plain text
    // and never uses the BungeeCord chat API, so it is left out (see docs/compatibility.md).
    compileOnly(libs.spigot.api) { exclude(group = "net.md-5", module = "bungeecord-chat") }
    // The tests read YAML with the API's own YamlConfiguration, as the server does.
    testImplementation(libs.spigot.api) { exclude(group = "net.md-5", module = "bungeecord-chat") }
}
