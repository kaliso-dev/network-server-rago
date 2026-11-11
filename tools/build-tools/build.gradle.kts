// RagoBuildTools: building tools for the local test server (tools/local-server). Never deployed
// on a production server.
plugins {
    id("rago.bukkit-plugin-conventions")
}

dependencies {
    // Same exclusion as the engine: bungeecord-chat 1.8-SNAPSHOT is no longer served anywhere and
    // the plugin only sends plain text (see docs/compatibility.md).
    compileOnly(libs.spigot.api) { exclude(group = "net.md-5", module = "bungeecord-chat") }
    // The tests read and write YAML with the API's own YamlConfiguration, as the server does.
    testImplementation(libs.spigot.api) { exclude(group = "net.md-5", module = "bungeecord-chat") }
}
