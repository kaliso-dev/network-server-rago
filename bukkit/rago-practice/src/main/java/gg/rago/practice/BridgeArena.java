package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * The bridge fight, open to everyone like the block placement arena: four wool platforms side by
 * side over the void, each of two colours as a top fight platform, in Ranked Bedwars gear (iron
 * armour, stone sword, wool, shears, golden apples) with real damage. A player picks the platform
 * they fight on with the compass (the least crowded one at first); after a death or a fall they
 * come back next to it, on a small pad at one of its ends, and bridge onto it. The blocks placed
 * go away after thirty seconds. The kills count in the stats of the mode.
 */
final class BridgeArena extends PracticeGame {

    /** A platform: its name, its two colours (wool data, armour), and where it is across the row. */
    static final class Platform {
        final String name;
        final ChatColor chat;
        final int first;
        final int second;
        final Color firstArmour;
        final Color secondArmour;
        final int z;

        Platform(String name, ChatColor chat, int first, Color firstArmour, int second, Color secondArmour, int z) {
            this.name = name;
            this.chat = chat;
            this.first = first;
            this.firstArmour = firstArmour;
            this.second = second;
            this.secondArmour = secondArmour;
            this.z = z;
        }

        /** The wool colour of this place of the platform: the first colour on the west half. */
        int colourAt(int x, int z) {
            return x < 0 || (x == 0 && z < 0) ? first : second;
        }
    }

    /** The platforms, the one a new player goes to on a tie first: across the row, as in the picture. */
    static final Platform[] PLATFORMS = {
        new Platform("Blue & Yellow", ChatColor.BLUE, Shapes.BLUE, Color.BLUE, Shapes.YELLOW, Color.YELLOW, 10),
        new Platform("Red & White", ChatColor.RED, Shapes.RED, Color.RED, Shapes.WHITE, Color.WHITE, 30),
        new Platform("Orange & Black", ChatColor.GOLD, Shapes.ORANGE, Color.ORANGE, Shapes.BLACK, Color.BLACK, -10),
        new Platform("Green & Lime", ChatColor.DARK_GREEN, Shapes.GREEN, Color.GREEN, Shapes.LIME, Color.LIME, -30),
    };
    /** The tips of the platforms are this far from their centre, along the row. */
    static final int HALF_LENGTH = 15;
    /** The centre of the spawn pads, this far from the centre of their platform: a short bridge to build. */
    static final int PAD = HALF_LENGTH + 5;
    private static final int DECAY_TICKS = 600;
    /** A player who just came back can't be hurt for this long, unless they hit first. */
    private static final long PROTECTION_MS = 3000L;
    static final String MENU_ITEM = ChatColor.GREEN + "Choose a platform " + ChatColor.GRAY + "(right click)";
    static final String MENU_TITLE = "Bridge fight: platforms";
    /** Where the platforms are in the menu. */
    static final int[] MENU_SLOTS = {1, 3, 5, 7};

    private final Random random = new Random();
    private final Deque<Object[]> decaying = new ArrayDeque<>();
    private final Map<UUID, Integer> platformOf = new HashMap<>();
    /** The end of their platform each player came back at: -1 west, 1 east. */
    private final Map<UUID, Integer> endOf = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();

    BridgeArena(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams);
    }

    /** Half the width of a platform at this place along it, as a top fight platform; -1 past its tips. */
    static int halfWidth(int x) {
        int along = Math.abs(x);
        return along <= 7 ? 4 : along <= 10 ? 3 : along <= 12 ? 2 : along <= 14 ? 1 : along == HALF_LENGTH ? 0 : -1;
    }

    /** The platform with the fewest players, the first one on a tie. */
    static int leastCrowded(int[] players) {
        int best = 0;
        for (int i = 1; i < players.length; i++) {
            if (players[i] < players[best]) {
                best = i;
            }
        }
        return best;
    }

    @Override
    void build() {
        for (Platform platform : PLATFORMS) {
            for (int x = -HALF_LENGTH; x <= HALF_LENGTH; x++) {
                for (int z = -halfWidth(x); z <= halfWidth(x); z++) {
                    arena.build(x, 0, platform.z + z, Material.WOOL, platform.colourAt(x, z));
                }
            }
            // the pads, each of the colour of its end
            for (int end = -1; end <= 1; end += 2) {
                int colour = end < 0 ? platform.first : platform.second;
                Shapes.floor(arena, end * PAD - 1, platform.z - 1, end * PAD + 1, platform.z + 1, 0, Material.WOOL, colour);
            }
        }
    }

    private int[] crowd() {
        int[] players = new int[PLATFORMS.length];
        for (Player player : online()) {
            Integer platform = platformOf.get(player.getUniqueId());
            if (platform != null) {
                players[platform]++;
            }
        }
        return players;
    }

    private Platform platformOf(UUID player) {
        Integer platform = platformOf.get(player);
        if (platform == null) {
            platform = leastCrowded(crowd());
            platformOf.put(player, platform);
        }
        return PLATFORMS[platform];
    }

    @Override
    Location spawnOf(UUID player) {
        Platform platform = platformOf(player);
        int end = random.nextBoolean() ? 1 : -1;
        endOf.put(player, end);
        protectedUntil.put(player, System.currentTimeMillis() + PROTECTION_MS);
        double z = platform.z + 0.5 + random.nextInt(3) - 1;
        // facing the platform
        return arena.at(end * PAD + 0.5, 1, z, end < 0 ? -90f : 90f);
    }

    @Override
    int countdownSeconds() {
        return 0;
    }

    @Override
    boolean freezes() {
        return false;
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    boolean counts() {
        return false;
    }

    @Override
    double fallY() {
        return arena.floorY() - 12;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.STONE_SWORD), new ItemStack(Material.WOOL, 64),
            new ItemStack(Material.WOOL, 64), new ItemStack(Material.SHEARS), new ItemStack(Material.GOLDEN_APPLE, 2)};
    }

    /** The colour of the end the player came back at: their wool and their armour. */
    private int woolOf(UUID player) {
        Platform platform = platformOf(player);
        return endOf.getOrDefault(player, -1) < 0 ? platform.first : platform.second;
    }

    @Override
    void armour(Player player) {
        Platform platform = platformOf(player.getUniqueId());
        ironArmour(player, endOf.getOrDefault(player.getUniqueId(), -1) < 0 ? platform.firstArmour : platform.secondArmour);
    }

    @Override
    void afterKit(Player player) {
        PlayerInventory inventory = player.getInventory();
        short colour = (short) woolOf(player.getUniqueId());
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && item.getType() == Material.WOOL) {
                item.setDurability(colour);
                inventory.setItem(slot, item);
            }
        }
        ItemStack menu = named(Material.COMPASS, 1, 0, MENU_ITEM, ChatColor.GRAY + "Now: " + platformOf(player.getUniqueId()).chat
                + platformOf(player.getUniqueId()).name);
        ItemStack there = inventory.getItem(8);
        inventory.setItem(8, menu);
        if (there != null && there.getType() != Material.AIR) {
            inventory.addItem(there);
        }
    }

    @Override
    void interact(Player player, PlayerInteractEvent event) {
        if (!isNamed(event.getItem(), Material.COMPASS, MENU_ITEM)) {
            return;
        }
        event.setCancelled(true);
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            openMenu(player);
        }
    }

    private void openMenu(Player player) {
        int[] crowd = crowd();
        Integer mine = platformOf.get(player.getUniqueId());
        ItemStack[] items = new ItemStack[9];
        for (int i = 0; i < PLATFORMS.length; i++) {
            Platform platform = PLATFORMS[i];
            String count = ChatColor.GRAY + "Players: " + ChatColor.WHITE + crowd[i];
            String what = mine != null && mine == i ? ChatColor.GREEN + "You fight here." : ChatColor.YELLOW + "Click to go there.";
            items[MENU_SLOTS[i]] = named(Material.WOOL, Math.max(1, crowd[i]), platform.first, platform.chat + platform.name,
                    count, what);
        }
        player.openInventory(new GameMenu(this, MENU_TITLE, items).getInventory());
    }

    @Override
    void menuClick(Player player, int slot) {
        int chosen = -1;
        for (int i = 0; i < MENU_SLOTS.length; i++) {
            if (MENU_SLOTS[i] == slot) {
                chosen = i;
            }
        }
        if (chosen < 0) {
            return;
        }
        player.closeInventory();
        UUID id = player.getUniqueId();
        Integer mine = platformOf.get(id);
        if (mine != null && mine == chosen) {
            player.sendMessage(ChatColor.GRAY + "You already fight on this platform.");
            return;
        }
        if (killerOf(id) != null) {
            player.sendMessage(ChatColor.RED + "You can't change platform in the middle of a fight.");
            return;
        }
        platformOf.put(id, chosen);
        streaks.remove(id);
        player.sendMessage(ChatColor.GRAY + "You go to the " + PLATFORMS[chosen].chat + PLATFORMS[chosen].name + ChatColor.GRAY
                + " platform.");
        placeAtStart(player);
        sidebars();
    }

    @Override
    boolean refills() {
        return false;
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        long now = System.currentTimeMillis();
        if (attacker != null) {
            // hitting first ends one's own protection
            protectedUntil.remove(attacker.getUniqueId());
        }
        return protectedUntil.getOrDefault(victim.getUniqueId(), 0L) <= now;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return arena.contains(block, 45, 12, 16);
    }

    @Override
    void placed(Player player, Block block) {
        decaying.addLast(new Object[] {block, tickCount});
    }

    @Override
    void tick() {
        while (!decaying.isEmpty() && tickCount - (Integer) decaying.peekFirst()[1] >= DECAY_TICKS) {
            Block block = (Block) decaying.pollFirst()[0];
            if (arena.isPlaced(block)) {
                arena.restore(block);
            }
        }
    }

    @Override
    void intro(Player player) {
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Bridge fight" + ChatColor.GRAY
                + " - four platforms, everyone in one arena, real damage. You come back next to your platform:"
                + " bridge onto it and fight. Your blocks go away after 30 seconds.");
        player.sendMessage(ChatColor.GRAY + "The compass changes platform (not in a fight). /spawn takes you back to the lobby.");
        for (Player other : online()) {
            if (!other.equals(player)) {
                other.sendMessage(ChatColor.GRAY + player.getName() + " joined the bridge fight.");
            }
        }
    }

    @Override
    void fell(Player player) {
        out(player);
    }

    @Override
    void lethal(Player victim, Player killer) {
        out(victim);
    }

    /** The player is out (a fall, a death): the kill to whoever hit them last, and back next to their platform. */
    private void out(Player victim) {
        UUID by = killerOf(victim.getUniqueId());
        Player killer = by == null ? null : Bukkit.getPlayer(by);
        if (killer != null && host.gameOf(by) == this && !killer.equals(victim)) {
            plugin.lobby().recordKill(mode.id(), killer.getUniqueId(), victim.getUniqueId());
            kills.merge(by, 1, Integer::sum);
            int streak = streaks.merge(by, 1, Integer::sum);
            broadcast(ChatColor.RED + victim.getName() + ChatColor.GRAY + " was killed by " + ChatColor.GREEN
                    + killer.getName() + (streak >= 3 ? ChatColor.GOLD + " (" + streak + " in a row)" : ""), null);
            sound(killer, Sound.ORB_PICKUP, 1.2f);
        } else {
            victim.sendMessage(ChatColor.GRAY + "You fell.");
        }
        streaks.remove(victim.getUniqueId());
        forgetHits(victim.getUniqueId());
        placeAtStart(victim);
        sidebars();
    }

    @Override
    void leave(Player player, boolean toLobby) {
        UUID id = player.getUniqueId();
        host.release(id);
        streaks.remove(id);
        platformOf.remove(id);
        endOf.remove(id);
        protectedUntil.remove(id);
        if (phase != Phase.ENDED) {
            if (online().isEmpty()) {
                finish(-1, null);
            } else {
                broadcast(ChatColor.GRAY + player.getName() + " left the bridge fight.", null);
                sidebars();
            }
        }
        if (toLobby) {
            plugin.lobby().sendToLobby(player);
        }
    }

    @Override
    List<String> sidebar(UUID viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.AQUA + "Bridge fight");
        lines.add("");
        Platform platform = platformOf(viewer);
        lines.add(Duel.cut(ChatColor.WHITE + "Platform: " + platform.chat + platform.name));
        lines.add(ChatColor.WHITE + "On it: " + ChatColor.GREEN + crowd()[platformOf.get(viewer)]
                + ChatColor.GRAY + " / " + online().size());
        lines.add(ChatColor.WHITE + "Your kills: " + ChatColor.GREEN + kills.getOrDefault(viewer, 0));
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streaks.getOrDefault(viewer, 0));
        UUID top = null;
        int most = 0;
        for (Player player : online()) {
            int count = kills.getOrDefault(player.getUniqueId(), 0);
            if (count > most) {
                most = count;
                top = player.getUniqueId();
            }
        }
        if (top != null) {
            lines.add("");
            lines.add(Duel.cut(ChatColor.WHITE + "Top: " + ChatColor.GOLD + nameOf(top) + ChatColor.GRAY + " " + most + " kills"));
        }
        lines.add("");
        lines.add(ChatColor.WHITE + "Ping: " + Ping.text(Bukkit.getPlayer(viewer)));
        return lines;
    }
}
