package gg.rago.bedwars;

import gg.rago.game.RedRoverRotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Red Rover Top Bridge (plan 6.5, solution 1), as the Red Rovers of RagoPractice: three players or
 * more (one team of the lobby's queue) on the top bridge, in 1v1, in turn; who waits watches above
 * the bridge. The two who fight are the two teams of a Top Bridge 1v1, on the map's two islands,
 * and play its rounds of one life: the first to red-rover.points-par-1v1-top-bridge rounds won
 * (RagoPractice's config.yml) wins the 1v1. Who fights next and how the game is won (the first to
 * a number of 1v1 won, or the last one in): {@link RedRoverRotation}, with the settings of every
 * Red Rover (red-rover, read in RagoPractice's config.yml). Between two rounds the map is made as
 * it was. One wins the game, the others lose it. Main thread only.
 */
final class RedRoverTopBridgeGame extends TopBridgeGame {

    /** The rounds that win a 1v1 when red-rover.points-par-1v1-top-bridge is missing: one, as a round of the sumo. */
    static final int ROUNDS_PER_1V1 = 1;
    /** At most the rounds of a game of Top Bridge. */
    static final int MAX_ROUNDS_PER_1V1 = TopBridgeGame.POINTS_TO_WIN;

    private final RedRoverRotation.Settings rover;
    /** The rounds won to win a 1v1. */
    private final int roundsPer1v1;
    private final RedRoverRotation<UUID> rotation;
    private final Random random;
    /** The hits each fighter landed in this 1v1, for its time limit. */
    private final Map<UUID, Integer> hits = new HashMap<>();
    /** The seconds played of this 1v1. */
    private int fightSeconds;
    /** Once the game has its winner: nothing changes any more until the end. */
    private boolean over;

    RedRoverTopBridgeGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides, RedRoverRotation.Settings rover, int roundsPer1v1, Random random) {
        super(plugin, host, instance, modeId, modeName, sides, false);
        this.rover = rover;
        this.roundsPer1v1 = roundsPer1v1;
        this.random = random;
        // the two teams of the 1v1, on the islands of a Top Bridge 1v1
        List<TeamColor> two = teamColors(2);
        teams.clear();
        for (TeamColor color : two) {
            teams.add(new Team(color, instance.map().teams().get(color)));
        }
        List<UUID> order = new ArrayList<>(gamers.keySet());
        Collections.shuffle(order, random);
        rotation = new RedRoverRotation<>(order, rover.variant, rover.stays, rover.target);
        assignFighters();
    }

    /** The settings of every Red Rover in this red-rover (of RagoPractice's config.yml), or null if one is wrong. */
    static RedRoverRotation.Settings settingsOf(ConfigurationSection section) {
        return RedRoverRotation.Settings.of(section.getString("variante", "points"), section.getString("reste", "gagnant"),
                section.getInt("points-pour-gagner", 3), section.getInt("secondes-par-1v1", 90));
    }

    /** What is wrong with the settings of this red-rover, or null. */
    static String problemOf(ConfigurationSection section) {
        return RedRoverRotation.Settings.check(section.getString("variante", "points"), section.getString("reste", "gagnant"),
                section.getInt("points-pour-gagner", 3), section.getInt("secondes-par-1v1", 90));
    }

    /** The rounds that win a 1v1 in this red-rover (points-par-1v1-top-bridge), or -1 if wrong. */
    static int roundsOf(ConfigurationSection section) {
        int rounds = section.getInt("points-par-1v1-top-bridge", ROUNDS_PER_1V1);
        return rounds < 1 || rounds > MAX_ROUNDS_PER_1V1 ? -1 : rounds;
    }

    /** The two who fight this 1v1, each in a team; the others out of it, watching. A new 1v1 from 0 - 0. */
    private void assignFighters() {
        List<UUID> fight = rotation.fight();
        for (Team team : teams) {
            team.members.clear();
        }
        resetPoints();
        hits.clear();
        fightSeconds = 0;
        for (Gamer gamer : gamers.values()) {
            int slot = fight.indexOf(gamer.id);
            // one who waits keeps a team, for the colour of their name: the first one's
            gamer.team = teams.get(Math.max(0, slot));
            if (slot >= 0) {
                gamer.team.members.add(gamer.id);
            }
            gamer.eliminated = slot < 0;
        }
    }

    @Override
    int pointsToWin() {
        return roundsPer1v1;
    }

    /** Everyone in the game hears everyone: no teams to talk in. */
    @Override
    boolean chatTeams() {
        return false;
    }

    /** One who waits watches above the bridge; the two who fight on their half, in their team's colour. */
    @Override
    void place(Player player, Gamer gamer) {
        if (!rotation.fights(gamer.id)) {
            gamer.eliminated = true;
            prepare(player);
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(watchPoint());
            return;
        }
        super.place(player, gamer);
        plugin.lobby().setGameNameTag(player, gamer.team.color.letter() + " " + gamer.team.color.chat());
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        List<String> others = new ArrayList<>();
        for (UUID id : rotation.players()) {
            if (!id.equals(gamer.id)) {
                others.add(nameOf(id));
            }
        }
        player.sendMessage(ChatColor.GREEN + modeName + " with " + ChatColor.WHITE + String.join(", ", others) + ChatColor.GREEN
                + ": 1v1 in turn on the top bridge of " + instance.map().world() + ", "
                + (rover.variant == RedRoverRotation.Variant.POINTS ? "the first to " + rover.target + " 1v1 won wins."
                : "a lost 1v1 is out, the last one in wins."));
        player.sendMessage(ChatColor.GRAY + "A 1v1: rounds of one life, the first to " + roundsPer1v1 + " round"
                + (roundsPer1v1 == 1 ? "" : "s") + " won wins it. Who waits watches the 1v1. Your kit is the mode's kit of the"
                + " lobby's kit editor. /spawn takes you back to the lobby.");
    }

    /** The first 1v1 is on: who fights whom, in the replay's chat (the next ones: "Next: A vs B"). */
    @Override
    void started() {
        super.started();
        List<UUID> fight = rotation.fight();
        if (fight.size() == 2) {
            replayChat(ChatColor.WHITE + nameOf(fight.get(0)) + ChatColor.GRAY + " vs " + ChatColor.WHITE + nameOf(fight.get(1)));
        }
    }

    @Override
    void outOfGame(Player victim, Gamer gamer) {
        int other = teams.indexOf(gamer.team) == 0 ? 1 : 0;
        boolean lost = points[other] + 1 >= roundsPer1v1;
        victim.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "YOU DIED!", ChatColor.GRAY
                + (lost ? "You lost the 1v1." : "You play the next round."));
    }

    /** A hit of one fighter on the other counts for the time limit of the 1v1. */
    @Override
    void hit(Gamer victim, Gamer attacker) {
        super.hit(victim, attacker);
        if (phase == Phase.PLAYING && rotation.fights(victim.id) && rotation.fights(attacker.id)) {
            Integer before = hits.get(attacker.id);
            hits.put(attacker.id, before == null ? 1 : before + 1);
        }
    }

    /**
     * A second of the 1v1 played: at its time limit (red-rover.secondes-par-1v1), the one who
     * landed more hits in it wins it, either of the two if they landed as many.
     */
    @Override
    void everySecond() {
        if (over || phase != Phase.PLAYING) {
            return;
        }
        fightSeconds++;
        List<UUID> fight = rotation.fight();
        if (rover.roundSeconds <= 0 || fightSeconds < rover.roundSeconds || fight.size() != 2) {
            return;
        }
        UUID a = fight.get(0);
        UUID b = fight.get(1);
        int ha = hitsOf(a);
        int hb = hitsOf(b);
        UUID winner = ha == hb ? (random.nextBoolean() ? a : b) : ha > hb ? a : b;
        broadcast(ChatColor.GRAY + "Time! The one who landed more hits wins the 1v1.", null);
        phase = Phase.WAITING;
        won(winner);
    }

    private int hitsOf(UUID fighter) {
        Integer count = hits.get(fighter);
        return count == null ? 0 : count;
    }

    /** The fighter of this team reached the rounds of a 1v1: they win it. */
    @Override
    void reachedTarget(Team winner) {
        won(winner.members.isEmpty() ? null : winner.members.get(0));
    }

    /** This fighter won the 1v1: the game over, or the next 1v1 at once. */
    private void won(UUID winner) {
        if (winner == null || over || phase == Phase.ENDED || !rotation.fights(winner)) {
            return;
        }
        List<UUID> fight = rotation.fight();
        UUID loser = fight.get(0).equals(winner) ? fight.get(1) : fight.get(0);
        UUID champion = rotation.won(winner);
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && host.gameOf(gamer.id) == this) {
                player.playSound(player.getLocation(), gamer.id.equals(winner) ? Sound.ORB_PICKUP : Sound.NOTE_BASS, 1f, 1f);
            }
        }
        if (rover.variant == RedRoverRotation.Variant.POINTS) {
            broadcast(ChatColor.GREEN + nameOf(winner) + ChatColor.GRAY + " wins the 1v1 (" + rotation.wins(winner) + "/"
                    + rover.target + ").", null);
        } else {
            broadcast(ChatColor.GREEN + nameOf(winner) + ChatColor.GRAY + " wins the 1v1: " + ChatColor.RED + nameOf(loser)
                    + ChatColor.GRAY + " is out.", null);
        }
        if (champion != null) {
            over = true;
            later(BETWEEN_TICKS, () -> finish(champion));
            sidebars();
            return;
        }
        nextFight();
    }

    /** The next 1v1, at once: who fights whom and who waits, then everyone in place. */
    private void nextFight() {
        assignFighters();
        List<UUID> fight = rotation.fight();
        if (fight.size() == 2) {
            List<String> waiting = new ArrayList<>();
            for (UUID id : rotation.waiting()) {
                waiting.add(nameOf(id));
            }
            broadcast(ChatColor.YELLOW + "Next: " + ChatColor.WHITE + nameOf(fight.get(0)) + ChatColor.GRAY + " vs "
                    + ChatColor.WHITE + nameOf(fight.get(1)) + (waiting.isEmpty() ? ""
                    : ChatColor.GRAY + " (" + String.join(", ", waiting) + " waits)"), null);
        }
        if (phase != Phase.WAITING) {
            // the 1v1 was being played (a fighter left): its round ends here, the next one starts
            phase = Phase.WAITING;
        }
        later(NEXT_ROUND_TICKS, () -> nextRound(null));
        sidebars();
    }

    /** The first round of a 1v1: who fights whom; the next rounds of the same 1v1: as in Top Bridge. */
    @Override
    void roundTitle(Player player, Gamer gamer, Team lastWinner, int countdown) {
        if (lastWinner != null) {
            super.roundTitle(player, gamer, lastWinner, countdown);
            return;
        }
        List<UUID> fight = rotation.fight();
        String versus = fight.size() == 2 ? ChatColor.WHITE + nameOf(fight.get(0)) + ChatColor.GRAY + " vs " + ChatColor.WHITE
                + nameOf(fight.get(1)) : "";
        player.sendTitle(rotation.fights(gamer.id) ? ChatColor.GREEN + "" + ChatColor.BOLD + "FIGHT!" : "", versus);
    }

    /**
     * The game's leave: one who leaves is out of it (a loss). The last one in wins; when a fighter
     * left, the next 1v1 starts at once, the other fighter staying on.
     */
    @Override
    void leave(Player player, boolean toLobby) {
        UUID id = player.getUniqueId();
        Gamer gamer = gamers.get(id);
        if (gamer == null || host.gameOf(id) != this || phase == Phase.ENDED || over || !rotation.isIn(id)) {
            if (gamer != null && host.gameOf(id) == this) {
                plugin.watchers().playerLeft(this, player);
                host.release(id);
            }
            if (toLobby) {
                plugin.lobby().sendToLobby(player);
            }
            return;
        }
        plugin.watchers().playerLeft(this, player);
        host.release(id);
        plugin.lobby().forfeited(modeId, id);
        gamer.eliminated = true;
        gamer.respawning = false;
        cancel(gamer.respawn);
        gamer.respawn = null;
        boolean fought = rotation.fights(id);
        UUID last = rotation.left(id);
        if (last != null) {
            over = true;
            broadcast(ChatColor.GRAY + nameOf(id) + " gave up.", null);
            finish(last);
        } else {
            broadcast(ChatColor.GRAY + nameOf(id) + " left the game.", null);
            if (fought) {
                nextFight();
            } else {
                sidebars();
            }
        }
        if (toLobby) {
            plugin.lobby().sendToLobby(player);
        }
    }

    /**
     * The game won by this player: the 1v1 won of each, then the end, the winner's team holding
     * them alone and the other everyone else (all lose, those who left too).
     */
    private void finish(UUID champion) {
        if (phase == Phase.ENDED) {
            return;
        }
        over = true;
        broadcast(ChatColor.YELLOW + "1v1 won: " + ChatColor.WHITE + result(), null);
        // the top 3 of the game (the owner's ask of 22/11): the winner first, then by 1v1 won
        List<UUID> top = rotation.top(champion, TOP);
        broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + "Top " + top.size(), null);
        for (int i = 0; i < top.size(); i++) {
            broadcast(topLine(i, nameOf(top.get(i)), rotation.wins(top.get(i))), null);
        }
        Gamer winner = gamers.get(champion);
        Team won = winner.team;
        Team lost = won == teams.get(0) ? teams.get(1) : teams.get(0);
        won.members.clear();
        lost.members.clear();
        for (Gamer gamer : gamers.values()) {
            gamer.team = gamer == winner ? won : lost;
            gamer.team.members.add(gamer.id);
        }
        end(won);
    }

    /** How many the top of the end shows (the owner's ask of 22/11: the top 3). */
    static final int TOP = 3;

    /** "  1st - Alice - 3 1v1 won": a place of the top, from 0, coloured as the killers' of the end. */
    static String topLine(int place, String name, int won) {
        return "  " + PLACES[place] + ChatColor.GRAY + " - " + ChatColor.WHITE + name + ChatColor.GRAY + " - " + ChatColor.WHITE + won
                + ChatColor.GRAY + " 1v1 won";
    }

    private static final String[] PLACES = {
        ChatColor.YELLOW + "" + ChatColor.BOLD + "1st",
        ChatColor.GOLD + "" + ChatColor.BOLD + "2nd",
        ChatColor.RED + "" + ChatColor.BOLD + "3rd",
    };

    /** The 1v1 won of each player: "Alice 3, Bob 1, Carl 0". */
    private String result() {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<UUID, Integer> score : rotation.scores().entrySet()) {
            parts.add(nameOf(score.getKey()) + " " + score.getValue());
        }
        return String.join(", ", parts);
    }

    private String nameOf(UUID id) {
        Gamer gamer = gamers.get(id);
        return gamer == null ? "?" : gamer.name;
    }

    /**
     * The sidebar: how the game is won, the two who fight (with their rounds in this 1v1 when a
     * 1v1 has more than one), then each player's 1v1 won (or in, out).
     */
    @Override
    List<String> lines(Gamer viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GRAY + rover.goal());
        lines.add("");
        List<UUID> fight = rotation.fight();
        if (fight.size() == 2 && teams.size() == 2) {
            String a = teams.get(0).color.chat() + nameOf(fight.get(0));
            String b = teams.get(1).color.chat() + nameOf(fight.get(1));
            lines.add(cut(roundsPer1v1 > 1 ? a + " " + ChatColor.WHITE + points[0] + ChatColor.GRAY + " - " + ChatColor.WHITE
                    + points[1] + " " + b : a + ChatColor.GRAY + " vs " + b));
            lines.add(" ");
        }
        for (Map.Entry<UUID, Integer> score : rotation.scores().entrySet()) {
            UUID id = score.getKey();
            ChatColor colour = !rotation.isIn(id) ? ChatColor.DARK_GRAY : id.equals(viewer.id) ? ChatColor.GREEN : ChatColor.WHITE;
            String state = rover.variant == RedRoverRotation.Variant.POINTS ? String.valueOf(score.getValue())
                    : rotation.isIn(id) ? "in" : "out";
            lines.add(cut(colour + nameOf(id) + ChatColor.GRAY + ": " + ChatColor.YELLOW + state));
        }
        return lines;
    }

    /** A sidebar line is 40 characters at most. */
    private static String cut(String line) {
        return line.length() <= 40 ? line : line.substring(0, 40);
    }
}
