package gg.rago.practice;

import gg.rago.game.GameMode;
import gg.rago.game.RedRoverRotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Red Rover Sumo (plan 6.5, solution 1): three friends (a party, or three players of the queue)
 * on the sumo platform, in 1v1, in turn; the one who waits watches. Each 1v1 is one round of the
 * sumo (knockback only, the first off the platform loses it), quick: a second of countdown between
 * two. The turns and the way to win (the first to a number of 1v1 won, or the last one in) are
 * in {@link RedRoverTurns}; its settings: red-rover in config.yml. On the sumo maps, or on a round
 * platform of the sumo's radius without them. The other Red Rovers are duels played so
 * ({@link Duel#asRedRover}).
 */
final class RedRover extends PracticeGame {

    /** Distance of each fighter from the centre at the start of a 1v1 (as in the sumo). */
    static final double START_DISTANCE = 3.0;

    private final int radius;
    private final RedRoverTurns turns;

    RedRover(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, RedRoverRotation.Settings settings,
            int radius) {
        super(host, mode, arena, alone(teams));
        this.radius = radius;
        this.turns = new RedRoverTurns(this, "Red Rover Sumo", settings, new Random());
    }

    /** Each player a side of their own (the queue brings them as one team): one wins, the others lose. */
    static List<List<Player>> alone(List<List<Player>> teams) {
        List<List<Player>> sides = new ArrayList<>();
        for (List<Player> team : teams) {
            for (Player player : team) {
                sides.add(Collections.singletonList(player));
            }
        }
        return sides;
    }

    @Override
    void build() {
        if (arena.map() != null) {
            return;
        }
        Shapes.disc(arena, 0, 0, 0, radius, Material.QUARTZ_BLOCK, 0);
    }

    @Override
    void intro(Player player) {
        turns.intro(player);
        player.sendMessage(ChatColor.GRAY + "Knock your opponent off the platform.");
    }

    @Override
    double fallY() {
        return arena.floorY() - SumoMatch.FALL_DEPTH;
    }

    @Override
    Location viewPoint() {
        Location view = arena.at(0.5, 8, -(radius + 4) + 0.5, 0f);
        view.setPitch(35f);
        return view;
    }

    @Override
    Location spawnOf(UUID player) {
        int index = turns.slotOf(player);
        if (index < 0) {
            return viewPoint();
        }
        Location start = arena.mapStart(index);
        if (start != null) {
            return start;
        }
        return index == 0 ? arena.at(0.5 - START_DISTANCE, 1, 0.5, -90f) : arena.at(0.5 + START_DISTANCE, 1, 0.5, 90f);
    }

    /** The two fighters at their start; the others watch. */
    @Override
    void placeAtStart(Player player) {
        if (!turns.fights(player.getUniqueId())) {
            spectate(player);
            return;
        }
        super.placeAtStart(player);
    }

    @Override
    void roundReset() {
        turns.roundStarted();
    }

    @Override
    void onFight() {
        turns.onFight();
    }

    @Override
    void onSecond() {
        turns.second(seconds);
    }

    /** Only the two fighters hit each other, without damage: the knockback only. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker == null || !turns.fights(victim.getUniqueId()) || !turns.fights(attacker.getUniqueId())
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return false;
        }
        noDamage(event);
        return true;
    }

    @Override
    void fell(Player player) {
        lost(player.getUniqueId(), true);
    }

    @Override
    void lethal(Player victim, Player killer) {
        if (turns.fights(victim.getUniqueId())) {
            lost(victim.getUniqueId(), false);
        } else {
            heal(victim);
        }
    }

    @Override
    void moved(Player player, Location from, Location to) {
        if (to.getBlock().isLiquid()) {
            lost(player.getUniqueId(), true);
        }
    }

    /** This fighter lost the 1v1 (fell off, or died): the kill to whoever knocked them, the 1v1 to the other. */
    private void lost(UUID loser, boolean fell) {
        if (phase != Phase.PLAYING || !turns.fights(loser)) {
            return;
        }
        UUID winner = turns.opponentOf(loser);
        UUID by = killerOf(loser);
        if (counts()) {
            plugin.lobby().recordKill(mode.id(), by, loser);
        }
        String victim = ChatColor.RED + nameOf(loser) + ChatColor.GRAY;
        broadcast(by == null ? victim + (fell ? " fell off." : " died.")
                : victim + (fell ? " was knocked off by " : " was killed by ") + ChatColor.GREEN + nameOf(by) + ChatColor.GRAY + ".", null);
        turns.won(winner);
    }

    /** One who leaves is out of the game (a loss); the last one in wins, else the next 1v1 goes on. */
    @Override
    void leave(Player player, boolean toLobby) {
        turns.leave(player, toLobby);
    }

    @Override
    String result(int winner) {
        return turns.result();
    }

    @Override
    List<String> sidebar(UUID viewer) {
        return turns.sidebar(viewer, null);
    }
}
