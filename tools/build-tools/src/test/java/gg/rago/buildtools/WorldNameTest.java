package gg.rago.buildtools;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WorldNameTest {

    @Test
    void acceptsPlainNames() {
        assertNull(WorldName.problem("lobby"));
        assertNull(WorldName.problem("bedwars-1v1_map2"));
        assertNull(WorldName.problem("a"));
        assertNull(WorldName.problem("abcdefghijklmnopqrstuvwxyz012345"));
    }

    @Test
    void refusesWhatFileSystemsDisagreeOn() {
        assertNotNull(WorldName.problem(""));
        assertNotNull(WorldName.problem(null));
        assertNotNull(WorldName.problem("Lobby"));
        assertNotNull(WorldName.problem("lobby principal"));
        assertNotNull(WorldName.problem("château"));
        assertNotNull(WorldName.problem("../world"));
        assertNotNull(WorldName.problem("a/b"));
        assertNotNull(WorldName.problem("a\\b"));
        assertNotNull(WorldName.problem("abcdefghijklmnopqrstuvwxyz0123456"));
    }

    @Test
    void refusesReservedNames() {
        assertNotNull(WorldName.problem("plugins"));
        assertNotNull(WorldName.problem("logs"));
        assertNotNull(WorldName.problem("con"));
        assertNotNull(WorldName.problem("nul"));
    }
}
