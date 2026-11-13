package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

/**
 * Top Bridge, as the owner plays it: no bed, no forge, no shop, only the fight on the platform of
 * the top bridge (the one he built on Antenna, on any map). The players start on top, each team on
 * its half, with the mode's kit (the lobby's kit editor: a stone sword and a stack of golden apples),
 * diamond leggings and boots with a leather helmet and chestplate in the team's colour, Protection
 * IV; one who falls off the platform is dead, as in the void.
 *
 * <ul>
 *   <li>A game of the queue (1v1, 2v2), or of a party that fills it from the Duels: rounds, one
 *       life each. A round is won when every player of the other team is dead; the first team to
 *       {@value #POINTS_TO_WIN} points wins. The next round starts at once, with no wait nor
 *       countdown: the map made as it was (the blocks placed go) and everyone on top again.
 *   <li>The private top bridge of a party (the Private games): a practice. Nobody is ever out: a
 *       dead player comes back on top at once, the blocks placed go after
 *       {@value #DECAY_SECONDS} seconds, and it lasts {@value #PRACTICE_MINUTES} minutes at most.
 * </ul>
 *
 * {@link TopBedFightGame} plays these rounds on the owner's own bridge. Main thread only.
 */
class TopBridgeGame extends BedwarsGame {

    static final int POINTS_TO_WIN = 5;
    static final int PRACTICE_MINUTES = 30;
    /** The practice's wait before a new life: none. */
    static final int PRACTICE_RESPAWN = 0;
    static final int DECAY_SECONDS = 45;
    /** A game of rounds lasts this long at most: then the team ahead wins, or it is a draw. */
    static final int ROUNDS_MINUTES = 30;
    /** The countdown at the start of the game, before the first round. */
    private static final int COUNTDOWN = 3;
    /** Before the end of the game, once its last round is won. */
    private static final long BETWEEN_TICKS = 20L;
    /** Between the end of a round and the next one: a tick, to be out of the event of the death. */
    static final long NEXT_ROUND_TICKS = 1L;
    /** The kit when the lobby has none for the mode: the owner's fight kit. */
    private static final ItemStack[] DEFAULT_KIT = {new ItemStack(Material.STONE_SWORD), new ItemStack(Material.GOLDEN_APPLE, 64)};

    /** The private top bridge of a party (the Private games): the practice without rounds. */
    final boolean practice;
    private final BlockDecay decay = new BlockDecay(DECAY_SECONDS);
    private final int[] points;
    private int round = 1;
    private boolean roundOver;
    /** The countdown before the current round, while it runs. */
    private BukkitTask roundCountdown;

    TopBridgeGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides, boolean practice) {
        super(plugin, host, instance, modeId, modeName, sides);
        this.practice = practice;
        this.points = new int[teams.size()];
    }

    // --- What Top Bridge changes ----------------------------------------------------------------

    /** The points a team needs to win a game of rounds. */
    int pointsToWin() {
        return POINTS_TO_WIN;
    }

    /** What the fight is on, made again for each round in the copy of the map: the top bridge. */
    void buildFight() {
        buildTopBridge();
    }

    /** The rule of the practice, after "you are in the X team." */
    String practiceRule() {
        return "A practice of your party: fight on the top bridge, fall off and you come back on it. No bed, no forge, "
                + PRACTICE_MINUTES + " minutes at most.";
    }

    /** The rule of a game of rounds, after "you are in the X team." */
    String roundsRule() {
        return "Rounds of one life: kill every player of the other team, or knock them off the bridge. The first team to "
                + pointsToWin() + " points wins.";
    }

    /** The private top bridge of a party, a practice, is not kept for /replay; a game of rounds is. */
    @Override
    boolean recorded() {
        return !practice;
    }

    @Override
    boolean hasTopBridge() {
        return true;
    }

    @Override
    boolean onTheBridge() {
        return topBridge != null;
    }

    @Override
    boolean economy() {
        return false;
    }

    @Override
    boolean upgrades() {
        return false;
    }

    /** No bed at all: the fight is on the bridge. */
    @Override
    void removeUnusedBeds() {
        World world = instance.world();
        for (BedwarsMap.Team team : instance.map().teams().values()) {
            if (team.bed != null) {
                for (Block half : Instance.bedOf(team.bed.blockIn(world))) {
                    half.setType(Material.AIR, false);
                }
            }
        }
        for (Team team : teams) {
            team.bed = false;
        }
    }

    @Override
    Team bedTeam(Block block) {
        return null;
    }

    @Override
    int countdownSeconds() {
        return COUNTDOWN;
    }

    @Override
    int respawnSeconds() {
        return PRACTICE_RESPAWN;
    }

    /** In rounds, a death is the last of the round; in the practice, nobody is ever out. */
    @Override
    boolean finalDeath(Gamer gamer) {
        return !practice;
    }

    @Override
    boolean finalKillsCounted() {
        return false;
    }

    /** No timed event: no generator, no bed; the game's own end (see {@link #everySecond}). */
    @Override
    void events() {
    }

    /** On the team's half of the platform, side by side; on the island when the map had no room for a bridge. */
    @Override
    Location spawnOf(Gamer gamer) {
        if (topBridge == null) {
            return super.spawnOf(gamer);
        }
        int team = teams.indexOf(gamer.team);
        int index = Math.max(0, gamer.team.members.indexOf(gamer.id));
        return at(topBridge.start(team != 1, index, Math.max(1, gamer.team.members.size())));
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        String head = ChatColor.YELLOW + "" + ChatColor.BOLD + modeName + ChatColor.YELLOW + " on " + ChatColor.WHITE
                + instance.map().world() + ChatColor.YELLOW + ": you are in the " + gamer.team.color.coloured()
                + ChatColor.YELLOW + " team. ";
        player.sendMessage(head + (practice ? practiceRule() : roundsRule()));
        player.sendMessage(ChatColor.GRAY + "Your kit is the mode's kit of the lobby's kit editor. /spawn takes you back to "
                + "the lobby.");
    }

    /**
     * The mode's kit as the player arranged it, and the fight armour, its leather in the team's
     * colour, at each life.
     */
    @Override
    void giveKit(Player player, Gamer gamer) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        ItemStack[] kit = lobbyKit(player, gamer);
        if (kit != null) {
            inventory.setContents(kit);
        } else {
            for (ItemStack item : DEFAULT_KIT) {
                inventory.addItem(kitItem(item.clone(), gamer));
            }
        }
        fightArmour(player, gamer.team.color.leather());
        player.updateInventory();
    }

    @Override
    void outOfGame(Player victim, Gamer gamer) {
        victim.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "YOU DIED!", ChatColor.GRAY + "You play the next round.");
    }

    @Override
    void placed(Player player, Block block) {
        if (practice) {
            decay.placed(block, seconds);
        }
    }

    @Override
    void everySecond() {
        if (practice) {
            decay.tick(instance, instance.world(), seconds);
        }
        int limit = (practice ? PRACTICE_MINUTES : ROUNDS_MINUTES) * 60;
        if (seconds == limit - 60) {
            broadcast(ChatColor.YELLOW + "One minute left.", Sound.NOTE_STICKS);
        } else if (seconds >= limit) {
            broadcast(ChatColor.YELLOW + "Time is up.", Sound.NOTE_BASS);
            end(ahead());
        }
    }

    /** The team ahead (in points, or in kills in the practice), or null when they are level. */
    private Team ahead() {
        int[] score = practice ? kills() : points;
        Team best = null;
        boolean level = false;
        for (int i = 0; i < teams.size(); i++) {
            if (best == null || score[i] > score[teams.indexOf(best)]) {
                best = teams.get(i);
                level = false;
            } else if (score[i] == score[teams.indexOf(best)]) {
                level = true;
            }
        }
        return level ? null : best;
    }

    /** The kills of each team. */
    private int[] kills() {
        int[] kills = new int[teams.size()];
        for (Gamer gamer : gamers.values()) {
            int team = teams.indexOf(gamer.team);
            if (team >= 0) {
                kills[team] += gamer.kills;
            }
        }
        return kills;
    }

    // --- Rounds ---------------------------------------------------------------------------------

    /**
     * The game is over when a team has nobody left in it; in rounds, the round is over when a team
     * has nobody alive.
     */
    @Override
    void checkEnd() {
        if (phase == Phase.ENDED) {
            return;
        }
        List<Team> here = new ArrayList<>();
        List<Team> alive = new ArrayList<>();
        for (Team team : teams) {
            boolean present = false;
            boolean living = false;
            for (UUID member : team.members) {
                Gamer gamer = gamers.get(member);
                if (gamer != null && present(gamer)) {
                    present = true;
                    living |= !gamer.eliminated;
                }
            }
            if (present) {
                here.add(team);
            }
            if (living) {
                alive.add(team);
            }
        }
        if (here.size() <= 1) {
            // the others left: the team still there wins
            end(here.isEmpty() ? null : here.get(0));
            return;
        }
        if (!practice && !roundOver && phase == Phase.PLAYING && alive.size() <= 1) {
            roundWon(alive.isEmpty() ? null : alive.get(0));
        }
    }

    /** A round is over: a point for the team still alive, then at once the next round, or the end. */
    private void roundWon(Team winner) {
        roundOver = true;
        // nobody fights or builds any more, but everyone moves as they like
        phase = Phase.WAITING;
        if (winner != null) {
            points[teams.indexOf(winner)]++;
        }
        String score = score();
        broadcast(winner == null ? ChatColor.YELLOW + "Round " + round + ": nobody is left, no point. " + score
                : winner.color.coloured() + ChatColor.YELLOW + " wins round " + round + "! " + score,
                winner == null ? Sound.NOTE_BASS : Sound.LEVEL_UP);
        if (winner != null && points[teams.indexOf(winner)] >= pointsToWin()) {
            later(BETWEEN_TICKS, () -> {
                if (phase != Phase.ENDED) {
                    end(winner);
                }
            });
            sidebars();
            return;
        }
        later(NEXT_ROUND_TICKS, () -> nextRound(winner));
        sidebars();
    }

    /** "Red 2 - 1 Blue". */
    private String score() {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            parts.add(teams.get(i).color.chat() + "" + points[i]);
        }
        return teams.size() == 2 ? teams.get(0).color.coloured() + " " + teams.get(0).color.chat() + points[0]
                + ChatColor.GRAY + " - " + teams.get(1).color.chat() + points[1] + " " + teams.get(1).color.coloured()
                : String.join(ChatColor.GRAY + " - ", parts);
    }

    /**
     * The seconds of the countdown before each round after the first, everyone frozen on top: none
     * in Top Bridge, the fight at once.
     */
    int roundCountdown() {
        return 0;
    }

    /**
     * The map as it was, the bridge again, everyone on top, and the fight (after the countdown of
     * roundCountdown, if any): the title says the round, and who won the last one.
     */
    private void nextRound(Team lastWinner) {
        if (phase == Phase.ENDED) {
            return;
        }
        round++;
        roundOver = false;
        instance.rollback();
        removeUnusedBeds();
        buildFight();
        phase = Phase.STARTING;
        int countdown = roundCountdown();
        for (Gamer gamer : gamers.values()) {
            cancel(gamer.respawn);
            gamer.respawn = null;
            gamer.respawning = false;
            gamer.lastDamager = null;
            if (!present(gamer)) {
                continue;
            }
            gamer.eliminated = false;
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null) {
                place(player, gamer);
                String last = lastWinner == null ? ChatColor.YELLOW + "No point"
                        : gamer.team == lastWinner ? ChatColor.GOLD + "Round won" : ChatColor.RED + "Round lost";
                player.sendTitle(countdown > 0 ? ChatColor.YELLOW + "" + ChatColor.BOLD + "ROUND " + round
                        : ChatColor.GREEN + "" + ChatColor.BOLD + "ROUND " + round + " - FIGHT!",
                        last + ChatColor.GRAY + " · " + score());
            }
        }
        if (countdown <= 0) {
            phase = Phase.PLAYING;
            sidebars();
            return;
        }
        sidebars();
        cancel(roundCountdown);
        final int[] left = {countdown};
        roundCountdown = repeat(20L, 20L, () -> {
            if (phase == Phase.ENDED) {
                cancel(roundCountdown);
                return;
            }
            if (left[0] > 0) {
                broadcast(ChatColor.YELLOW + "Round " + round + " starts in " + ChatColor.RED + left[0] + ChatColor.YELLOW
                        + "...", Sound.NOTE_STICKS);
                title(ChatColor.RED + "" + ChatColor.BOLD + left[0], "");
                left[0]--;
                return;
            }
            cancel(roundCountdown);
            roundCountdown = null;
            phase = Phase.PLAYING;
            title(ChatColor.GREEN + "" + ChatColor.BOLD + "FIGHT!", "");
            sidebars();
        });
    }

    @Override
    void left(Gamer gamer) {
        // checkEnd follows: a team left alone wins, a round left without a living enemy ends
    }

    @Override
    List<String> lines(Gamer viewer) {
        List<String> lines = new ArrayList<>();
        if (practice) {
            int left = Math.max(0, PRACTICE_MINUTES * 60 - seconds);
            lines.add(phase == Phase.STARTING ? ChatColor.WHITE + "Starting..."
                    : ChatColor.WHITE + "Practice: " + ChatColor.GREEN + clock(left));
        } else {
            lines.add(ChatColor.WHITE + "Round " + ChatColor.GREEN + round + ChatColor.GRAY + " - first to " + pointsToWin());
        }
        lines.add("");
        int[] kills = kills();
        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            int alive = 0;
            for (UUID member : team.members) {
                Gamer gamer = gamers.get(member);
                if (gamer != null && !gamer.eliminated && present(gamer)) {
                    alive++;
                }
            }
            String state = practice ? ChatColor.GREEN + "" + kills[i] + ChatColor.GRAY + " kills"
                    : ChatColor.GREEN + "" + points[i] + ChatColor.GRAY + " (" + alive + " alive)";
            lines.add(team.color.letter() + " " + ChatColor.WHITE + team.color.displayName() + ": " + state
                    + (team == viewer.team ? ChatColor.GRAY + " YOU" : ""));
        }
        lines.add("");
        lines.add(ChatColor.WHITE + "Kills: " + ChatColor.GREEN + viewer.kills);
        lines.add(ChatColor.WHITE + "Map: " + ChatColor.GREEN + instance.map().world());
        return lines;
    }
}
