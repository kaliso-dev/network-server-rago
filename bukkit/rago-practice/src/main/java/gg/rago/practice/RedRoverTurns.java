package gg.rago.practice;

import gg.rago.game.RedRoverRotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * The turns of a Red Rover (plan 6.5, solution 1), whatever its 1v1 is (a round of the sumo, a
 * clean edit of the block edit...): who fights whom ({@link RedRoverRotation}), what the players
 * read, the sidebar, the time limit of a 1v1, and one who leaves. Each player is a side of their
 * own in the game ({@link RedRover#alone}): one wins it, the others lose it. The two who fight
 * have a place each in the 1v1, 0 (red) and 1 (blue): their start and their colour.
 */
final class RedRoverTurns {

    final RedRoverRotation.Settings settings;
    final RedRoverRotation<UUID> rotation;
    private final PracticeGame game;
    /** The name of the mode, "Red Rover Sumo". */
    private final String title;
    private final Random random;
    /** The hits of each fighter when this 1v1 started (the recap counts them), for the time limit. */
    private final Map<UUID, Integer> hitsBefore = new HashMap<>();

    RedRoverTurns(PracticeGame game, String title, RedRoverRotation.Settings settings, Random random) {
        this.game = game;
        this.title = title;
        this.settings = settings;
        this.random = random;
        List<UUID> order = new ArrayList<>();
        for (List<UUID> side : game.sides) {
            order.addAll(side);
        }
        Collections.shuffle(order, random);
        this.rotation = new RedRoverRotation<>(order, settings.variant, settings.stays, settings.target);
    }

    boolean fights(UUID player) {
        return rotation.fights(player);
    }

    /** This fighter's place in the 1v1, 0 or 1; -1 for one who waits, is out or gone. */
    int slotOf(UUID player) {
        return rotation.fight().indexOf(player);
    }

    /** The fighter in this place of the 1v1, or null. */
    UUID fighter(int slot) {
        List<UUID> fight = rotation.fight();
        return slot >= 0 && slot < fight.size() ? fight.get(slot) : null;
    }

    /** The other fighter of the 1v1. */
    UUID opponentOf(UUID fighter) {
        List<UUID> fight = rotation.fight();
        if (fight.size() != 2 || !fight.contains(fighter)) {
            return null;
        }
        return fight.get(0).equals(fighter) ? fight.get(1) : fight.get(0);
    }

    /** The first line a player reads: whom they play with, how the game is won. */
    void intro(Player player) {
        List<String> others = new ArrayList<>();
        for (UUID id : rotation.players()) {
            if (!id.equals(player.getUniqueId())) {
                others.add(game.nameOf(id));
            }
        }
        player.sendMessage(ChatColor.GREEN + title + " with " + ChatColor.WHITE + String.join(", ", others) + ChatColor.GREEN
                + ": 1v1 in turn, " + (settings.variant == RedRoverRotation.Variant.POINTS
                ? "the first to " + settings.target + " 1v1 won wins." : "a lost 1v1 is out, the last one in wins."));
        player.sendMessage(ChatColor.GRAY + "Who waits watches the 1v1.");
    }

    /** A 1v1 starts (the round reset): the hits so far, for the time limit. */
    void roundStarted() {
        hitsBefore.clear();
        for (UUID id : rotation.fight()) {
            hitsBefore.put(id, game.recap.hits(id));
        }
    }

    /** The fight is on: who fights whom, in the replay's chat. */
    void onFight() {
        List<UUID> fight = rotation.fight();
        if (fight.size() == 2) {
            game.replayChat(ChatColor.WHITE + game.nameOf(fight.get(0)) + ChatColor.GRAY + " vs " + ChatColor.WHITE
                    + game.nameOf(fight.get(1)));
        }
    }

    /**
     * A second of the 1v1 went by (its seconds so far): at the time limit, the one who landed more
     * hits in it wins it, either of the two if they landed as many.
     */
    void second(int seconds) {
        List<UUID> fight = rotation.fight();
        if (settings.roundSeconds <= 0 || seconds < settings.roundSeconds || fight.size() != 2
                || game.phase != PracticeGame.Phase.PLAYING) {
            return;
        }
        UUID a = fight.get(0);
        UUID b = fight.get(1);
        int ha = game.recap.hits(a) - hitsBefore.getOrDefault(a, 0);
        int hb = game.recap.hits(b) - hitsBefore.getOrDefault(b, 0);
        UUID winner = ha == hb ? (random.nextBoolean() ? a : b) : ha > hb ? a : b;
        game.broadcast(ChatColor.GRAY + "Time! The one who landed more hits wins the 1v1.", null);
        won(winner);
    }

    /** This fighter won the 1v1: the game over, or the next 1v1 at once. */
    void won(UUID winner) {
        if (winner == null || game.phase == PracticeGame.Phase.ENDED || !rotation.fights(winner)) {
            return;
        }
        UUID loser = opponentOf(winner);
        UUID champion = rotation.won(winner);
        for (Player player : game.online()) {
            PracticeGame.sound(player, player.getUniqueId().equals(winner) ? Sound.ORB_PICKUP : Sound.NOTE_BASS, 1f);
        }
        if (settings.variant == RedRoverRotation.Variant.POINTS) {
            game.broadcast(ChatColor.GREEN + game.nameOf(winner) + ChatColor.GRAY + " wins the 1v1 (" + rotation.wins(winner) + "/"
                    + settings.target + ").", null);
        } else {
            game.broadcast(ChatColor.GREEN + game.nameOf(winner) + ChatColor.GRAY + " wins the 1v1: " + ChatColor.RED
                    + game.nameOf(loser) + ChatColor.GRAY + " is out.", null);
        }
        if (champion != null) {
            game.finish(game.sideOf(champion), null);
            tellTop(champion);
            return;
        }
        announceNext();
        game.startRound();
    }

    /** Who fights next, and who waits. */
    void announceNext() {
        List<UUID> fight = rotation.fight();
        if (fight.size() != 2) {
            return;
        }
        StringBuilder waiting = new StringBuilder();
        for (UUID id : rotation.waiting()) {
            waiting.append(waiting.length() == 0 ? "" : ", ").append(game.nameOf(id));
        }
        game.broadcast(ChatColor.YELLOW + "Next: " + ChatColor.WHITE + game.nameOf(fight.get(0)) + ChatColor.GRAY + " vs "
                + ChatColor.WHITE + game.nameOf(fight.get(1)) + (waiting.length() == 0 ? "" : ChatColor.GRAY + " (" + waiting
                + " waits)"), null);
    }

    /**
     * The game's leave: one who leaves is out of it (a loss). The last one in wins; when a fighter
     * left, the next 1v1 starts, the other fighter staying on.
     */
    void leave(Player player, boolean toLobby) {
        UUID id = player.getUniqueId();
        game.plugin.spectators().playerLeft(game, player);
        if (game.phase != PracticeGame.Phase.ENDED && rotation.isIn(id)) {
            game.plugin.lobby().forfeited(game.mode.id(), id);
            game.host.release(id);
            boolean fought = rotation.fights(id);
            UUID last = rotation.left(id);
            if (last != null) {
                game.finish(game.sideOf(last), ChatColor.GRAY + game.nameOf(id) + " gave up.");
                tellTop(last);
            } else {
                game.broadcast(ChatColor.GRAY + game.nameOf(id) + " left the game.", null);
                if (fought) {
                    announceNext();
                    game.startRound();
                } else {
                    game.sidebars();
                }
            }
        }
        game.host.release(id);
        if (toLobby) {
            game.plugin.lobby().sendToLobby(player);
        }
    }

    /** How many the top of the end shows (the owner's ask of 22/11: the top 3). */
    static final int TOP = 3;

    /** Its places, coloured as the killers' of the end of a Bed Wars game. */
    private static final String[] PLACES = {
        ChatColor.YELLOW + "" + ChatColor.BOLD + "1st",
        ChatColor.GOLD + "" + ChatColor.BOLD + "2nd",
        ChatColor.RED + "" + ChatColor.BOLD + "3rd",
    };

    /** The top 3 of the game, once it is over: the winner first, then by 1v1 won ({@link RedRoverRotation#top}). */
    private void tellTop(UUID champion) {
        List<UUID> top = rotation.top(champion, TOP);
        game.broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + "Top " + top.size(), null);
        for (int i = 0; i < top.size(); i++) {
            game.broadcast(topLine(i, game.nameOf(top.get(i)), rotation.wins(top.get(i))), null);
        }
    }

    /** "  1st - Alice - 3 1v1 won": a place of the top, from 0. */
    static String topLine(int place, String name, int won) {
        return "  " + PLACES[place] + ChatColor.GRAY + " - " + ChatColor.WHITE + name + ChatColor.GRAY + " - " + ChatColor.WHITE + won
                + ChatColor.GRAY + " 1v1 won";
    }

    /** The 1v1 won of each player: "(Alice 3, Bob 1, Carl 0)". */
    String result() {
        StringBuilder text = new StringBuilder("(");
        for (Map.Entry<UUID, Integer> score : rotation.scores().entrySet()) {
            text.append(text.length() == 1 ? "" : ", ").append(game.nameOf(score.getKey())).append(' ').append(score.getValue());
        }
        return text.append(')').toString();
    }

    /**
     * The sidebar: the mode, how the game is won, the two who fight (with their points in this
     * 1v1 when the mode counts some, else null), then each player's 1v1 won (or in, out).
     */
    List<String> sidebar(UUID viewer, int[] fightPoints) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GREEN + "" + ChatColor.BOLD + title);
        lines.add(ChatColor.GRAY + settings.goal());
        lines.add("");
        List<UUID> fight = rotation.fight();
        if (fight.size() == 2) {
            for (int slot = 0; slot < 2; slot++) {
                String points = fightPoints == null ? "" : ChatColor.GRAY + ": " + ChatColor.WHITE + fightPoints[slot];
                lines.add(Duel.cut(Duel.CHAT[slot] + game.nameOf(fight.get(slot)) + points));
                if (slot == 0) {
                    lines.add(ChatColor.GRAY + "vs");
                }
            }
            lines.add(" ");
        }
        for (Map.Entry<UUID, Integer> score : rotation.scores().entrySet()) {
            UUID id = score.getKey();
            ChatColor colour = !rotation.isIn(id) ? ChatColor.DARK_GRAY : id.equals(viewer) ? ChatColor.GREEN : ChatColor.WHITE;
            String state = settings.variant == RedRoverRotation.Variant.POINTS ? String.valueOf(score.getValue())
                    : rotation.isIn(id) ? "in" : "out";
            lines.add(Duel.cut(colour + game.nameOf(id) + ChatColor.GRAY + ": " + ChatColor.YELLOW + state));
        }
        return lines;
    }
}
