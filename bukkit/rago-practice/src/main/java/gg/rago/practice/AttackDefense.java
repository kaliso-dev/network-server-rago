package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Attack and defense, as a rush of a solo Bed Wars game on Hypixel: the defender is on their
 * island, next to their bed with no defense, and the attacker is already on a bridge a few blocks
 * away, three blocks of void left to bridge. The defender must cover the bed and hold; the
 * attacker must break it. The island is a solo island of one of the server's Bed Wars maps (see
 * {@link Base}), or a plain one. The defender comes back 3 seconds after a death, as with a bed;
 * the attacker who dies has failed the rush. Each round has its time; the sides swap each round:
 * after the rounds, the side that broke more beds wins, and with as many, the one that broke them
 * faster.
 */
final class AttackDefense extends Duel {

    /**
     * The defender's island: the copy of a solo island (or null for a plain one) whose foot of the
     * bed lands on the centre of the zone, and from it, the head of the bed, where the defender
     * starts, and the attacker's bridge, from its far end to its near end (the attacker starts on
     * the far end).
     */
    static final class Base {
        final MapCopy copy;
        final int[] head;
        final double[] defender;
        final int[] bridge;

        Base(MapCopy copy, int[] head, double[] defender, int[] bridge) {
            this.copy = copy;
            this.head = head;
            this.defender = defender;
            this.bridge = bridge;
        }

        /**
         * The plain island, its top at the height of the floor: the bed at its front, the defender
         * behind the bed, the bridge to the west with three blocks of void left.
         */
        static Base plain() {
            return new Base(null, new int[] {0, 0, 1}, new double[] {3.5, 1, 0.5, 90}, new int[] {-16, 0, 0, -6, 0, 0});
        }

        /**
         * Where an attacker starts: on the far end of the bridge, facing along it; the next one
         * of the side this many blocks further back.
         */
        double[] attackerStart(int back) {
            int dx = Integer.signum(bridge[3] - bridge[0]);
            int dz = Integer.signum(bridge[5] - bridge[2]);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            return new double[] {bridge[0] + 0.5 - dx * back, bridge[1] + 1, bridge[2] + 0.5 - dz * back, yaw};
        }
    }

    static final int RESPAWN_SECONDS = 3;

    private final Base base;
    private final int attackSeconds;
    private final int rounds;
    /** How long each side took to break the bed, in milliseconds; -1 if it did not. */
    private final long[] breakTime = {-1L, -1L};
    private final Set<UUID> respawning = new HashSet<>();
    private long attackStart;
    private boolean decided;

    AttackDefense(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Base base, int attackSeconds,
            int rounds) {
        super(host, mode, arena, teams, rounds);
        this.base = base;
        this.attackSeconds = attackSeconds;
        this.rounds = rounds;
    }

    @Override
    String title() {
        return "Attack and defense";
    }

    @Override
    void intro(Player player) {
        player.sendMessage(ChatColor.GREEN + "Attack and defense against " + ChatColor.WHITE + namesOf(1 - sideOf(player.getUniqueId()))
                + ChatColor.GREEN + ": " + rounds + " rounds, each side rushes in turn.");
        player.sendMessage(ChatColor.GRAY + "The attacker is already on a bridge next to the island: defender, cover your bed"
                + " and hold " + clock(attackSeconds) + "; attacker, break it. Most beds, then the fastest, wins.");
    }

    /** The side that attacks this round: blue first, then red. */
    int attackers() {
        return round % 2 == 1 ? 1 : 0;
    }

    private boolean attacking(UUID player) {
        return sideOf(player) == attackers();
    }

    @Override
    void build() {
        if (base.copy != null) {
            base.copy.build(arena);
            return;
        }
        // the defender's island, the bed at its front
        Shapes.island(arena, -2, -5, 8, 5, Material.WOOD, 0, Material.LOG, 0);
    }

    @Override
    void roundReset() {
        super.roundReset();
        respawning.clear();
        decided = false;
        if (base.copy == null) {
            Shapes.putBed(arena, 0, 1, 0, base.head[0], base.head[2]);
        }
        // the attacker's bridge, of this round
        int[] b = base.bridge;
        for (int x = Math.min(b[0], b[3]); x <= Math.max(b[0], b[3]); x++) {
            for (int z = Math.min(b[2], b[5]); z <= Math.max(b[2], b[5]); z++) {
                if (arena.block(x, b[1], z).getType() == Material.AIR) {
                    arena.put(x, b[1], z, Material.WOOL, WOOL[attackers()]);
                }
            }
        }
    }

    @Override
    void onFight() {
        attackStart = System.currentTimeMillis();
        for (Player player : online()) {
            if (attacking(player.getUniqueId())) {
                title(player, ChatColor.RED + "Rush!", ChatColor.GRAY + "Bridge the last blocks and break the bed");
            } else {
                title(player, ChatColor.GREEN + "Defend!", ChatColor.GRAY + "Cover your bed, now");
            }
        }
    }

    @Override
    void onSecond() {
        if (decided) {
            return;
        }
        int left = attackSeconds - seconds;
        if (left <= 0) {
            broadcast(ChatColor.GREEN + "The bed held!" + ChatColor.GRAY + " " + TEAMS[1 - attackers()] + " defended it.", Sound.NOTE_BASS);
            endRound();
        } else if (left == 30 || left == 10 || left <= 3) {
            broadcast(ChatColor.YELLOW + "" + left + " s left to break the bed.", Sound.NOTE_STICKS);
        }
    }

    @Override
    Location spawnOf(UUID player) {
        if (attacking(player)) {
            // one behind the other: the bridge is one block wide
            double[] start = base.attackerStart(Math.max(0, indexOf(player)));
            return arena.at(start[0], start[1], start[2], (float) start[3]);
        }
        // side by side, across the way they look
        double[] start = base.defender;
        double across = across(player);
        double yaw = Math.toRadians(start[3]);
        return arena.at(start[0] + across * Math.cos(yaw), start[1], start[2] + across * Math.sin(yaw), (float) start[3]);
    }

    @Override
    double fallY() {
        return arena.floorY() - 12;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.STONE_SWORD), new ItemStack(Material.WOOL, 48), new ItemStack(Material.SHEARS),
            new ItemStack(Material.WOOD_PICKAXE)};
    }

    /**
     * The kit is the attacker's, as after the first minutes of a solo game. The defender has what
     * a player at home has then: a wooden sword and 32 wool.
     */
    @Override
    void afterKit(Player player) {
        if (attacking(player.getUniqueId())) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        boolean wool = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null) {
                continue;
            }
            if (item.getType().name().endsWith("_SWORD")) {
                inventory.setItem(slot, new ItemStack(Material.WOOD_SWORD));
            } else if (item.getType() == Material.WOOL && !wool) {
                wool = true;
                item.setAmount(32);
                inventory.setItem(slot, item);
            } else {
                inventory.setItem(slot, null);
            }
        }
    }

    @Override
    void armour(Player player) {
        leather(player, COLOURS[Math.max(0, sideOf(player.getUniqueId()))]);
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        return !respawning.contains(victim.getUniqueId());
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return arena.contains(block, 40, 12, 16);
    }

    @Override
    boolean canBreak(Player player, Block block) {
        if (block.getType() == Material.BED_BLOCK) {
            if (!attacking(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You can't break your own bed!");
                return false;
            }
            return true;
        }
        return arena.isPlaced(block);
    }

    @Override
    void broke(Player player, Block block) {
        if (block.getType() != Material.BED_BLOCK || phase != Phase.PLAYING || decided) {
            return;
        }
        int side = attackers();
        long took = System.currentTimeMillis() - attackStart;
        breakTime[side] = breakTime[side] < 0 ? took : breakTime[side] + took;
        points[side]++;
        broadcast(ChatColor.BOLD + "BED DESTRUCTION > " + ChatColor.RESET + CHAT[side] + player.getName() + ChatColor.GRAY
                + " broke the bed in " + ChatColor.WHITE + time(took) + " s" + ChatColor.GRAY + "!", Sound.ENDERDRAGON_GROWL);
        endRound();
    }

    /** The round is over: the next one with the sides swapped, or the end. */
    private void endRound() {
        decided = true;
        if (round >= rounds) {
            int winner;
            String why;
            if (points[0] != points[1]) {
                winner = points[0] > points[1] ? 0 : 1;
                why = null;
            } else if (breakTime[0] >= 0 && breakTime[1] >= 0 && breakTime[0] != breakTime[1]) {
                winner = breakTime[0] < breakTime[1] ? 0 : 1;
                why = ChatColor.GRAY + "Faster beds: " + time(breakTime[winner]) + " s against " + time(breakTime[1 - winner]) + " s.";
            } else {
                winner = -1;
                why = null;
            }
            finish(winner, why);
            return;
        }
        startRound();
    }

    @Override
    void fell(Player player) {
        knockedOut(player, true);
    }

    @Override
    void lethal(Player victim, Player killer) {
        knockedOut(victim, false);
    }

    /** The defender comes back, the bed still standing; an attacker out has failed the rush. */
    private void knockedOut(Player player, boolean fell) {
        UUID id = player.getUniqueId();
        if (respawning.contains(id) || phase != Phase.PLAYING || decided) {
            player.teleport(spawnOf(id));
            return;
        }
        UUID by = killerOf(id);
        deathMessage(player, by, fell);
        if (by != null && counts()) {
            plugin.lobby().recordKill(mode.id(), by, id);
        }
        forgetHits(id);
        if (attacking(id)) {
            respawning.add(id);
            spectate(player);
            if (allOut(attackers())) {
                broadcast(ChatColor.GREEN + "The rush failed!" + ChatColor.GRAY + " " + TEAMS[1 - attackers()] + " kept the bed.",
                        Sound.NOTE_BASS);
                endRound();
            }
            return;
        }
        respawning.add(id);
        spectate(player);
        for (int s = 0; s < RESPAWN_SECONDS; s++) {
            final int left = RESPAWN_SECONDS - s;
            later(20L * s, () -> {
                if (respawning.contains(id) && host.gameOf(id) == this) {
                    title(player, ChatColor.RED + "You died!", ChatColor.YELLOW + "Respawning in " + left + " s");
                }
            });
        }
        final int roundNow = round;
        later(20L * RESPAWN_SECONDS, () -> {
            if (respawning.remove(id) && round == roundNow && host.gameOf(id) == this && player.isOnline()) {
                placeAtStart(player);
            }
        });
    }

    /** True when every player of the side is out of this round. */
    private boolean allOut(int side) {
        for (Player player : online(side)) {
            if (!respawning.contains(player.getUniqueId())) {
                return false;
            }
        }
        return true;
    }

    /** A player who left: their side plays on, or the other side wins (done by leave). */
    @Override
    void left(Player player) {
        respawning.remove(player.getUniqueId());
    }

    @Override
    void extraLines(UUID viewer, List<String> lines) {
        boolean attack = attacking(viewer);
        lines.add(ChatColor.WHITE + "Round " + round + "/" + rounds + ": " + (attack ? ChatColor.RED + "you rush" : ChatColor.GREEN + "you defend"));
        lines.add(ChatColor.WHITE + "Bed: " + ChatColor.YELLOW + clock(Math.max(0, attackSeconds - seconds)) + ChatColor.GRAY + " left");
    }

    @Override
    String goal() {
        return rounds + " rounds";
    }

    @Override
    String score(int side) {
        return points[side] + (points[side] == 1 ? " bed" : " beds") + (breakTime[side] >= 0 ? ChatColor.GRAY + " (" + time(breakTime[side]) + " s)" : "");
    }
}
