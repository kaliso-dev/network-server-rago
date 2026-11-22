package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

/** The line the players of a game read when someone spectates it (the owner's ask of 22/11). */
class WatchersTest {

    @Test
    void aSpectatorIsAnnouncedInRedAndBold() {
        String red = ChatColor.RED + "" + ChatColor.BOLD;
        assertEquals(red + "Sofiene" + red + " is now spectating your game.",
                Watchers.spectatingLine(ChatColor.GOLD + "Sofiene" + ChatColor.RESET));
    }
}
