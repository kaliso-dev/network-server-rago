// RagoLobby: the main lobby (spawn, protection, hotbar, mode selector, local queues, scoreboard).
plugins {
    id("rago.bukkit-plugin-conventions")
}

dependencies {
    // Copied into the jar: the game plugins (rago-practice, rago-bedwars) compile against it and
    // load its classes from this plugin, so that both sides share the same MatchProposal class.
    implementation(project(":rago-game-model"))
    // Same exclusion as the engine: bungeecord-chat 1.8-SNAPSHOT is no longer served anywhere and
    // the plugin only sends plain text (see docs/compatibility.md).
    compileOnly(libs.spigot.api) { exclude(group = "net.md-5", module = "bungeecord-chat") }
    // The packetevents plugin, installed next to RagoLobby (depend in plugin.yml), sends the NPC
    // bodies and the per-player holograms. Not copied into the jar.
    compileOnly(libs.packetevents.spigot)
    // The tests read YAML with the API's own YamlConfiguration, as the server does.
    testImplementation(libs.spigot.api) { exclude(group = "net.md-5", module = "bungeecord-chat") }
    testImplementation(libs.packetevents.spigot)
}
