package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** /ping [player] (the owner's ask of 22/11): the command, and its ping coloured as the games' sidebars. */
class PingCommandTest {

    @Test
    void thePingIsGreenThenYellowThenRedAsInTheGames() {
        assertEquals(ChatColor.GREEN + "0 ms", NetworkCommands.pingText(0));
        assertEquals(ChatColor.GREEN + "79 ms", NetworkCommands.pingText(79));
        assertEquals(ChatColor.YELLOW + "80 ms", NetworkCommands.pingText(80));
        assertEquals(ChatColor.YELLOW + "149 ms", NetworkCommands.pingText(149));
        assertEquals(ChatColor.RED + "150 ms", NetworkCommands.pingText(150));
        assertEquals(ChatColor.GRAY + "?", NetworkCommands.pingText(-1));
    }

    @Test
    void pingIsACommandOfTheLobby() throws Exception {
        YamlConfiguration plugin;
        try (Reader reader = new InputStreamReader(PingCommandTest.class.getResourceAsStream("/plugin.yml"), StandardCharsets.UTF_8)) {
            plugin = YamlConfiguration.loadConfiguration(reader);
        }
        assertEquals("/ping [player]", plugin.getString("commands.ping.usage"));
        assertTrue(Arrays.asList(NetworkCommands.NAMES).contains("ping"), "its executor is set");
    }
}
