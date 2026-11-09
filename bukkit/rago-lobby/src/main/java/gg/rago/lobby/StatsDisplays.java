package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * What the stats show in the lobby, each player reading their own:
 * <ul>
 * <li>a stats NPC: a Steve holding a book, with the player's profile floating above its head
 * (Network level and rank, wins, losses, kills, best streak, wins of the day, ELO of each ranked
 * mode); a click opens {@link StatsMenu};
 * <li>a leaderboard wall: boards side by side, each with its top 10, the player's own place and
 * when it starts again. A click on a board changes its period (day, week, ever) for that player,
 * or its mode for an ELO board. A board with several stats goes to the next one on a right
 * click, and changes the period or the mode on a left click.
 * </ul>
 * Main thread only.
 */
final class StatsDisplays {

    /** Rows of a board. */
    static final int TOP = 10;
    /** Lines of a board: title, subtitle, blank, the top 10, blank, the player, blank, reset, two clicks. */
    static final int BOARD_LINES = 3 + TOP + 6;
    /** Space between the centres of two boards of a wall, in blocks. */
    static final double BOARD_GAP = 5.5;
    /** Height of a board's lowest line above its point. */
    private static final double BOARD_BOTTOM = 1.2;
    private static final int PROFILE_LINES = 10;
    private static final long CLICK_COOLDOWN_MS = 300;

    /** What one player looks at on one board: which of its stats, and which period or mode. */
    private static final class Choice {
        int stat;
        int option;
    }

    private final LobbySettings settings;
    private final StatsBook book;
    private final StatsMenu menu;
    private final FakeEntities fakes;
    private final List<GameMode> ranked;
    private final Map<UUID, Map<String, Choice>> choices = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    StatsDisplays(LobbySettings settings, StatsBook book, StatsMenu menu, FakeEntities fakes) {
        this.settings = settings;
        this.book = book;
        this.menu = menu;
        this.fakes = fakes;
        this.ranked = StatsMenu.rankedModes(settings);
    }

    // --- Stats NPC ------------------------------------------------------------------------------

    /** A Steve holding a book on the point, the player's profile above it. */
    void addStatsNpc(final LobbyNpc npc, String bodyName, Location base) {
        FakeEntities.Clicker open = (player, left) -> {
            if (cooled(player)) {
                menu.open(player);
            }
        };
        fakes.add(new FakePlayer(base, bodyName, new ItemStack(Material.BOOK), open));
        Location bottom = base.clone().add(0, LobbyNpcs.ABOVE_HEAD, 0);
        fakes.add(new FakeHologram(bottom, PROFILE_LINES, viewer -> profile(npc, viewer), open));
    }

    /** The lines above a stats NPC, for this player. */
    List<String> profile(LobbyNpc npc, Player viewer) {
        long now = System.currentTimeMillis();
        String day = StatsPeriod.dayKey(now);
        String week = StatsPeriod.weekKey(now);
        PlayerStats stats = book.get(viewer.getUniqueId());
        int level = book.level(viewer.getUniqueId());
        List<String> lines = new ArrayList<>();
        lines.add(npc.name());
        lines.add(ChatColor.GRAY + "Network level: " + book.levels().tag(level));
        lines.add(ChatColor.GRAY + "Wins: " + ChatColor.GREEN + (stats == null ? 0 : stats.wins(StatsPeriod.ALL, null, day, week)));
        lines.add(ChatColor.GRAY + "Losses: " + ChatColor.RED + (stats == null ? 0 : stats.losses()));
        lines.add(ChatColor.GRAY + "Kills: " + ChatColor.GREEN + (stats == null ? 0 : stats.kills(StatsPeriod.ALL, null, day, week)));
        lines.add(ChatColor.GRAY + "Best win streak: " + ChatColor.YELLOW + (stats == null ? 0 : stats.best(StatsPeriod.ALL, null, day, week)));
        lines.add(ChatColor.GRAY + "Wins today: " + ChatColor.GREEN
                + (stats == null ? 0 : stats.wins(StatsPeriod.DAY, null, day, week)));
        // The ELO of the ranked modes, two per line.
        for (int i = 0; i < 4; i += 2) {
            StringBuilder line = new StringBuilder();
            for (int j = i; j < i + 2 && j < ranked.size(); j++) {
                if (line.length() > 0) {
                    line.append(ChatColor.DARK_GRAY).append(" · ");
                }
                GameMode mode = ranked.get(j);
                line.append(ChatColor.GRAY).append("Elo ").append(StatsMenu.shortName(mode)).append(": ")
                        .append(book.ladder().format(book.elo(viewer.getUniqueId(), mode.id())));
            }
            lines.add(line.toString());
        }
        lines.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK FOR YOUR STATS");
        return lines;
    }

    // --- Leaderboards ---------------------------------------------------------------------------

    /** The boards of a wall side by side, facing the spawn, the first one on the left. */
    void addWall(final LobbyNpc wall, Location base) {
        List<List<BoardStat>> boards = wall.boards();
        double yaw = Math.toRadians(base.getYaw());
        // Seen from the front (looking at the wall), the right is (cos yaw, sin yaw).
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        for (int i = 0; i < boards.size(); i++) {
            final List<BoardStat> stats = boards.get(i);
            final String key = wall.point() + "#" + i;
            double offset = (i - (boards.size() - 1) / 2.0) * BOARD_GAP;
            Location bottom = base.clone().add(rightX * offset, BOARD_BOTTOM, rightZ * offset);
            fakes.add(new FakeHologram(bottom, BOARD_LINES, viewer -> board(stats, key, viewer, System.currentTimeMillis()),
                    (player, left) -> {
                        if (cooled(player)) {
                            next(player, stats, key, left);
                        }
                    }));
        }
        if (!ChatColor.stripColor(wall.name()).isEmpty()) {
            final List<String> title = new ArrayList<>();
            title.add(wall.name());
            Location top = base.clone().add(0, BOARD_BOTTOM + BOARD_LINES * FakeHologram.LINE + 0.3, 0);
            fakes.add(new FakeHologram(top, 1, viewer -> title, null));
        }
    }

    /** The lines of a board for this player. */
    List<String> board(List<BoardStat> stats, String key, Player viewer, long now) {
        Choice choice = choice(viewer.getUniqueId(), key);
        BoardStat stat = stats.get(choice.stat % stats.size());
        StatsPeriod period = StatsPeriod.values()[choice.option % StatsPeriod.values().length];
        GameMode mode = stat.choice() != BoardStat.Choice.RANKED_MODE || ranked.isEmpty() ? null
                : ranked.get(choice.option % ranked.size());
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.AQUA + "" + ChatColor.BOLD + stat.title());
        switch (stat.choice()) {
            case PERIOD:
                lines.add(ChatColor.GRAY + "All modes " + ChatColor.DARK_GRAY + "· " + ChatColor.WHITE + period.label());
                break;
            case RANKED_MODE:
                lines.add(ChatColor.GRAY + (mode == null ? "No ranked mode" : ChatColor.stripColor(mode.name())));
                break;
            default:
                lines.add(ChatColor.GRAY + "All modes " + ChatColor.DARK_GRAY + "· " + ChatColor.WHITE + StatsPeriod.ALL.label());
                break;
        }
        lines.add("");
        StatsBook.Ranking ranking = stat.choice() != BoardStat.Choice.RANKED_MODE || mode != null
                ? book.ranking(stat, period, mode == null ? null : mode.id(), now) : null;
        List<StatsBook.Row> rows = ranking == null ? new ArrayList<StatsBook.Row>() : ranking.rows();
        for (int i = 0; i < TOP; i++) {
            if (i < rows.size()) {
                StatsBook.Row row = rows.get(i);
                lines.add(ChatColor.YELLOW + "" + (i + 1) + ". " + ChatColor.WHITE + row.name() + ChatColor.GRAY + " - "
                        + shown(stat, row.value()));
            } else if (i == 0) {
                lines.add(ChatColor.GRAY + "Nobody yet");
            } else {
                lines.add("");
            }
        }
        lines.add("");
        StatsBook.Row own = ranking == null ? null : ranking.rowOf(viewer.getUniqueId());
        lines.add(own == null ? ChatColor.GRAY + "You: " + ChatColor.DARK_GRAY + "not ranked yet"
                : ChatColor.GRAY + "You: " + ChatColor.YELLOW + "#" + ranking.position(viewer.getUniqueId())
                        + ChatColor.GRAY + " - " + shown(stat, own.value()));
        lines.add("");
        switch (stat.choice()) {
            case PERIOD:
                lines.add(ChatColor.GRAY + period.resetText(now));
                break;
            case RANKED_MODE:
                lines.add(ChatColor.GRAY + "Season Elo");
                break;
            default:
                lines.add(ChatColor.GRAY + StatsPeriod.ALL.resetText(now));
                break;
        }
        if (stats.size() > 1) {
            lines.add(ChatColor.GOLD + "Right click: next leaderboard");
            if (stat.choice() == BoardStat.Choice.PERIOD) {
                lines.add(ChatColor.GOLD + "Left click: change the period");
            } else if (stat.choice() == BoardStat.Choice.RANKED_MODE) {
                lines.add(ChatColor.GOLD + "Left click: change the mode");
            }
        } else if (stat.choice() == BoardStat.Choice.PERIOD) {
            lines.add(ChatColor.GOLD + "Click to change the period");
        } else if (stat.choice() == BoardStat.Choice.RANKED_MODE) {
            lines.add(ChatColor.GOLD + "Click to change the mode");
        }
        return lines;
    }

    /**
     * The value a board shows: the level for the Network level (ranked by XP), the division with
     * the ELO ("Gold 245"), the number otherwise.
     */
    private String shown(BoardStat stat, int value) {
        if (stat == BoardStat.LEVEL) {
            return book.levels().colour(book.levels().level(value)) + book.levels().level(value);
        }
        if (stat == BoardStat.ELO) {
            return book.ladder().format(value);
        }
        return ChatColor.YELLOW + String.valueOf(value);
    }

    private Choice choice(UUID player, String key) {
        Map<String, Choice> mine = choices.get(player);
        if (mine == null) {
            mine = new HashMap<>();
            choices.put(player, mine);
        }
        Choice choice = mine.get(key);
        if (choice == null) {
            choice = new Choice();
            mine.put(key, choice);
        }
        return choice;
    }

    /**
     * A click on a board. With one stat, any click changes its period or mode. With several, a
     * right click goes to the next stat (back to its first period or mode), a left click changes
     * the period or the mode.
     */
    void next(Player player, List<BoardStat> stats, String key, boolean left) {
        Choice choice = choice(player.getUniqueId(), key);
        if (stats.size() > 1 && !left) {
            choice.stat = (choice.stat + 1) % stats.size();
            choice.option = 0;
            return;
        }
        BoardStat stat = stats.get(choice.stat % stats.size());
        int count;
        switch (stat.choice()) {
            case PERIOD:
                count = StatsPeriod.values().length;
                break;
            case RANKED_MODE:
                count = Math.max(1, ranked.size());
                break;
            default:
                count = 1;
                break;
        }
        choice.option = (choice.option + 1) % count;
    }

    private boolean cooled(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        return last == null || now - last >= CLICK_COOLDOWN_MS;
    }

    void forget(UUID player) {
        choices.remove(player);
        lastClick.remove(player);
    }
}
