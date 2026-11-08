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
 * <li>a stats NPC: a Steve holding a book, with the player's profile floating above its head (wins,
 * losses, best streak, wins of the day, ELO of each ranked mode); a click opens {@link StatsMenu};
 * <li>a leaderboard wall: boards side by side, each with its top 10, the player's own place and
 * when it starts again; a click on a board changes its period (day, week, ever) for that player,
 * or its mode for an ELO board.
 * </ul>
 * Main thread only.
 */
final class StatsDisplays {

    /** Rows of a board. */
    static final int TOP = 10;
    /** Lines of a board: title, subtitle, blank, the top 10, blank, the player, blank, reset, click. */
    static final int BOARD_LINES = 3 + TOP + 6;
    /** Space between the centres of two boards of a wall, in blocks. */
    static final double BOARD_GAP = 5.5;
    /** Height of a board's lowest line above its point. */
    private static final double BOARD_BOTTOM = 1.2;
    private static final int PROFILE_LINES = 8;
    private static final long CLICK_COOLDOWN_MS = 300;

    private final LobbySettings settings;
    private final StatsBook book;
    private final StatsMenu menu;
    private final FakeEntities fakes;
    private final List<GameMode> ranked;
    /** Each player's choice on each board: a period, or a ranked mode for an ELO board. */
    private final Map<UUID, Map<String, Integer>> choices = new HashMap<>();
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
        FakeEntities.Clicker open = player -> {
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
        List<String> lines = new ArrayList<>();
        lines.add(npc.name());
        lines.add(ChatColor.GRAY + "Victoires : " + ChatColor.GREEN + (stats == null ? 0 : stats.wins(StatsPeriod.ALL, null, day, week)));
        lines.add(ChatColor.GRAY + "Défaites : " + ChatColor.RED + (stats == null ? 0 : stats.losses()));
        lines.add(ChatColor.GRAY + "Meilleure série : " + ChatColor.YELLOW + (stats == null ? 0 : stats.best(StatsPeriod.ALL, null, day, week)));
        lines.add(ChatColor.GRAY + "Victoires aujourd'hui : " + ChatColor.GREEN
                + (stats == null ? 0 : stats.wins(StatsPeriod.DAY, null, day, week)));
        // The ELO of the ranked modes, two per line.
        for (int i = 0; i < 4; i += 2) {
            StringBuilder line = new StringBuilder();
            for (int j = i; j < i + 2 && j < ranked.size(); j++) {
                if (line.length() > 0) {
                    line.append(ChatColor.DARK_GRAY).append(" · ");
                }
                GameMode mode = ranked.get(j);
                line.append(ChatColor.GRAY).append("Elo ").append(StatsMenu.shortName(mode)).append(" : ")
                        .append(ChatColor.AQUA).append(book.elo(viewer.getUniqueId(), mode.id()));
            }
            lines.add(line.toString());
        }
        lines.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "CLIQUE POUR TES STATS");
        return lines;
    }

    // --- Leaderboards ---------------------------------------------------------------------------

    /** The boards of a wall side by side, facing the spawn, the first one on the left. */
    void addWall(final LobbyNpc wall, Location base) {
        List<BoardStat> boards = wall.boards();
        double yaw = Math.toRadians(base.getYaw());
        // Seen from the front (looking at the wall), the right is (cos yaw, sin yaw).
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        for (int i = 0; i < boards.size(); i++) {
            final BoardStat stat = boards.get(i);
            final String key = wall.point() + "#" + i;
            double offset = (i - (boards.size() - 1) / 2.0) * BOARD_GAP;
            Location bottom = base.clone().add(rightX * offset, BOARD_BOTTOM, rightZ * offset);
            fakes.add(new FakeHologram(bottom, BOARD_LINES, viewer -> board(stat, key, viewer, System.currentTimeMillis()),
                    player -> {
                        if (cooled(player)) {
                            next(player, stat, key);
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
    List<String> board(BoardStat stat, String key, Player viewer, long now) {
        int choice = choice(viewer.getUniqueId(), key);
        StatsPeriod period = StatsPeriod.values()[choice % StatsPeriod.values().length];
        GameMode mode = stat.periodic() || ranked.isEmpty() ? null : ranked.get(choice % ranked.size());
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.AQUA + "" + ChatColor.BOLD + stat.title());
        if (stat.periodic()) {
            lines.add(ChatColor.GRAY + "Tous les modes " + ChatColor.DARK_GRAY + "· " + ChatColor.WHITE + period.label());
        } else {
            lines.add(ChatColor.GRAY + (mode == null ? "Aucun mode classé" : ChatColor.stripColor(mode.name())));
        }
        lines.add("");
        StatsBook.Ranking ranking = stat.periodic() || mode != null
                ? book.ranking(stat, period, mode == null ? null : mode.id(), now) : null;
        List<StatsBook.Row> rows = ranking == null ? new ArrayList<StatsBook.Row>() : ranking.rows();
        for (int i = 0; i < TOP; i++) {
            if (i < rows.size()) {
                StatsBook.Row row = rows.get(i);
                lines.add(ChatColor.YELLOW + "" + (i + 1) + ". " + ChatColor.WHITE + row.name() + ChatColor.GRAY + " - "
                        + ChatColor.YELLOW + row.value());
            } else if (i == 0) {
                lines.add(ChatColor.GRAY + "Personne pour l'instant");
            } else {
                lines.add("");
            }
        }
        lines.add("");
        StatsBook.Row own = ranking == null ? null : ranking.rowOf(viewer.getUniqueId());
        lines.add(own == null ? ChatColor.GRAY + "Toi : " + ChatColor.DARK_GRAY + "pas encore classé"
                : ChatColor.GRAY + "Toi : " + ChatColor.YELLOW + "#" + ranking.position(viewer.getUniqueId())
                        + ChatColor.GRAY + " - " + ChatColor.YELLOW + own.value());
        lines.add("");
        lines.add(ChatColor.GRAY + (stat.periodic() ? period.resetText(now) : "Elo de la saison"));
        lines.add(ChatColor.GOLD + (stat.periodic() ? "Clique pour changer la période" : "Clique pour changer de mode"));
        return lines;
    }

    private int choice(UUID player, String key) {
        Map<String, Integer> mine = choices.get(player);
        Integer choice = mine == null ? null : mine.get(key);
        return choice == null ? 0 : choice;
    }

    /** A click on a board: the next period, or the next ranked mode. */
    private void next(Player player, BoardStat stat, String key) {
        Map<String, Integer> mine = choices.get(player.getUniqueId());
        if (mine == null) {
            mine = new HashMap<>();
            choices.put(player.getUniqueId(), mine);
        }
        int count = stat.periodic() ? StatsPeriod.values().length : Math.max(1, ranked.size());
        mine.put(key, (choice(player.getUniqueId(), key) + 1) % count);
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
