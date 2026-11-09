package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * What the stats show in the lobby, each player reading their own:
 * <ul>
 * <li>a stats NPC: a Steve holding a book, with the player's profile floating above its head
 * (Network level and rank, wins, losses, kills, best streak, wins of the day, ELO of each ranked
 * mode); a click opens {@link StatsMenu};
 * <li>a leaderboard wall: boards side by side, each with its top 10, the player's own place and
 * when it starts again. A click on a board changes its period (day, week, ever) for that player,
 * or its mode for an ELO board. A board with several stats goes to the next one on a right
 * click, and changes the period or the mode on a left click; a click (either) on its line "Click
 * here: next leaderboard" or "Click here: change the period" does what the line says. A board is
 * clicked from as far as {@link #AIM_REACH} blocks, anywhere on its text: the player's look is
 * followed to it (the armor stands of its lines are too thin to aim at, and an entity is only
 * clicked from 3 blocks).
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
    /** How far a board is clicked from, in blocks. */
    static final double AIM_REACH = 16;
    /** Half the width of a board's text, in blocks (its lines are about 4 blocks wide). */
    static final double AIM_HALF_WIDTH = 2.3;
    /** The lines "Click here": the first two after the reset line. */
    static final int FIRST_CLICK_LINE = 3 + TOP + 4;
    /** The colour of the place above a step: gold, silver, bronze, then grey. */
    private static final ChatColor[] PODIUM_COLOURS = {ChatColor.GOLD, ChatColor.WHITE, ChatColor.RED, ChatColor.GRAY};

    /** A board in the world: the point under its text, and what it shows. */
    private static final class Spot {
        final World world;
        final double x;
        final double z;
        /** Where the name of its last line floats. */
        final double bottom;
        final List<BoardStat> stats;
        final String key;

        Spot(Location bottom, List<BoardStat> stats, String key) {
            this.world = bottom.getWorld();
            this.x = bottom.getX();
            this.z = bottom.getZ();
            this.bottom = bottom.getY();
            this.stats = stats;
            this.key = key;
        }
    }

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
    /** Where the boards are, for the clicks from a distance. */
    private final List<Spot> spots = new ArrayList<>();
    /** The skins of the podiums: none until {@link #setSkins}. */
    private Function<UUID, Skin> skins = player -> null;

    StatsDisplays(LobbySettings settings, StatsBook book, StatsMenu menu, FakeEntities fakes) {
        this.settings = settings;
        this.book = book;
        this.menu = menu;
        this.fakes = fakes;
        this.ranked = StatsMenu.rankedModes(settings);
    }

    void setSkins(Function<UUID, Skin> skins) {
        this.skins = skins;
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
            spots.add(new Spot(bottom, stats, key));
            fakes.add(new FakeHologram(bottom, BOARD_LINES, viewer -> board(stats, key, viewer, System.currentTimeMillis()),
                    (player, left) -> {
                        if (!clickAimed(player, left) && cooled(player)) {
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

    /** What a player reads on a board now: the stat, its period or ranked mode, and the ranking. */
    private static final class View {
        final BoardStat stat;
        final StatsPeriod period;
        /** The ranked mode of an ELO board, or null. */
        final GameMode mode;
        /** Null for an ELO board without any ranked mode. */
        final StatsBook.Ranking ranking;

        View(BoardStat stat, StatsPeriod period, GameMode mode, StatsBook.Ranking ranking) {
            this.stat = stat;
            this.period = period;
            this.mode = mode;
            this.ranking = ranking;
        }
    }

    private View view(List<BoardStat> stats, String key, UUID viewer, long now) {
        Choice choice = choice(viewer, key);
        BoardStat stat = stats.get(choice.stat % stats.size());
        StatsPeriod period = StatsPeriod.values()[choice.option % StatsPeriod.values().length];
        GameMode mode = stat.choice() != BoardStat.Choice.RANKED_MODE || ranked.isEmpty() ? null
                : ranked.get(choice.option % ranked.size());
        StatsBook.Ranking ranking = stat.choice() != BoardStat.Choice.RANKED_MODE || mode != null
                ? book.ranking(stat, period, mode == null ? null : mode.id(), now) : null;
        return new View(stat, period, mode, ranking);
    }

    /** The lines of a board for this player. */
    List<String> board(List<BoardStat> stats, String key, Player viewer, long now) {
        View view = view(stats, key, viewer.getUniqueId(), now);
        BoardStat stat = view.stat;
        StatsPeriod period = view.period;
        GameMode mode = view.mode;
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
        StatsBook.Ranking ranking = view.ranking;
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
            lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "\u00bb " + ChatColor.GOLD + "Click here: next leaderboard");
        }
        if (stat.choice() == BoardStat.Choice.PERIOD) {
            lines.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "\u00bb " + ChatColor.YELLOW + "Click here: change the period");
        } else if (stat.choice() == BoardStat.Choice.RANKED_MODE) {
            lines.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "\u00bb " + ChatColor.YELLOW + "Click here: change the mode");
        }
        return lines;
    }

    // --- Podiums ---------------------------------------------------------------------------------

    /**
     * The podium of a wall: on each step, the player at that place of what this viewer reads on the
     * wall's first board, in their skin, with a line above saying who they are and their score. A
     * click on one opens their stats.
     */
    void addPodium(LobbyNpc wall, List<Location> steps) {
        final List<BoardStat> stats = wall.boards().get(0);
        final String key = wall.point() + "#0";
        for (int i = 0; i < steps.size(); i++) {
            final int place = i;
            final PodiumPlace.Holder holder = viewer -> holder(stats, key, viewer.getUniqueId(), place, System.currentTimeMillis());
            FakeEntities.Clicker open = (player, left) -> {
                StatsBook.Row row = holder.of(player);
                if (row != null && cooled(player)) {
                    menu.open(player, row.id(), row.name());
                }
            };
            Location step = steps.get(i);
            fakes.add(new PodiumPlace(step, LobbyNpcs.podiumName(i), holder, player -> skins.apply(player), open));
            fakes.add(new FakeHologram(step.clone().add(0, LobbyNpcs.ABOVE_HEAD, 0), 1,
                    viewer -> Collections.singletonList(podiumLine(stats, key, viewer.getUniqueId(), place,
                            System.currentTimeMillis())), open));
        }
    }

    /** The player at this place (0 for the first) of the board for this viewer, or null. */
    StatsBook.Row holder(List<BoardStat> stats, String key, UUID viewer, int place, long now) {
        View view = view(stats, key, viewer, now);
        return view.ranking == null || place >= view.ranking.rows().size() ? null : view.ranking.rows().get(place);
    }

    /** "#1 Sofiene - Gold 245": the line above a step of the podium, for this viewer. */
    String podiumLine(List<BoardStat> stats, String key, UUID viewer, int place, long now) {
        View view = view(stats, key, viewer, now);
        String rank = "#" + (place + 1);
        if (view.ranking == null || place >= view.ranking.rows().size()) {
            return ChatColor.DARK_GRAY + rank + " " + ChatColor.GRAY + "Nobody yet";
        }
        StatsBook.Row row = view.ranking.rows().get(place);
        String score;
        if (view.stat == BoardStat.ELO) {
            score = book.ladder().format(row.value());
        } else if (view.stat == BoardStat.LEVEL) {
            int level = book.levels().level(row.value());
            score = ChatColor.GRAY + "Level " + book.levels().colour(level) + level;
        } else {
            score = ChatColor.YELLOW + String.valueOf(row.value()) + ChatColor.GRAY + " "
                    + view.stat.title().toLowerCase(java.util.Locale.ROOT);
        }
        return PODIUM_COLOURS[Math.min(place, PODIUM_COLOURS.length - 1)].toString() + ChatColor.BOLD + rank + " " + ChatColor.WHITE
                + row.name() + ChatColor.DARK_GRAY + " - " + score;
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
        next(player.getUniqueId(), stats, key, left);
    }

    void next(UUID player, List<BoardStat> stats, String key, boolean left) {
        Choice choice = choice(player, key);
        boolean nothingToChange = stats.get(choice.stat % stats.size()).choice() == BoardStat.Choice.NONE;
        if (stats.size() > 1 && (!left || nothingToChange)) {
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

    /**
     * A click on line {@code line} (0 for the title) of a board: its line "next leaderboard" goes to
     * the next stat, its line "change the period" (or mode) changes it, and elsewhere the button
     * decides as in {@link #next}.
     */
    void clickLine(UUID player, List<BoardStat> stats, String key, int line, boolean left) {
        int next = stats.size() > 1 ? FIRST_CLICK_LINE : -1;
        int change = stats.size() > 1 ? FIRST_CLICK_LINE + 1 : FIRST_CLICK_LINE;
        if (line == next) {
            next(player, stats, key, false);
        } else if (line == change || stats.size() == 1) {
            next(player, stats, key, true);
        } else {
            next(player, stats, key, left);
        }
    }

    /**
     * A click of this player, if they aim at a board within {@link #AIM_REACH} blocks: true if it
     * went to one (the nearest one aimed at), even when it came too soon after the last.
     */
    boolean clickAimed(Player player, boolean left) {
        if (spots.isEmpty()) {
            return false;
        }
        Location eye = player.getEyeLocation();
        Vector way = eye.getDirection();
        Spot aimed = null;
        double nearest = Double.MAX_VALUE;
        double height = 0;
        for (Spot spot : spots) {
            if (!spot.world.equals(eye.getWorld())) {
                continue;
            }
            double low = spot.bottom - FakeHologram.LINE / 2;
            double high = spot.bottom + (BOARD_LINES - 1) * FakeHologram.LINE + FakeHologram.LINE / 2;
            double distance = aim(eye.getX(), eye.getY(), eye.getZ(), way.getX(), way.getY(), way.getZ(),
                    spot.x, spot.z, low, high, AIM_HALF_WIDTH, AIM_REACH);
            if (distance >= 0 && distance < nearest) {
                nearest = distance;
                aimed = spot;
                height = eye.getY() + way.getY() * distance - spot.bottom;
            }
        }
        if (aimed == null) {
            return false;
        }
        if (cooled(player)) {
            clickLine(player.getUniqueId(), aimed.stats, aimed.key, lineAt(height), left);
        }
        return true;
    }

    /** The line of a board at this height above the name of its last line (0 for the title). */
    static int lineAt(double height) {
        return BOARD_LINES - 1 - (int) Math.round(height / FakeHologram.LINE);
    }

    /**
     * How far along the look (eye, unit way) it meets the text of a board, or -1 when it misses it.
     * The text of floating names always faces the one who reads it: the board is a rectangle
     * standing on the vertical line through (x, z), turned to the eye, halfWidth to each side of
     * the line, from low to high.
     */
    static double aim(double eyeX, double eyeY, double eyeZ, double wayX, double wayY, double wayZ,
            double x, double z, double low, double high, double halfWidth, double reach) {
        double toEyeX = eyeX - x;
        double toEyeZ = eyeZ - z;
        double away = Math.sqrt(toEyeX * toEyeX + toEyeZ * toEyeZ);
        if (away < 0.5) {
            return -1;
        }
        double normalX = toEyeX / away;
        double normalZ = toEyeZ / away;
        double towards = wayX * normalX + wayZ * normalZ;
        if (towards > -1e-6) {
            return -1;
        }
        double distance = away / -towards;
        if (distance > reach) {
            return -1;
        }
        double atX = toEyeX + wayX * distance;
        double atZ = toEyeZ + wayZ * distance;
        double aside = Math.abs(atX * -normalZ + atZ * normalX);
        double y = eyeY + wayY * distance;
        return aside <= halfWidth && y >= low && y <= high ? distance : -1;
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
