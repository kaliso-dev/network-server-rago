package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * A duel of two sides, red and blue, in rounds: a player who falls into the void or dies is out
 * of the round (watching from above); when a whole side is out, the other side gets the point and
 * the next round starts on the arena as built. The first side to the target wins. Each fall or
 * death after a hit is a kill of the one who hit, in the lobby's stats.
 */
abstract class Duel extends PracticeGame {

    static final Color[] COLOURS = {Color.RED, Color.BLUE};
    static final ChatColor[] CHAT = {ChatColor.RED, ChatColor.BLUE};
    static final int[] WOOL = {Shapes.RED, Shapes.BLUE};
    static final String[] TEAMS = {"Red", "Blue"};

    final int target;
    final int[] points = new int[2];
    /** The players out of the current round. */
    final Set<UUID> out = new HashSet<>();

    Duel(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, int target) {
        super(host, mode, arena, teams);
        this.target = target;
    }

    /** The duel's name in the messages. */
    abstract String title();

    /** The goal, after "first to": "3 points". */
    String goal() {
        return target + (target == 1 ? " point" : " points");
    }

    /** True if the white blocks of the kit take the colour of the player's side. */
    boolean colouredBlocks() {
        return true;
    }

    @Override
    void intro(Player player) {
        int side = sideOf(player.getUniqueId());
        // the name of each player in the colour of their side, above their head and in the tab list
        plugin.lobby().setGameNameTag(player, CHAT[Math.max(0, side)].toString());
        player.sendMessage(ChatColor.GREEN + title() + " against " + ChatColor.WHITE + namesOf(1 - side) + ChatColor.GREEN
                + ": first to " + goal() + " wins.");
        if (sides.get(side).size() > 1) {
            player.sendMessage(ChatColor.GRAY + "Your team: " + CHAT[side] + namesOf(side));
        }
    }

    @Override
    void roundReset() {
        out.clear();
    }

    @Override
    ChatColor sideColour(int side) {
        return CHAT[Math.max(0, Math.min(CHAT.length - 1, side))];
    }

    @Override
    void fell(Player player) {
        out(player, true);
    }

    @Override
    void lethal(Player victim, Player killer) {
        out(victim, false);
    }

    /**
     * The player is out of this round: the kill goes to the last one who hit them; when their
     * whole side is out, the other side scores.
     */
    void out(Player player, boolean fell) {
        UUID id = player.getUniqueId();
        int side = sideOf(id);
        if (side < 0 || phase != Phase.PLAYING || out.contains(id)) {
            return;
        }
        UUID by = killerOf(id);
        deathMessage(player, by, fell);
        if (by != null && counts()) {
            plugin.lobby().recordKill(mode.id(), by, id);
        }
        out.add(id);
        if (alive(side)) {
            spectate(player);
            return;
        }
        point(1 - side);
    }

    void deathMessage(Player player, UUID by, boolean fell) {
        int side = sideOf(player.getUniqueId());
        String victim = CHAT[side] + player.getDisplayName() + ChatColor.GRAY;
        String text;
        if (by == null) {
            text = victim + (fell ? " fell into the void." : " died.");
        } else {
            String killer = CHAT[Math.max(0, sideOf(by))] + nameOf(by) + ChatColor.GRAY;
            text = victim + (fell ? " was knocked into the void by " : " was killed by ") + killer + ".";
        }
        broadcast(text, null);
    }

    @Override
    boolean isOut(UUID player) {
        return out.contains(player);
    }

    /** True while a player of this side is still in the round. */
    boolean alive(int side) {
        for (Player player : online(side)) {
            if (!out.contains(player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    /** A point for this side: the round is over, the next one starts unless the side reached the target. */
    void point(int side) {
        if (phase != Phase.PLAYING) {
            return;
        }
        points[side]++;
        replayChat(CHAT[side] + "Point for " + TEAMS[side] + ChatColor.GRAY + " (" + points[0] + " - " + points[1] + ")");
        for (Player player : online()) {
            int mine = sideOf(player.getUniqueId());
            player.sendMessage((mine == side ? ChatColor.GREEN + "Point for your team" : ChatColor.RED + "Point for " + TEAMS[side])
                    + ChatColor.GRAY + " (" + points[mine] + " - " + points[1 - mine] + ")");
            pointSound(player, mine == side);
        }
        if (points[side] >= target) {
            finish(side, null);
        } else {
            startRound();
        }
    }

    /** The sound of a point for this player: their side's point, or the other side's. */
    void pointSound(Player player, boolean ours) {
        player.playSound(player.getLocation(), ours ? Sound.ORB_PICKUP : Sound.NOTE_BASS, 1f, 1f);
    }

    /** A player of a side that plays on left: if nobody of their side is left in the round, a point. */
    @Override
    void left(Player player) {
        int side = sideOf(player.getUniqueId());
        if (phase == Phase.PLAYING && !alive(side)) {
            point(1 - side);
        }
    }

    @Override
    String result(int winner) {
        if (winner < 0) {
            return "(" + points[0] + " - " + points[1] + ")";
        }
        return "(" + points[winner] + " - " + points[1 - winner] + ")";
    }

    @Override
    List<String> sidebar(UUID viewer) {
        int side = Math.max(0, sideOf(viewer));
        List<String> lines = new ArrayList<>();
        lines.add(cut(mode.name()));
        lines.add("");
        for (int s = 0; s < 2; s++) {
            lines.add(CHAT[s] + TEAMS[s] + ": " + ChatColor.WHITE + score(s) + (s == side ? ChatColor.GRAY + " (you)" : ""));
        }
        lines.add(ChatColor.GRAY + "First to " + goal());
        lines.add("");
        extraLines(viewer, lines);
        lines.add(ChatColor.WHITE + "Time: " + ChatColor.GREEN + clock(seconds) + ChatColor.GRAY + "  Round " + round);
        lines.add("");
        Player me = Bukkit.getPlayer(viewer);
        lines.add(ChatColor.WHITE + "Your ping: " + Ping.text(me));
        List<Player> them = online(1 - side);
        if (them.size() == 1) {
            lines.add(cut(ChatColor.WHITE + "Opponent: " + CHAT[1 - side] + them.get(0).getDisplayName()));
            lines.add(ChatColor.WHITE + "Their ping: " + Ping.text(them.get(0)));
        } else {
            lines.add(cut(ChatColor.WHITE + "Opponents: " + CHAT[1 - side] + them.size()));
        }
        return lines;
    }

    /** The score of a side in the sidebar. */
    String score(int side) {
        return String.valueOf(points[side]);
    }

    /** More lines of the sidebar, after the score (a phase, a timer...). */
    void extraLines(UUID viewer, List<String> lines) {
    }

    /** A sidebar line is 40 characters at most. */
    static String cut(String line) {
        return line.length() <= 40 ? line : line.substring(0, 40);
    }

    @Override
    void giveKit(Player player) {
        super.giveKit(player);
        if (colouredBlocks()) {
            recolour(player.getInventory(), WOOL[Math.max(0, sideOf(player.getUniqueId()))]);
        }
    }

    /** The white wool, clay and glass of the kit in the colour of the side, as in Bed Wars. */
    static void recolour(PlayerInventory inventory, int colour) {
        ItemStack[] contents = inventory.getContents();
        for (ItemStack item : contents) {
            if (item != null && item.getDurability() == 0 && (item.getType() == Material.WOOL
                    || item.getType() == Material.STAINED_CLAY || item.getType() == Material.STAINED_GLASS
                    || item.getType() == Material.CARPET)) {
                item.setDurability((short) colour);
            }
        }
        inventory.setContents(contents);
    }

    /** Where the players of a side start: in a row across the way they look, one block apart. */
    double across(UUID player) {
        int index = indexOf(player);
        int size = sides.get(Math.max(0, sideOf(player))).size();
        return index - (size - 1) / 2.0;
    }
}
