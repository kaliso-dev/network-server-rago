// RagoPractice: the practice modes played from the lobby, starting with the sumo duel.
plugins {
    id("rago.bukkit-plugin-conventions")
}

dependencies {
    // Not copied into the jar: RagoLobby provides them at run time (plugin.yml depends on it), so
    // that this plugin and the lobby share the same LobbyService and MatchProposal classes.
    compileOnly(project(":rago-lobby"))
    compileOnly(project(":rago-game-model"))
    testImplementation(project(":rago-game-model"))
    // Same exclusion as the engine: bungeecord-chat 1.8-SNAPSHOT is no longer served anywhere and
    // the plugin only sends plain text (see docs/compatibility.md).
    compileOnly(libs.spigot.api) { exclude(group = "net.md-5", module = "bungeecord-chat") }
    testImplementation(libs.spigot.api) { exclude(group = "net.md-5", module = "bungeecord-chat") }
}
