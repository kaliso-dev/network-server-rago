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

/**
 * Attack and defense, as in Ranked Bedwars: one side defends a bed, the other attacks it. The
 * defenders first have 30 seconds to build their defense while the attackers wait on their island;
 * then the attackers have 2 minutes to break the bed. A death respawns in 3 seconds. The sides
 * swap each round: after the two rounds, the side that broke more beds wins, and with one bed each,
 * the one that broke it faster.
 */
final class AttackDefense extends Duel {

    enum Part { BUILD, ATTACK }

    /** Where the bed is: its foot, its head one block south. */
    private static final int BED_X = 10;
    private static final int RESPAWN_SECONDS = 3;

    private final int buildSeconds;
    private final int attackSeconds;
    private final int rounds;
    /** How long each side took to break the bed, in milliseconds; -1 if it did not. */
    private final long[] breakTime = {-1L, -1L};
    private final Set<UUID> respawning = new HashSet<>();
    private Part part = Part.BUILD;
    private int partSeconds;
    private long attackStart;

    AttackDefense(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int buildSeconds,
            int attackSeconds, int rounds) {
        super(host, mode, arena, teams, rounds);
        this.buildSeconds = buildSeconds;
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
                + ChatColor.GREEN + ": " + rounds + " rounds, each side attacks in turn.");
        player.sendMessage(ChatColor.GRAY + "Defenders: " + buildSeconds + " s to build, then hold " + clock(attackSeconds)
                + ". Attackers: break the bed. Most beds, then the fastest, wins.");
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
        // the defenders' island and its bed, the attackers' island, 14 blocks of void between
        Shapes.island(arena, 5, -5, 15, 5, Material.WOOD, 0, Material.LOG, 0);
        Shapes.island(arena, -14, -2, -10, 2, Material.STAINED_CLAY, Shapes.WHITE, Material.HARD_CLAY, 0);
    }

    @Override
    void roundReset() {
        super.roundReset();
        respawning.clear();
        part = Part.BUILD;
        partSeconds = 0;
        Shapes.putBed(arena, BED_X, 1, 0, 0, 1);
    }

    @Override
    void onFight() {
        for (Player player : online()) {
            if (attacking(player.getUniqueId())) {
                title(player, ChatColor.RED + "You attack", ChatColor.GRAY + "The defense is being built: wait " + buildSeconds + " s");
            } else {
                title(player, ChatColor.GREEN + "You defend", ChatColor.GRAY + "Build your defense: " + buildSeconds + " s");
            }
        }
    }

    @Override
    void onSecond() {
        partSeconds++;
        if (part == Part.BUILD) {
            int left = buildSeconds - partSeconds;
            if (left <= 0) {
                part = Part.ATTACK;
                partSeconds = 0;
                attackStart = System.currentTimeMillis();
                broadcast(ChatColor.RED + "" + ChatColor.BOLD + "Attack!" + ChatColor.GRAY + " The attackers have "
                        + clock(attackSeconds) + " to break the bed.", Sound.ENDERDRAGON_GROWL);
                for (Player player : online()) {
                    title(player, attacking(player.getUniqueId()) ? ChatColor.RED + "Break the bed!" : ChatColor.GREEN + "Defend!", "");
                }
            } else if (left <= 3) {
                broadcast(ChatColor.YELLOW + "The attack starts in " + left + "...", Sound.NOTE_STICKS);
            }
            return;
        }
        int left = attackSeconds - partSeconds;
        if (left <= 0) {
            broadcast(ChatColor.GREEN + "The bed held!" + ChatColor.GRAY + " " + TEAMS[1 - attackers()] + " defended it.", Sound.NOTE_BASS);
            endRound();
        } else if (left == 30 || left == 10 || left <= 3) {
            broadcast(ChatColor.YELLOW + "" + left + " s left to break the bed.", Sound.NOTE_STICKS);
        }
    }

    /** The attackers wait on their island while the defense is built. */
    @Override
    void moved(Player player, Location from, Location to) {
        if (phase == Phase.PLAYING && part == Part.BUILD && attacking(player.getUniqueId()) && !onAttackIsland(to)) {
            Location back = onAttackIsland(from) ? from.clone() : spawnOf(player.getUniqueId());
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            player.teleport(back);
        }
    }

    /** True if this point is on the attackers' island (x -14 to -10, z -2 to 2), not at its very edge. */
    private boolean onAttackIsland(Location at) {
        double x = at.getX() - arena.centreX();
        double z = at.getZ() - arena.centreZ();
        return x >= -13.75 && x <= -9.25 && z >= -1.75 && z <= 2.75;
    }

    @Override
    Location spawnOf(UUID player) {
        double z = across(player) * 1.5 + 0.5;
        return attacking(player) ? arena.at(-11.5, 1, z, -90f) : arena.at(13.5, 1, z, 90f);
    }

    @Override
    double fallY() {
        return arena.floorY() - 10;
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        return new ItemStack[] {new ItemStack(Material.IRON_SWORD), new ItemStack(Material.WOOL, 64),
            new ItemStack(Material.ENDER_STONE, 32), new ItemStack(Material.SHEARS), new ItemStack(Material.WOOD_PICKAXE)};
    }

    @Override
    void armour(Player player) {
        ironArmour(player, COLOURS[Math.max(0, sideOf(player.getUniqueId()))]);
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        return part == Part.ATTACK && !respawning.contains(victim.getUniqueId());
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        if (part == Part.BUILD) {
            // the defenders build around their bed only
            return !attacking(player.getUniqueId()) && Math.abs(block.getX() - arena.centreX() - BED_X) <= 6
                    && Math.abs(block.getZ() - arena.centreZ()) <= 6 && block.getY() <= arena.floorY() + 8;
        }
        return arena.contains(block, 30, 12, 16);
    }

    @Override
    boolean canBreak(Player player, Block block) {
        if (block.getType() == Material.BED_BLOCK) {
            if (!attacking(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You can't break your own bed!");
                return false;
            }
            return part == Part.ATTACK;
        }
        return part == Part.ATTACK || !attacking(player.getUniqueId()) ? arena.isPlaced(block) : false;
    }

    @Override
    void broke(Player player, Block block) {
        if (block.getType() != Material.BED_BLOCK || phase != Phase.PLAYING) {
            return;
        }
        int side = attackers();
        long took = System.currentTimeMillis() - attackStart;
        breakTime[side] = took;
        points[side]++;
        broadcast(ChatColor.BOLD + "BED DESTRUCTION > " + ChatColor.RESET + CHAT[side] + player.getName() + ChatColor.GRAY
                + " broke the bed in " + ChatColor.WHITE + time(took) + " s" + ChatColor.GRAY + "!", Sound.ENDERDRAGON_GROWL);
        endRound();
    }

    /** The round is over: the next one with the sides swapped, or the end. */
    private void endRound() {
        if (round >= rounds) {
            int winner;
            String why;
            if (points[0] != points[1]) {
                winner = points[0] > points[1] ? 0 : 1;
                why = null;
            } else if (breakTime[0] >= 0 && breakTime[1] >= 0 && breakTime[0] != breakTime[1]) {
                winner = breakTime[0] < breakTime[1] ? 0 : 1;
                why = ChatColor.GRAY + "Faster bed: " + time(breakTime[winner]) + " s against " + time(breakTime[1 - winner]) + " s.";
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

    private void knockedOut(Player player, boolean fell) {
        UUID id = player.getUniqueId();
        if (respawning.contains(id) || phase != Phase.PLAYING) {
            player.teleport(spawnOf(id));
            return;
        }
        UUID by = killerOf(id);
        deathMessage(player, by, fell);
        if (by != null && counts()) {
            plugin.lobby().recordKill(mode.id(), by, id);
        }
        forgetHits(id);
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

    /** A player who left: their side plays on, or the other side wins (done by leave). */
    @Override
    void left(Player player) {
        respawning.remove(player.getUniqueId());
    }

    @Override
    void extraLines(UUID viewer, List<String> lines) {
        boolean attack = attacking(viewer);
        lines.add(ChatColor.WHITE + "Round " + round + "/" + rounds + ": " + (attack ? ChatColor.RED + "you attack" : ChatColor.GREEN + "you defend"));
        if (part == Part.BUILD) {
            lines.add(ChatColor.WHITE + "Building: " + ChatColor.YELLOW + clock(buildSeconds - partSeconds));
        } else {
            lines.add(ChatColor.WHITE + "Bed: " + ChatColor.YELLOW + clock(attackSeconds - partSeconds) + ChatColor.GRAY + " left");
        }
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
