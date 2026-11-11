package gg.rago.bedwars;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Bed Fight, 1v1 or 2v2, as on Minemen: two islands face to face, each with its bed under the
 * defense the map has on it (wood and end stone on the owner's Bed Fight map), which the players
 * break as in a game. No forge, no shop, no upgrade, no diamond: everyone has the mode's kit (the
 * lobby's kit editor; else a stone sword, a wooden pickaxe and axe, shears and two stacks of wool)
 * in leather armour of their colour, at each life. A player comes back while their bed stands,
 * {@value #RESPAWN_SECONDS} seconds after a death at most; the last team standing wins. The beds go at {@value #BEDS_GONE_SECONDS} seconds,
 * and the game ends in a draw at {@value #GAME_END_SECONDS}. Main thread only.
 */
final class BedFightGame extends BedwarsGame {

    /** The kit when the lobby has none for the mode: the materials, with their amount. */
    static final String[] KIT = {"STONE_SWORD", "WOOD_PICKAXE", "WOOD_AXE", "SHEARS", "WOOL 64", "WOOL 64"};
    static final int BEDS_GONE_SECONDS = 600;
    static final int GAME_END_SECONDS = 900;
    /** The longest wait before a new life (reapparition-secondes when shorter). */
    static final int RESPAWN_SECONDS = 3;

    BedFightGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides) {
        super(plugin, host, instance, modeId, modeName, sides);
    }

    @Override
    int respawnSeconds() {
        return respawnOf(super.respawnSeconds());
    }

    /** Blocks go everywhere, the spawns too: a respawn clears its place ({@link #respawn}). */
    @Override
    boolean protectsSpawns() {
        return false;
    }

    /** The two blocks where the player comes back (feet and head) go first: nobody is walled in. */
    @Override
    void respawn(Player player, Gamer gamer) {
        Block feet = spawnOf(gamer).getBlock();
        for (Block block : new Block[] {feet, feet.getRelative(BlockFace.UP)}) {
            if (block.getType() != Material.AIR && block.getType() != Material.BED_BLOCK) {
                block.setType(Material.AIR);
            }
        }
        super.respawn(player, gamer);
    }

    /** The wait before a new life: the server's, {@value #RESPAWN_SECONDS} seconds at most. */
    static int respawnOf(int server) {
        return Math.min(server, RESPAWN_SECONDS);
    }

    /** No forge, no shop, no diamond nor emerald. */
    @Override
    boolean economy() {
        return false;
    }

    @Override
    boolean upgrades() {
        return false;
    }

    /** The defense the map has on each bed is the team's own: breakable, as if built by the players. */
    @Override
    void setUp() {
        for (Team team : teams) {
            if (team.points != null && team.points.bed != null) {
                Defense.free(instance, team.points.bed.blockIn(instance.world()));
            }
        }
    }

    /** Leather armour of the team's colour and the mode's kit, at each life; the pickaxe and the axe with Efficiency I. */
    @Override
    void giveKit(Player player, Gamer gamer) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        gamer.armor = 0;
        wearArmor(player, gamer);
        ItemStack[] kit = lobbyKit(player, gamer);
        if (kit != null) {
            inventory.setContents(kit);
        } else {
            for (ItemStack item : defaultKit()) {
                inventory.addItem(kitItem(item, gamer));
            }
        }
        ItemStack[] contents = inventory.getContents();
        efficient(contents);
        inventory.setContents(contents);
        player.updateInventory();
    }

    /** Efficiency I on every pickaxe and axe of these items (the rest as they are). */
    static void efficient(ItemStack[] items) {
        for (ItemStack item : items) {
            if (item != null && digs(item.getType()) && item.getEnchantmentLevel(Enchantment.DIG_SPEED) < 1) {
                item.addUnsafeEnchantment(Enchantment.DIG_SPEED, 1);
            }
        }
    }

    /** True for a pickaxe or an axe. */
    static boolean digs(Material type) {
        String name = type.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_AXE");
    }

    /** The items of {@link #KIT}, not yet in the team's colour. */
    static ItemStack[] defaultKit() {
        return items(KIT);
    }

    /** The items of a kit written as materials, each with its amount ("WOOL 64"). */
    static ItemStack[] items(String[] kit) {
        ItemStack[] items = new ItemStack[kit.length];
        for (int i = 0; i < kit.length; i++) {
            String[] parts = kit[i].split(" ");
            items[i] = new ItemStack(Material.valueOf(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
        }
        return items;
    }

    /** No generator to upgrade: the end of the beds, then the end of the game. */
    @Override
    void events() {
        if (seconds == BEDS_GONE_SECONDS) {
            for (Team team : teams) {
                if (team.bed) {
                    destroyBed(team);
                }
            }
            broadcast(ChatColor.RED + "" + ChatColor.BOLD + "All beds have been destroyed!", Sound.ENDERDRAGON_GROWL);
            title(ChatColor.RED + "" + ChatColor.BOLD + "BEDS DESTROYED", ChatColor.WHITE + "Nobody respawns any more!");
        } else if (seconds >= GAME_END_SECONDS) {
            broadcast(ChatColor.YELLOW + "Time is up: the game ends in a draw.", Sound.NOTE_BASS);
            end(null);
        }
    }

    /** The sidebar of a game, with the bed fight's own clock on top. */
    @Override
    List<String> lines(Gamer viewer) {
        List<String> lines = super.lines(viewer);
        if (phase != Phase.STARTING && !lines.isEmpty()) {
            lines.set(0, nextEvent(seconds));
        }
        return lines;
    }

    /** "Bed Gone in 9:58", then "Game End in 4:12". */
    static String nextEvent(int seconds) {
        if (seconds < BEDS_GONE_SECONDS) {
            return ChatColor.WHITE + "Bed Gone in " + ChatColor.GREEN + clock(BEDS_GONE_SECONDS - seconds);
        }
        if (seconds < GAME_END_SECONDS) {
            return ChatColor.WHITE + "Game End in " + ChatColor.GREEN + clock(GAME_END_SECONDS - seconds);
        }
        return ChatColor.WHITE + "Game End";
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + modeName + ChatColor.GREEN + " on " + ChatColor.WHITE
                + instance.map().world() + ChatColor.GREEN + ": you are in the " + gamer.team.color.coloured()
                + ChatColor.GREEN + " team. Break the other bed through its defense and protect yours: you come back"
                + " while it stands.");
        player.sendMessage(ChatColor.GRAY + "No shop, no forge: your kit at each life (edit it in the lobby). The beds go at "
                + clock(BEDS_GONE_SECONDS) + ", a draw at " + clock(GAME_END_SECONDS) + ". /spawn takes you back to the lobby.");
    }
}
