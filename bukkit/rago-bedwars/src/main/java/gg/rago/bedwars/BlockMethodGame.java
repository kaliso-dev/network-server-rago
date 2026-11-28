package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

/**
 * Block Method 1v1 (the owner's ask of 28/11): Bed Fight as a practice, the player alone against a
 * RagoBot much weaker than the one of the parties, which came for them (ragobot.seul in the lobby's
 * config.yml). Bed Fight's kit, 20 HP, its damage, its maps' defenses; nobody wins: each death, of
 * one or the other, starts the round again, the map as at the start (the blocks placed gone, the
 * blocks broken and the beds back), both on their spawn with the kit. The player starts it again
 * themselves with the blaze rod of their hotbar, and goes on on another map of the mode with its
 * map. No clock, no result, no stats against the RagoBot; the practice is over when one of the two
 * leaves (/spawn, the RagoBot gone). Main thread only.
 */
final class BlockMethodGame extends BedFightGame {

    static final Material RESET_ITEM = Material.BLAZE_ROD;
    static final Material MAPS_ITEM = Material.EMPTY_MAP;
    /** The slots of the two items in the hotbar at first (else the first free ones). */
    static final int RESET_SLOT = 8;
    static final int MAPS_SLOT = 7;
    /** The blaze rod resets the round at most this often. */
    static final long RESET_COOLDOWN_MILLIS = 1_000L;
    /** A change of map waits this long at most for a copy of the map to be ready. */
    static final int MOVE_WAIT_SECONDS = 30;

    /** The team of the player alone, who has the blaze rod and the map (the first side of the match). */
    private final Team alone;
    private int round = 1;
    private final Map<UUID, Integer> deaths = new HashMap<>();
    /** For each player, the slots their blaze rod and their map had at their last death (-1: none). */
    private final Map<UUID, int[]> toolSlots = new HashMap<>();
    private long lastReset;
    private BukkitTask moving;

    BlockMethodGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides) {
        super(plugin, host, instance, modeId, modeName, sides);
        alone = teams.get(0);
    }

    /** Goes on from a practice on another map: its round, kills and deaths. */
    void carryFrom(BlockMethodGame old) {
        round = old.round;
        deaths.putAll(old.deaths);
        toolSlots.putAll(old.toolSlots);
        for (Gamer gamer : gamers.values()) {
            Gamer before = old.gamers.get(gamer.id);
            if (before != null) {
                gamer.kills = before.kills;
            }
        }
    }

    /** At once: a practice. */
    @Override
    int countdownSeconds() {
        return 0;
    }

    /** No end of the beds, no end of the game: the practice lasts while both are there. */
    @Override
    void events() {
    }

    /** A practice: not kept for /replay. */
    @Override
    boolean recorded() {
        return false;
    }

    /** A practice against a RagoBot: no death in the stats. */
    @Override
    boolean countsDeaths() {
        return false;
    }

    /** Over when one of the two is gone: everyone back to the lobby, no result. */
    @Override
    void checkEnd() {
        if (phase != Phase.ENDED && present().size() < 2) {
            close();
        }
    }

    /**
     * A death, of the player or of the RagoBot (a hit, the void): the kill counted in the game only,
     * and the round starts again at once.
     */
    @Override
    void kill(Player victim, Player killer, boolean voidDeath) {
        Gamer gamer = gamers.get(victim.getUniqueId());
        if (gamer == null || phase != Phase.PLAYING || gamer.respawning || gamer.eliminated) {
            super.kill(victim, killer, voidDeath);
            return;
        }
        Gamer author = killer == null ? null : gamers.get(killer.getUniqueId());
        if (author == null && gamer.lastDamager != null
                && System.currentTimeMillis() - gamer.lastDamageAt < CREDIT_MS) {
            author = gamers.get(gamer.lastDamager);
        }
        if (author != null && (author.team == gamer.team || author == gamer)) {
            author = null;
        }
        deaths.merge(gamer.id, 1, Integer::sum);
        if (author != null) {
            author.kills++;
        }
        String how = voidDeath ? (author == null ? " fell into the void." : " was knocked into the void by ")
                : (author == null ? " died." : " was killed by ");
        broadcast(gamer.coloured() + ChatColor.GRAY + how + (author == null ? "" : author.coloured() + ChatColor.GRAY + "."),
                deathSound());
        boolean won = author != null && author.team == alone;
        reset(won ? ChatColor.GREEN + "" + ChatColor.BOLD + "KILL!" : ChatColor.RED + "" + ChatColor.BOLD + "DIED",
                ChatColor.GRAY + "Round " + (round + 1));
    }

    /** The round again: the map as at the start, the beds back, both on their spawn with the kit. */
    void reset(String title, String subtitle) {
        if (phase == Phase.ENDED) {
            return;
        }
        for (Gamer gamer : gamers.values()) {
            Player one = Bukkit.getPlayer(gamer.id);
            if (one != null && present(gamer)) {
                // the hotbar as they arranged it, for the kit again
                beforeDeath(one, gamer);
            }
        }
        instance.rollback();
        removeUnusedBeds();
        for (Team team : teams) {
            team.bed = true;
        }
        // the defense of the beds is breakable again (the rollback forgot what was freed)
        setUp();
        round++;
        for (Gamer gamer : gamers.values()) {
            cancel(gamer.respawn);
            gamer.respawn = null;
            gamer.respawning = false;
            gamer.eliminated = false;
            gamer.lastDamager = null;
            Player one = Bukkit.getPlayer(gamer.id);
            if (one != null && present(gamer)) {
                place(one, gamer);
                one.setFallDistance(0f);
                one.sendTitle(title, subtitle);
            }
        }
        phase = Phase.PLAYING;
        sidebars();
    }

    /** A bed broken: the step of the pattern done, nothing in the stats; the round goes on until a death. */
    @Override
    void breakBed(Player breaker, Team team) {
        Gamer gamer = gamers.get(breaker.getUniqueId());
        if (gamer == null || !team.bed) {
            return;
        }
        gamer.beds++;
        destroyBed(team);
        broadcast(ChatColor.WHITE + "" + ChatColor.BOLD + "BED DESTRUCTION > " + team.color.coloured() + " Bed"
                + ChatColor.GRAY + " was destroyed by " + gamer.coloured() + ChatColor.GRAY + "!", Sound.ENDERDRAGON_GROWL);
        sidebars();
    }

    @Override
    void giveKit(Player player, Gamer gamer) {
        super.giveKit(player, gamer);
        if (gamer.team != alone) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int[] slots = toolSlots.get(gamer.id);
        put(inventory, tool(RESET_ITEM, ChatColor.GOLD + "" + ChatColor.BOLD + "Reset" + ChatColor.GRAY + " (right click)",
                "The round again, the map as at the start."), slots == null || slots[0] < 0 ? RESET_SLOT : slots[0]);
        put(inventory, tool(MAPS_ITEM, ChatColor.AQUA + "" + ChatColor.BOLD + "Maps" + ChatColor.GRAY + " (right click)",
                "Go on on another map."), slots == null || slots[1] < 0 ? MAPS_SLOT : slots[1]);
        player.updateInventory();
    }

    /** Where the blaze rod and the map were, for the next kit; then Bed Fight's own layout. */
    @Override
    void beforeDeath(Player victim, Gamer gamer) {
        ItemStack[] contents = victim.getInventory().getContents();
        toolSlots.put(gamer.id, new int[] {slotOf(contents, RESET_ITEM), slotOf(contents, MAPS_ITEM)});
        super.beforeDeath(victim, gamer);
    }

    /** The slot holding an item of this kind, -1 if none. */
    static int slotOf(ItemStack[] contents, Material type) {
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] != null && contents[slot].getType() == type) {
                return slot;
            }
        }
        return -1;
    }

    /** The slot an item goes to: the one wanted if free, else the first free one, else -1. */
    static int freeSlot(ItemStack[] contents, int wanted) {
        if (wanted >= 0 && wanted < contents.length && empty(contents[wanted])) {
            return wanted;
        }
        for (int slot = 0; slot < contents.length; slot++) {
            if (empty(contents[slot])) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private static void put(PlayerInventory inventory, ItemStack item, int wanted) {
        int slot = freeSlot(inventory.getContents(), wanted);
        if (slot >= 0) {
            inventory.setItem(slot, item);
        }
    }

    private static ItemStack tool(Material type, String name, String lore) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(ChatColor.GRAY + lore));
        item.setItemMeta(meta);
        return item;
    }

    /** True for the blaze rod and the map of the practice: they stay in the hotbar. */
    static boolean isTool(Material type) {
        return type == RESET_ITEM || type == MAPS_ITEM;
    }

    /** A right click with this item: true if it was the blaze rod or the map (the click goes no further). */
    boolean use(Player clicker, ItemStack item) {
        if (item == null || !isTool(item.getType())) {
            return false;
        }
        Gamer gamer = gamers.get(clicker.getUniqueId());
        if (gamer == null || gamer.team != alone || phase == Phase.ENDED) {
            return true;
        }
        if (item.getType() == MAPS_ITEM) {
            BlockMethodMenu.open(clicker, this, host.blockMethodMaps(modeId), instance.map().world());
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastReset < RESET_COOLDOWN_MILLIS || phase != Phase.PLAYING) {
            return true;
        }
        lastReset = now;
        clicker.playSound(clicker.getLocation(), Sound.NOTE_PLING, 1f, 1.5f);
        reset(ChatColor.YELLOW + "" + ChatColor.BOLD + "RESET", ChatColor.GRAY + "Round " + (round + 1));
        clicker.sendMessage(ChatColor.YELLOW + "Round " + round + ": the map as at the start.");
        return true;
    }

    /** The player chose a map in the menu: the practice goes on there, once a copy of it is ready. */
    void chooseMap(Player clicker, String world) {
        if (phase == Phase.ENDED) {
            return;
        }
        if (world.equalsIgnoreCase(instance.map().world())) {
            clicker.sendMessage(ChatColor.YELLOW + "You are on " + world + " already.");
            return;
        }
        BedwarsMap map = null;
        for (BedwarsMap one : host.blockMethodMaps(modeId)) {
            if (one.world().equalsIgnoreCase(world)) {
                map = one;
            }
        }
        if (map == null) {
            clicker.sendMessage(ChatColor.RED + world + " cannot be played now.");
            return;
        }
        cancel(moving);
        clicker.sendMessage(ChatColor.YELLOW + "To " + ChatColor.WHITE + map.world() + ChatColor.YELLOW + "...");
        final BedwarsMap target = map;
        final long until = System.currentTimeMillis() + MOVE_WAIT_SECONDS * 1000L;
        moving = repeat(0L, 20L, () -> {
            if (phase == Phase.ENDED || host.moveTo(this, target)) {
                cancel(moving);
                return;
            }
            if (System.currentTimeMillis() > until) {
                cancel(moving);
                clicker.sendMessage(ChatColor.RED + "No copy of " + target.world() + " is ready: try again in a moment.");
            }
        });
    }

    /** The players of each team still here, for the same practice on another map. */
    List<List<Player>> sides() {
        List<List<Player>> sides = new ArrayList<>();
        for (Team team : teams) {
            List<Player> side = new ArrayList<>();
            for (UUID member : team.members) {
                Player one = Bukkit.getPlayer(member);
                if (one != null && present(gamers.get(member))) {
                    side.add(one);
                }
            }
            if (side.isEmpty()) {
                return null;
            }
            sides.add(side);
        }
        return sides;
    }

    @Override
    List<String> lines(Gamer viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.WHITE + "Map: " + ChatColor.GREEN + instance.map().world());
        lines.add(ChatColor.WHITE + "Round: " + ChatColor.GREEN + round);
        lines.add("");
        lines.add(ChatColor.WHITE + "Kills: " + ChatColor.GREEN + viewer.kills);
        lines.add(ChatColor.WHITE + "Deaths: " + ChatColor.GREEN + deaths.getOrDefault(viewer.id, 0));
        lines.add("");
        lines.add(ChatColor.GOLD + "Blaze rod" + ChatColor.GRAY + ": reset");
        lines.add(ChatColor.AQUA + "Map" + ChatColor.GRAY + ": change map");
        return lines;
    }

    @Override
    void welcome(Player one, Gamer gamer) {
        one.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + modeName + ChatColor.GREEN + " on " + ChatColor.WHITE
                + instance.map().world() + ChatColor.GREEN + ": a practice against RagoBot, as a real Bed Fight."
                + " Each death starts the round again, the map as at the start.");
        one.sendMessage(ChatColor.GRAY + "Blaze rod: the round again. Map: another map. /spawn takes you back to the lobby.");
    }
}
