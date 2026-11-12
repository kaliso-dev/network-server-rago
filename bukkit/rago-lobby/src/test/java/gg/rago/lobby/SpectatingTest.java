package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** /spectate: who may watch whom, and a watcher counted in the game but only to watch it. */
class SpectatingTest {

    @Test
    void friendsWatchEachOtherTheStaffAndTheCreatorsAnyGame() {
        assertTrue(Spectating.mayWatch(false, false, true, false), "a friend");
        assertFalse(Spectating.mayWatch(false, false, false, false), "a stranger");
        assertTrue(Spectating.mayWatch(true, true, false, false), "the staff");
        assertTrue(Spectating.mayWatch(false, true, false, false), "a content creator");
    }

    @Test
    void aNickStaysHiddenButFromTheStaffAndTheFriends() {
        assertFalse(Spectating.mayWatch(false, true, false, true), "a creator would see who plays under the nick");
        assertTrue(Spectating.mayWatch(true, true, false, true));
        assertTrue(Spectating.mayWatch(false, false, true, true));
    }

    @Test
    void aWatcherIsInTheGameOnlyToWatchIt() {
        LobbyState state = new LobbyState(null, null);
        UUID watcher = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        state.enterWatch(watcher, "boxing", target);
        assertEquals("boxing", state.gameOf(watcher), "in the game of the mode: /spawn goes through its host");
        assertTrue(state.isWatching(watcher));
        assertEquals(target, state.targetOf(watcher), "the player they came for: their sidebar");
        state.leaveGame(watcher);
        assertFalse(state.isWatching(watcher));
        assertNull(state.targetOf(watcher));
        assertNull(state.gameOf(watcher));
        state.enterWatch(watcher, "sumo", target);
        state.forget(watcher);
        assertFalse(state.isWatching(watcher), "gone from the server");
        assertNull(state.targetOf(watcher));
        UUID player = UUID.randomUUID();
        state.enterGame(player, "sumo");
        assertFalse(state.isWatching(player), "one who plays");
        assertNull(state.targetOf(player));
    }

    @Test
    void theContentCreatorsMaySpectateAnyGame() throws Exception {
        YamlConfiguration bundled;
        try (Reader reader = new InputStreamReader(SpectatingTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            bundled = YamlConfiguration.loadConfiguration(reader);
        }
        assertTrue(bundled.getStringList("rangs.media.permissions").contains(Spectating.ANY_GAME));
        YamlConfiguration plugin;
        try (Reader reader = new InputStreamReader(SpectatingTest.class.getResourceAsStream("/plugin.yml"), StandardCharsets.UTF_8)) {
            plugin = YamlConfiguration.loadConfiguration(reader);
        }
        // the dots of the permissions are paths for Bukkit: rago.staff is rago, then staff
        assertTrue(plugin.getBoolean("permissions.rago.staff.children.rago.perk.spectate"), "the staff has it");
        assertFalse(plugin.getBoolean("permissions.rago.perk.spectate.default", true), "nobody else by default");
        assertEquals("/spectate <player>", plugin.getString("commands.spectate.usage"));
    }
}
