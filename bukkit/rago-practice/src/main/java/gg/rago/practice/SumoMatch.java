package gg.rago.practice;

import gg.rago.lobby.api.EndOfGame;
import gg.rago.lobby.api.ReplayRecording;
import java.util.Collections;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

/**
 * One sumo duel in one arena slot: two players on a round platform, the first one off it gives a
 * point to the other, first to N points wins (one round by default). Each round starts with a three-second countdown,
 * in the chat and as a big title (3, 2, 1, Combat !), during which both players stand still. Main
 * thread only.
 */
final class SumoMatch implements Spectators.Watched {

    enum Phase { COUNTDOWN, FIGHT, ENDED }

    /** Distance of each player from the centre at the start of a round. */
    private static final double START_DISTANCE = 3.0;
    /** Below the platform by this much, a player has fallen. */
    static final double FALL_DEPTH = 3.0;
    /** Time between the end and the return to the lobby, in ticks: the next tick, out of the event. */
    private static final long END_DELAY_TICKS = 1L;
    /**
     * Title timings, in ticks: each number shows at once and stays until the next one, and
     * "Combat !" fades out quickly so that it does not hide the fight.
     */
    private static final int TITLE_FADE_IN = 0;
    private static final int TITLE_STAY = 22;
    private static final int TITLE_FADE_OUT = 6;
    /** Colours of the countdown titles: 1 in red, 2 in gold, 3 in yellow. */
    private static final ChatColor[] COUNTDOWN_COLOURS = {ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW};

    private final RagoPractice plugin;
    private final SumoHost host;
    private final int slot;
    private final Location centre;
    /** The name of the map, or null on the plain platform. */
    private final String map;
    private final UUID[] players;
    private final String[] names;
    private final DuelScore score;
    /** The hits and combos of both players, told at the end. */
    private final MatchRecap recap = new MatchRecap();
    private Phase phase = Phase.COUNTDOWN;
    private BukkitTask countdown;
    /** The rounds started: the first one waits 3 seconds, the next ones a second. */
    private int round;
    /** The recording of the duel for /replay, or null. */
    private ReplayRecording replay;

    SumoMatch(RagoPractice plugin, SumoHost host, int slot, Location centre, String map, Player first, Player second,
            int target) {
        this.plugin = plugin;
        this.host = host;
        this.slot = slot;
        this.centre = centre;
        this.map = map;
        this.players = new UUID[] {first.getUniqueId(), second.getUniqueId()};
        this.names = new String[] {first.getDisplayName(), second.getDisplayName()};
        this.score = new DuelScore(target);
    }

    int slot() {
        return slot;
    }

    Phase phase() {
        return phase;
    }

    /** A hit that went through, in the fight: counted for the recap. */
    void hit(UUID attacker, UUID victim) {
        if (phase == Phase.FIGHT) {
            recap.hit(attacker, victim);
        }
    }

    /** 0 or 1, or -1 if the player is not in this duel. */
    int sideOf(UUID player) {
        return players[0].equals(player) ? 0 : players[1].equals(player) ? 1 : -1;
    }

    UUID[] players() {
        return players.clone();
    }

    /** Height under which a player has fallen off. */
    double fallY() {
        return centre.getY() - FALL_DEPTH;
    }

    /** How far from the middle of the platform those who watch the duel may fly. */
    static final double WATCH_RANGE = 40;

    /** Those who watch the duel (/spectate) stay near the platform. */
    @Override
    public boolean holds(Location location) {
        if (!centre.getWorld().equals(location.getWorld())) {
            return false;
        }
        double dx = location.getX() - centre.getX();
        double dz = location.getZ() - centre.getZ();
        return dx * dx + dz * dz <= WATCH_RANGE * WATCH_RANGE && location.getY() >= fallY() - 20
                && location.getY() <= centre.getY() + WATCH_RANGE;
    }

    /** Above the side of the platform, looking at its middle. */
    @Override
    public Location watchPoint() {
        Location view = centre.clone().add(0, 5, -9);
        view.setYaw(0f);
        view.setPitch(25f);
        return view;
    }

    /** The duel is recorded from now on (null: it is not): red the first player, blue the second. */
    void record(ReplayRecording recording) {
        replay = recording;
        if (recording == null) {
            return;
        }
        for (int side = 0; side < 2; side++) {
            Player player = Bukkit.getPlayer(players[side]);
            if (player != null) {
                recording.actor(player, Duel.CHAT[side]);
            }
        }
    }

    /** A line of the duel's chat for its replay and those who watch it; the players read their own version. */
    private void replayChat(String line) {
        if (replay != null) {
            replay.chat(line);
        }
        plugin.spectators().tell(this, line);
    }

    void begin() {
        for (int side = 0; side < 2; side++) {
            Player player = Bukkit.getPlayer(players[side]);
            if (player != null) {
                prepare(player);
                player.sendMessage(ChatColor.GREEN + "Sumo duel against " + ChatColor.WHITE + names[1 - side]
                        + (map == null ? "" : ChatColor.GREEN + " on " + ChatColor.WHITE + map)
                        + ChatColor.GREEN + (score.target() <= 1 ? ": one round, the first off the platform loses."
                                : ": first to " + score.target() + " points wins."));
            }
        }
        startRound();
    }

    private void startRound() {
        phase = Phase.COUNTDOWN;
        round++;
        final int count = PracticeGame.countdownOf(round, 3);
        for (int side = 0; side < 2; side++) {
            Player player = Bukkit.getPlayer(players[side]);
            if (player != null) {
                player.teleport(startOf(side));
                player.setFallDistance(0f);
                player.setHealth(player.getMaxHealth());
            }
        }
        cancelCountdown();
        countdown = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int left = count;

            @Override
            public void run() {
                if (left > 0) {
                    broadcast(ChatColor.YELLOW + "" + left + "...", Sound.NOTE_STICKS);
                    title(COUNTDOWN_COLOURS[left - 1] + "" + ChatColor.BOLD + left);
                    left--;
                    return;
                }
                cancelCountdown();
                phase = Phase.FIGHT;
                broadcast(ChatColor.GREEN + "Fight!", Sound.NOTE_PLING);
                title(ChatColor.GREEN + "" + ChatColor.BOLD + "Fight!");
            }
        }, 0L, 20L);
    }

    /** Where a side stands at the start of a round, facing the other side. */
    Location startOf(int side) {
        Location start = centre.clone().add(side == 0 ? -START_DISTANCE : START_DISTANCE, 0, 0);
        // a map's platform can rise a little away from its middle: the feet go over it
        for (int i = 0; i < 3 && start.getBlock().getType().isSolid(); i++) {
            start.add(0, 1, 0);
        }
        start.setYaw(side == 0 ? -90f : 90f);
        start.setPitch(0f);
        return start;
    }

    /** The player fell off the platform. */
    void fell(Player player) {
        int side = sideOf(player.getUniqueId());
        if (side < 0 || phase != Phase.FIGHT) {
            if (side >= 0 && phase != Phase.FIGHT) {
                // before the round, or at the end (the one who fell watches the winner)
                player.teleport(startOf(side));
            }
            return;
        }
        int scorer = 1 - side;
        // A point is a kill for the scorer and a death for the player who fell.
        plugin.lobby().recordKill(SumoHost.MODE_ID, players[scorer], players[side]);
        boolean won = score.point(scorer);
        replayChat(Duel.CHAT[scorer] + "Point for " + names[scorer] + ChatColor.GRAY + " (" + score.text(0) + ")");
        for (int s = 0; s < 2; s++) {
            Player p = Bukkit.getPlayer(players[s]);
            if (p != null) {
                p.sendMessage((s == scorer ? ChatColor.GREEN + "Your point" : ChatColor.RED + "Point for " + names[scorer])
                        + ChatColor.GRAY + " (" + score.text(s) + ")");
            }
        }
        if (won) {
            end(scorer, false);
        } else {
            startRound();
        }
    }

    /** The player left the duel (left the server, or /spawn): the other side wins. */
    void leave(Player player, boolean toLobbyNow) {
        int side = sideOf(player.getUniqueId());
        if (side < 0) {
            return;
        }
        plugin.spectators().playerLeft(this, player);
        if (phase != Phase.ENDED) {
            // a forfeit: the queues of a ranked mode close for a while (the lobby says which)
            plugin.lobby().forfeited(SumoHost.MODE_ID, player.getUniqueId());
            end(1 - side, true);
        }
        host.release(player.getUniqueId());
        if (toLobbyNow) {
            plugin.lobby().sendToLobby(player);
        }
    }

    private void end(int winner, boolean forfeit) {
        phase = Phase.ENDED;
        cancelCountdown();
        // Counted in the stats and the leaderboards; a forfeit is a loss.
        plugin.lobby().recordMatch(SumoHost.MODE_ID, Collections.singletonList(players[winner]),
                Collections.singletonList(players[1 - winner]));
        for (int side = 0; side < 2; side++) {
            Player player = Bukkit.getPlayer(players[side]);
            if (player == null) {
                continue;
            }
            if (side == winner) {
                player.sendMessage(ChatColor.GOLD + "Victory!" + ChatColor.GRAY + " (" + score.text(side) + ")"
                        + (forfeit ? ChatColor.GRAY + " " + names[1 - side] + " gave up." : ""));
                player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
            } else {
                player.sendMessage(ChatColor.RED + "Defeat." + ChatColor.GRAY + " (" + score.text(side) + ")");
            }
        }
        replayChat(ChatColor.GOLD + "" + ChatColor.BOLD + names[winner] + " won! " + ChatColor.GRAY + "(" + score.text(winner) + ")"
                + (forfeit ? " " + names[1 - winner] + " gave up." : ""));
        if (!recap.isEmpty()) {
            java.util.List<String> lines = recap.lines(java.util.Arrays.asList(Collections.singletonList(players[0]),
                    Collections.singletonList(players[1])), id -> names[sideOf(id)], Duel.CHAT);
            for (UUID id : players) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    for (String line : lines) {
                        player.sendMessage(line);
                    }
                }
            }
            for (String line : lines) {
                replayChat(line);
            }
        }
        if (replay != null) {
            replay.finish(ChatColor.stripColor(names[winner]) + " won (" + score.text(winner) + ")" + (forfeit ? ", forfeit" : ""));
            replay = null;
        }
        // the winner and their victory effect for a few seconds, Play again and Leave usable at once
        int seconds = plugin.lobby().endSeconds();
        if (seconds > 0) {
            for (UUID id : players) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && host.matchOf(id) == this) {
                    EndOfGame.give(player);
                }
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::backToLobby, seconds > 0 ? seconds * 20L : END_DELAY_TICKS);
    }

    private void backToLobby() {
        // those who watch first: none of them still sees through the eyes of a player who left
        plugin.spectators().ended(this);
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            // A player who already left this duel (forfeit, /spawn) is no longer ours.
            if (player != null && host.matchOf(id) == this) {
                host.release(id);
                plugin.lobby().sendToLobby(player);
            }
        }
        host.finished(this);
    }

    /** Ends the duel now, without a winner: the plugin is being disabled. */
    void abort() {
        phase = Phase.ENDED;
        cancelCountdown();
        if (replay != null) {
            replay.cancel();
            replay = null;
        }
        plugin.spectators().ended(this);
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && host.matchOf(id) == this) {
                player.sendMessage(ChatColor.RED + "The duel is stopped: the server is restarting its modes.");
                host.release(id);
                plugin.lobby().sendToLobby(player);
            }
        }
    }

    private void cancelCountdown() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
    }

    private void broadcast(String message, Sound sound) {
        replayChat(message);
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && host.matchOf(id) == this) {
                player.sendMessage(message);
                player.playSound(player.getLocation(), sound, 1f, 1f);
            }
        }
    }

    /** Shows the text as a big title in the middle of both players' screens. */
    @SuppressWarnings("deprecation") // Player#sendTitle is marked "API subject to change" in 1.8.8
    private void title(String text) {
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && host.matchOf(id) == this) {
                Titles.times(plugin.getLogger(), player, TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT);
                player.sendTitle(text, "");
            }
        }
    }

    private void prepare(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        // A sumo gives no items, unless its kit: in the lobby's config.yml has some (a stick...):
        // then the player gets them as they arranged them in the kit editor.
        ItemStack[] kit = plugin.lobby().kit(player, SumoHost.MODE_ID);
        if (kit != null) {
            player.getInventory().setContents(kit);
        }
        player.updateInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setFireTicks(0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }
}
