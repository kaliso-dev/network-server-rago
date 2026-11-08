package gg.rago.practice;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

/**
 * One sumo duel in one arena slot: two players on a round platform, the first one off it gives a
 * point to the other, first to N points wins. Each round starts with a three-second countdown
 * during which both players stand still. Main thread only.
 */
final class SumoMatch {

    enum Phase { COUNTDOWN, FIGHT, ENDED }

    /** Distance of each player from the centre at the start of a round. */
    private static final double START_DISTANCE = 3.0;
    /** Below the platform by this much, a player has fallen. */
    static final double FALL_DEPTH = 3.0;
    /** Time between the end and the return to the lobby, in ticks: the next tick, out of the event. */
    private static final long END_DELAY_TICKS = 1L;

    private final RagoPractice plugin;
    private final SumoHost host;
    private final int slot;
    private final Location centre;
    private final UUID[] players;
    private final String[] names;
    private final DuelScore score;
    private Phase phase = Phase.COUNTDOWN;
    private BukkitTask countdown;

    SumoMatch(RagoPractice plugin, SumoHost host, int slot, Location centre, Player first, Player second, int target) {
        this.plugin = plugin;
        this.host = host;
        this.slot = slot;
        this.centre = centre;
        this.players = new UUID[] {first.getUniqueId(), second.getUniqueId()};
        this.names = new String[] {first.getName(), second.getName()};
        this.score = new DuelScore(target);
    }

    int slot() {
        return slot;
    }

    Phase phase() {
        return phase;
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

    void begin() {
        for (int side = 0; side < 2; side++) {
            Player player = Bukkit.getPlayer(players[side]);
            if (player != null) {
                prepare(player);
                player.sendMessage(ChatColor.GREEN + "Duel sumo contre " + ChatColor.WHITE + names[1 - side]
                        + ChatColor.GREEN + " : le premier à " + score.target() + " points gagne.");
            }
        }
        startRound();
    }

    private void startRound() {
        phase = Phase.COUNTDOWN;
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
            private int left = 3;

            @Override
            public void run() {
                if (left > 0) {
                    broadcast(ChatColor.YELLOW + "" + left + "...", Sound.NOTE_STICKS);
                    left--;
                    return;
                }
                cancelCountdown();
                phase = Phase.FIGHT;
                broadcast(ChatColor.GREEN + "Combat !", Sound.NOTE_PLING);
            }
        }, 0L, 20L);
    }

    /** Where a side stands at the start of a round, facing the other side. */
    Location startOf(int side) {
        Location start = centre.clone().add(side == 0 ? -START_DISTANCE : START_DISTANCE, 0, 0);
        start.setYaw(side == 0 ? -90f : 90f);
        start.setPitch(0f);
        return start;
    }

    /** The player fell off the platform. */
    void fell(Player player) {
        int side = sideOf(player.getUniqueId());
        if (side < 0 || phase != Phase.FIGHT) {
            if (side >= 0 && phase == Phase.COUNTDOWN) {
                player.teleport(startOf(side));
            }
            return;
        }
        int scorer = 1 - side;
        boolean won = score.point(scorer);
        for (int s = 0; s < 2; s++) {
            Player p = Bukkit.getPlayer(players[s]);
            if (p != null) {
                p.sendMessage((s == scorer ? ChatColor.GREEN + "Point pour toi" : ChatColor.RED + "Point pour " + names[scorer])
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
        if (phase != Phase.ENDED) {
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
        for (int side = 0; side < 2; side++) {
            Player player = Bukkit.getPlayer(players[side]);
            if (player == null) {
                continue;
            }
            if (side == winner) {
                player.sendMessage(ChatColor.GOLD + "Victoire !" + ChatColor.GRAY + " (" + score.text(side) + ")"
                        + (forfeit ? ChatColor.GRAY + " " + names[1 - side] + " a abandonné." : ""));
                player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
            } else {
                player.sendMessage(ChatColor.RED + "Défaite." + ChatColor.GRAY + " (" + score.text(side) + ")");
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::backToLobby, END_DELAY_TICKS);
    }

    private void backToLobby() {
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
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && host.matchOf(id) == this) {
                player.sendMessage(ChatColor.RED + "Le duel est arrêté : le serveur redémarre ses modes.");
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
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && host.matchOf(id) == this) {
                player.sendMessage(message);
                player.playSound(player.getLocation(), sound, 1f, 1f);
            }
        }
    }

    private static void prepare(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
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
