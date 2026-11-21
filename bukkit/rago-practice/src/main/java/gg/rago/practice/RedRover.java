package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Red Rover Sumo (plan 6.5, solution 1): three friends (a party, or three players of the queue)
 * on the sumo platform, in 1v1, in turn; the one who waits watches. Each 1v1 is one round of the
 * sumo (knockback only, the first off the platform loses it), quick: a second of countdown between
 * two. The rotation and the way to win (the first to a number of 1v1 won, or the last one in) are
 * in {@link RedRoverRotation}; its settings: red-rover in config.yml. On the sumo maps, or on a
 * round platform of the sumo's radius without them.
 */
final class RedRover extends PracticeGame {

    /** The settings of the mode (red-rover in config.yml). */
    static final class Settings {
        final RedRoverRotation.Variant variant;
        final RedRoverRotation.Stays stays;
        /** With points: the 1v1 won to win the game. */
        final int target;
        /** A 1v1 ends after this long (0: never): the one who landed more hits wins it. */
        final int roundSeconds;

        Settings(RedRoverRotation.Variant variant, RedRoverRotation.Stays stays, int target, int roundSeconds) {
            this.variant = variant;
            this.stays = stays;
            this.target = target;
            this.roundSeconds = roundSeconds;
        }

        /** The variant of its word in config.yml (points, elimination), or null. */
        static RedRoverRotation.Variant variantOf(String word) {
            if ("points".equalsIgnoreCase(word)) {
                return RedRoverRotation.Variant.POINTS;
            }
            if ("elimination".equalsIgnoreCase(word)) {
                return RedRoverRotation.Variant.ELIMINATION;
            }
            return null;
        }

        /** Who stays on of its word in config.yml (gagnant, perdant), or null. */
        static RedRoverRotation.Stays staysOf(String word) {
            if ("gagnant".equalsIgnoreCase(word)) {
                return RedRoverRotation.Stays.WINNER;
            }
            if ("perdant".equalsIgnoreCase(word)) {
                return RedRoverRotation.Stays.LOSER;
            }
            return null;
        }

        /** What is wrong with these values, or null. */
        static String check(String variant, String stays, int target, int roundSeconds) {
            if (variantOf(variant) == null) {
                return "variante must be points or elimination";
            }
            if (staysOf(stays) == null) {
                return "reste must be gagnant or perdant";
            }
            if (target < 1 || target > 10) {
                return "points-pour-gagner must be from 1 to 10";
            }
            if (roundSeconds < 0 || roundSeconds > 600) {
                return "secondes-par-1v1 must be from 0 (no limit) to 600";
            }
            return null;
        }
    }

    /** Distance of each fighter from the centre at the start of a 1v1 (as in the sumo). */
    static final double START_DISTANCE = 3.0;

    private final Settings settings;
    private final int radius;
    private final RedRoverRotation<UUID> rotation;
    private final Random random = new Random();
    /** The hits of each fighter in this 1v1, for its end at the time limit. */
    private final Map<UUID, Integer> hits = new HashMap<>();
    private int fightSeconds;

    RedRover(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Settings settings, int radius) {
        super(host, mode, arena, alone(teams));
        this.settings = settings;
        this.radius = radius;
        List<UUID> order = new ArrayList<>();
        for (List<Player> team : teams) {
            for (Player player : team) {
                order.add(player.getUniqueId());
            }
        }
        Collections.shuffle(order, random);
        this.rotation = new RedRoverRotation<>(order, settings.variant, settings.stays, settings.target);
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

    /** What the game is won by, for the players: "First to 3" or "Elimination". */
    static String goal(RedRoverRotation.Variant variant, int target) {
        return variant == RedRoverRotation.Variant.POINTS ? "First to " + target : "Elimination";
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
        List<String> others = new ArrayList<>();
        for (UUID id : rotation.players()) {
            if (!id.equals(player.getUniqueId())) {
                others.add(nameOf(id));
            }
        }
        player.sendMessage(ChatColor.GREEN + "Red Rover Sumo with " + ChatColor.WHITE + String.join(", ", others) + ChatColor.GREEN
                + ": 1v1 in turn, " + (settings.variant == RedRoverRotation.Variant.POINTS
                ? "the first to " + settings.target + " 1v1 won wins." : "a lost 1v1 is out, the last one in wins."));
        player.sendMessage(ChatColor.GRAY + "Knock your opponent off the platform. Who waits watches the 1v1.");
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
        int index = rotation.fight().indexOf(player);
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
        if (!rotation.fights(player.getUniqueId())) {
            spectate(player);
            return;
        }
        super.placeAtStart(player);
    }

    @Override
    void roundReset() {
        hits.clear();
        fightSeconds = 0;
    }

    @Override
    void onFight() {
        List<UUID> fight = rotation.fight();
        if (fight.size() == 2) {
            replayChat(ChatColor.WHITE + nameOf(fight.get(0)) + ChatColor.GRAY + " vs " + ChatColor.WHITE + nameOf(fight.get(1)));
        }
    }

    @Override
    void onSecond() {
        if (settings.roundSeconds <= 0 || rotation.fight().size() != 2 || ++fightSeconds < settings.roundSeconds) {
            return;
        }
        UUID a = rotation.fight().get(0);
        UUID b = rotation.fight().get(1);
        int ha = hits.getOrDefault(a, 0);
        int hb = hits.getOrDefault(b, 0);
        UUID winner = ha == hb ? (random.nextBoolean() ? a : b) : ha > hb ? a : b;
        broadcast(ChatColor.GRAY + "Time! The one who landed more hits wins the 1v1.", null);
        won(winner);
    }

    /** Only the two fighters hit each other, without damage: the knockback only. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker == null || !rotation.fights(victim.getUniqueId()) || !rotation.fights(attacker.getUniqueId())
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return false;
        }
        noDamage(event);
        return true;
    }

    @Override
    void landed(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker != null) {
            hits.merge(attacker.getUniqueId(), 1, Integer::sum);
        }
    }

    @Override
    void fell(Player player) {
        lost(player.getUniqueId(), true);
    }

    @Override
    void lethal(Player victim, Player killer) {
        if (rotation.fights(victim.getUniqueId())) {
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
        if (phase != Phase.PLAYING || !rotation.fights(loser)) {
            return;
        }
        UUID winner = rotation.fight().get(0).equals(loser) ? rotation.fight().get(1) : rotation.fight().get(0);
        UUID by = killerOf(loser);
        if (counts()) {
            plugin.lobby().recordKill(mode.id(), by, loser);
        }
        String victim = ChatColor.RED + nameOf(loser) + ChatColor.GRAY;
        broadcast(by == null ? victim + (fell ? " fell off." : " died.")
                : victim + (fell ? " was knocked off by " : " was killed by ") + ChatColor.GREEN + nameOf(by) + ChatColor.GRAY + ".", null);
        won(winner);
    }

    /** The 1v1 is won: the game over, or the next 1v1 at once. */
    private void won(UUID winner) {
        List<UUID> fight = new ArrayList<>(rotation.fight());
        UUID loser = fight.get(0).equals(winner) ? fight.get(1) : fight.get(0);
        UUID champion = rotation.won(winner);
        for (Player player : online()) {
            sound(player, player.getUniqueId().equals(winner) ? Sound.ORB_PICKUP : Sound.NOTE_BASS, 1f);
        }
        if (settings.variant == RedRoverRotation.Variant.POINTS) {
            broadcast(ChatColor.GREEN + nameOf(winner) + ChatColor.GRAY + " wins the 1v1 (" + rotation.wins(winner) + "/"
                    + settings.target + ").", null);
        } else {
            broadcast(ChatColor.GREEN + nameOf(winner) + ChatColor.GRAY + " wins the 1v1: " + ChatColor.RED + nameOf(loser)
                    + ChatColor.GRAY + " is out.", null);
        }
        if (champion != null) {
            finish(sideOf(champion), null);
            return;
        }
        announceNext();
        startRound();
    }

    /** Who fights next, and who waits. */
    private void announceNext() {
        List<UUID> fight = rotation.fight();
        StringBuilder waiting = new StringBuilder();
        for (UUID id : rotation.waiting()) {
            waiting.append(waiting.length() == 0 ? "" : ", ").append(nameOf(id));
        }
        broadcast(ChatColor.YELLOW + "Next: " + ChatColor.WHITE + nameOf(fight.get(0)) + ChatColor.GRAY + " vs " + ChatColor.WHITE
                + nameOf(fight.get(1)) + (waiting.length() == 0 ? "" : ChatColor.GRAY + " (" + waiting + " waits)"), null);
    }

    /** One who leaves is out of the game (a loss); the last one in wins, else the next 1v1 goes on. */
    @Override
    void leave(Player player, boolean toLobby) {
        UUID id = player.getUniqueId();
        plugin.spectators().playerLeft(this, player);
        if (phase != Phase.ENDED && rotation.isIn(id)) {
            plugin.lobby().forfeited(mode.id(), id);
            host.release(id);
            boolean fought = rotation.fights(id);
            UUID last = rotation.left(id);
            if (last != null) {
                finish(sideOf(last), ChatColor.GRAY + nameOf(id) + " gave up.");
            } else {
                broadcast(ChatColor.GRAY + nameOf(id) + " left the game.", null);
                if (fought) {
                    announceNext();
                    startRound();
                } else {
                    sidebars();
                }
            }
        }
        host.release(id);
        if (toLobby) {
            plugin.lobby().sendToLobby(player);
        }
    }

    /** The 1v1 won of each player: "(Alice 3, Bob 1, Carl 0)". */
    @Override
    String result(int winner) {
        StringBuilder text = new StringBuilder("(");
        for (Map.Entry<UUID, Integer> score : rotation.scores().entrySet()) {
            text.append(text.length() == 1 ? "" : ", ").append(nameOf(score.getKey())).append(' ').append(score.getValue());
        }
        return text.append(')').toString();
    }

    @Override
    List<String> sidebar(UUID viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Red Rover Sumo");
        lines.add(ChatColor.GRAY + goal(settings.variant, settings.target));
        lines.add("");
        List<UUID> fight = rotation.fight();
        if (fight.size() == 2) {
            lines.add(Duel.cut(ChatColor.RED + nameOf(fight.get(0))));
            lines.add(ChatColor.GRAY + "vs");
            lines.add(Duel.cut(ChatColor.BLUE + nameOf(fight.get(1))));
            lines.add(" ");
        }
        for (Map.Entry<UUID, Integer> score : rotation.scores().entrySet()) {
            UUID id = score.getKey();
            ChatColor colour = !rotation.isIn(id) ? ChatColor.DARK_GRAY : id.equals(viewer) ? ChatColor.GREEN : ChatColor.WHITE;
            String state = settings.variant == RedRoverRotation.Variant.POINTS ? String.valueOf(score.getValue())
                    : rotation.isIn(id) ? "in" : "out";
            lines.add(Duel.cut(colour + nameOf(id) + ChatColor.GRAY + ": " + ChatColor.YELLOW + state));
        }
        return lines;
    }
}
