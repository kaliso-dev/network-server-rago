package gg.rago.lobby.api;

import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * The lobby as the game plugins see it, from Bukkit's services manager:
 * {@code getServer().getServicesManager().load(LobbyService.class)}. Main thread only.
 *
 * <p>On the local test server the lobby and the games run on one server and talk through this
 * interface. On the network the queues move to the proxy (rago-proxy) and the games receive their
 * matches from it; the {@link MatchHost} side of a game plugin stays the same.
 */
public interface LobbyService {

    /**
     * Makes a mode of the lobby's catalogue playable: its queue opens in the selector. Returns
     * false if the catalogue has no such mode (see the lobby's config.yml). A mode has one host:
     * a second registration replaces the first.
     */
    boolean registerHost(Plugin plugin, String modeId, MatchHost host);

    /** Closes the queues of every mode this plugin hosts, when it is disabled. */
    void unregisterHosts(Plugin plugin);

    /**
     * Brings the player back to the lobby spawn, as on joining the server: lobby items, adventure
     * mode, full health, the lobby scoreboard. The player no longer belongs to a game.
     */
    void sendToLobby(Player player);

    /** True while the player belongs to a game started by a {@link MatchHost}. */
    boolean isInGame(Player player);

    /**
     * A game of this mode is over: one side won, the other lost (a forfeit is a loss). The lobby
     * counts it in the stats, the leaderboards and, for a ranked mode, the ELO. Players may have
     * left the server already.
     */
    void recordMatch(String modeId, List<UUID> winners, List<UUID> losers);

    /**
     * A kill in a game of this mode (in a sumo: the victim fell off and the killer got the point).
     * The killer gets a kill and some Network XP, the victim a death (null when nobody died).
     * Counted in the stats and the kill leaderboards, of the day, the week and ever.
     */
    void recordKill(String modeId, UUID killer, UUID victim);

    /**
     * The kit of this mode (kit: in the lobby's config.yml) as the player arranged it in the kit
     * editor, the book of the lobby hotbar: 36 stacks, the hotbar (0 to 8) then the inventory (9 to
     * 35), null for an empty slot. A game gives it with {@code player.getInventory().setContents(kit)}
     * when it starts. Null when the mode has no kit.
     */
    ItemStack[] kit(Player player, String modeId);

    /**
     * The quick buy of the Bed Wars shop the player copied from Hypixel (the emerald of the kit
     * editor, or /hypixel import [name]): 21 Hypixel item ids, three rows of seven as in the
     * Hypixel shop, such as "wool", "stone_sword" or "speed_ii_potion_(45_seconds)"; null for an
     * empty slot. For the Bed Wars modes and Fast Shop, which lay out their shop with it. Null when
     * the player imported nothing.
     */
    List<String> hypixelQuickBuy(Player player);

    /**
     * The hotbar manager the player copied from Hypixel: for each of the 9 hotbar slots, the kind of
     * item that goes there ("Melee", "Blocks", "Tools", "Ranged", "Potions", "Utility", "Compass"),
     * null for a free slot. Null when the player imported nothing, or had no hotbar manager.
     */
    List<String> hypixelHotbar(Player player);

    /**
     * The lines a game shows in the player's sidebar while they play, top to bottom, between the
     * lobby's title and its address: 12 lines at most, 40 characters each, colour codes with §, ""
     * for a blank line. Null gives back the lobby's own game lines (the mode and the ping). The
     * lobby forgets them when the player comes back to it.
     */
    void setGameSidebar(Player player, List<String> lines);

    /**
     * What shows before the player's name, above their head and in the tab list, while they play:
     * their team as Hypixel shows it in Bed Wars ("§c§lR §c": the team's letter, then the name in
     * its colour). 16 characters at most, colour codes with §. Null gives back the player's rank.
     * The lobby forgets it when the player comes back to it. A lobby without name tags ignores it.
     */
    default void setGameNameTag(Player player, String prefix) {
    }
}
