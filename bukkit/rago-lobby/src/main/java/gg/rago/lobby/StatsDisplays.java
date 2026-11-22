package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
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
 * <li>a stats NPC: a body in its skin, with the player's profile floating above its head
 * (Network level and rank, wins, losses, kills, best streak, wins of the day, ELO of each ranked
 * mode); a click opens {@link StatsMenu};
 * <li>a leaderboard wall: boards side by side, each with its top 10, the player's own place and
 * when it starts again. A click on a board changes its period (day, week, ever) for that player,
 * or its mode for an ELO board. A board with several stats goes to the next one on a click
 * (either button) anywhere but its line "Click here: change the period", which changes the period
 * or the mode. A click goes to a board from as far as {@link #AIM_REACH} blocks, anywhere on its
 * text: the player's look is followed to it (the armor stands of its lines are too thin to aim at,
 * and an entity is only clicked from 3 blocks). A right click with an item of the lobby in the
 * hand goes to the board aimed at instead of the item; with an empty hand, the game sends nothing
 * further than 3 blocks. A wall can fix the period of its boards (periode: semaine, the wall of
 * the week): a click then only goes to the next stat. A board of the week whose top wins
 * something on Monday ({@link WeeklyRewards}) says what, under the player's place.
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
    static final double BOARD_BOTTOM = 1.2;
    private static final int PROFILE_LINES = 11;
    private static final long CLICK_COOLDOWN_MS = 300;
    /** How far a board is clicked from, in blocks: as far as its text is easily read. */
    static final double AIM_REACH = 24;
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
    /** The boards whose period is fixed by their wall, by key. */
    private final Map<String, StatsPeriod> fixed = new HashMap<>();
    /**
     * The boards that follow a leaderboard selector, by key: their wall. They share one choice, the
     * stat chosen at the selector ({@link #choiceKey}), each on the period of its wall.
     */
    private final Map<String, LobbyNpc> followers = new HashMap<>();
    /** What the top of the leaderboards of the week wins: nothing until {@link #setWeekly}. */
    private WeeklyRewards.Settings weekly = WeeklyRewards.Settings.OFF;
    private String weeklyBadge;
    /** The skins of the podiums: none until {@link #setSkins}. */
    private Function<UUID, Skin> skins = player -> null;
    /** How a podium writes a player: in white, without rank, until {@link #setNames}. */
    private BiFunction<UUID, String, String> names = (player, name) -> ChatColor.WHITE + name;

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

    /** What the top of the leaderboards of the week wins, and the badge of the week (its text, or null). */
    void setWeekly(WeeklyRewards.Settings weekly, String badgeText) {
        this.weekly = weekly;
        this.weeklyBadge = badgeText;
    }

    /** How a podium writes a player: their rank and their name in its colour ({@link NameLooks#stillDisplay}). */
    void setNames(BiFunction<UUID, String, String> names) {
        this.names = names;
    }

    // --- Stats NPC ------------------------------------------------------------------------------

    /** A body on the point (in its skin, Steve without one), the player's profile above it. */
    void addStatsNpc(final LobbyNpc npc, String bodyName, Skin skin, Location base) {
        FakeEntities.Clicker open = (player, left) -> {
            if (cooled(player)) {
                menu.open(player);
            }
        };
        fakes.add(new FakePlayer(base, bodyName, skin, open));
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
        int wins = stats == null ? 0 : stats.wins(StatsPeriod.ALL, null, day, week);
        int losses = stats == null ? 0 : stats.losses();
        int finals = stats == null ? 0 : stats.finalKills();
        String dot = ChatColor.DARK_GRAY + " · ";
        lines.add(ChatColor.GRAY + "Games played: " + ChatColor.WHITE + (stats == null ? 0 : stats.games()));
        lines.add(ChatColor.GRAY + "Wins: " + ChatColor.GREEN + wins + dot + ChatColor.GRAY + "Losses: " + ChatColor.RED + losses);
        lines.add(ChatColor.GRAY + "WLR: " + ChatColor.GOLD + StatsMenu.ratio(wins, losses));
        lines.add(ChatColor.GRAY + "Kills: " + ChatColor.GREEN + (stats == null ? 0 : stats.kills(StatsPeriod.ALL, null, day, week))
                + dot + ChatColor.GRAY + "Final kills: " + ChatColor.GREEN + finals);
        lines.add(ChatColor.GRAY + "FKDR: " + ChatColor.GOLD + StatsMenu.fkdr(finals, stats == null ? 0 : stats.finalDeaths()));
        lines.add(ChatColor.GRAY + "Best win streak: " + Brand.ACCENT + (stats == null ? 0 : stats.best(StatsPeriod.ALL, null, day, week))
                + dot + ChatColor.GRAY + "Wins today: " + ChatColor.GREEN
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
        lines.add(Brand.ACCENT + "" + ChatColor.BOLD + "CLICK FOR YOUR STATS");
        return lines;
    }

    // --- Leaderboards ---------------------------------------------------------------------------

    /** The walls are taken down (before they are placed again): their places are forgotten. */
    void clearWalls() {
        spots.clear();
        fixed.clear();
        followers.clear();
    }

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
            if (wall.period() != null) {
                fixed.put(key, wall.period());
            }
            if (wall.selector() != null) {
                followers.put(key, wall);
            }
            double offset = (i - (boards.size() - 1) / 2.0) * BOARD_GAP;
            Location bottom = base.clone().add(rightX * offset, BOARD_BOTTOM, rightZ * offset);
            spots.add(new Spot(bottom, stats, key));
            fakes.add(new FakeHologram(bottom, BOARD_LINES, viewer -> board(stats, key, viewer, System.currentTimeMillis()),
                    (player, left) -> {
                        if (!clickAimed(player, left) && cooled(player)) {
                            next(player, stats, key, stats.size() == 1);
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
        /**
         * The wall of a board of the selector (every stat on its period), or null: a stat that has no
         * period (level, FKDR, WLR, ELO) is then its lifetime one.
         */
        final LobbyNpc follows;

        View(BoardStat stat, StatsPeriod period, GameMode mode, StatsBook.Ranking ranking, LobbyNpc follows) {
            this.stat = stat;
            this.period = period;
            this.mode = mode;
            this.ranking = ranking;
            this.follows = follows;
        }

        /** A stat without a period of its own shown over a day or a week: the XP earned, the ELO won, a ratio of the period. */
        boolean overPeriod() {
            return follows != null && period != StatsPeriod.ALL && !stat.periodic();
        }
    }

    private View view(List<BoardStat> stats, String key, UUID viewer, long now) {
        Choice choice = choice(viewer, key);
        BoardStat stat = stats.get(choice.stat % stats.size());
        StatsPeriod period = fixed.containsKey(key) ? fixed.get(key)
                : StatsPeriod.values()[choice.option % StatsPeriod.values().length];
        GameMode mode = stat.choice() != BoardStat.Choice.RANKED_MODE || ranked.isEmpty() ? null
                : ranked.get(choice.option % ranked.size());
        LobbyNpc follows = followers.get(key);
        StatsBook.Ranking ranking = null;
        if (stat.choice() != BoardStat.Choice.RANKED_MODE || mode != null) {
            String modeId = mode == null ? null : mode.id();
            ranking = follows != null ? book.ranking(stat, period, modeId, now, follows.minimumGames(period))
                    : book.ranking(stat, period, modeId, now);
        }
        return new View(stat, period, mode, ranking, follows);
    }

    /** The lines of a board for this player. */
    List<String> board(List<BoardStat> stats, String key, Player viewer, long now) {
        View view = view(stats, key, viewer.getUniqueId(), now);
        BoardStat stat = view.stat;
        StatsPeriod period = view.period;
        GameMode mode = view.mode;
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.AQUA + "" + ChatColor.BOLD + stat.title());
        // the period of the board: its own, or ever for a stat without one, but on a wall of the selector
        StatsPeriod shownPeriod = stat.periodic() || view.follows != null ? period : StatsPeriod.ALL;
        switch (stat.choice()) {
            case PERIOD:
                lines.add(ChatColor.GRAY + "All modes " + ChatColor.DARK_GRAY + "· " + ChatColor.WHITE + period.label());
                break;
            case RANKED_MODE:
                lines.add(ChatColor.GRAY + (mode == null ? "No ranked mode" : ChatColor.stripColor(mode.name()))
                        + (view.follows == null ? "" : " " + ChatColor.DARK_GRAY + "· " + ChatColor.WHITE + period.label()));
                break;
            default:
                lines.add(ChatColor.GRAY + "All modes " + ChatColor.DARK_GRAY + "· " + ChatColor.WHITE + shownPeriod.label());
                break;
        }
        lines.add("");
        StatsBook.Ranking ranking = view.ranking;
        List<StatsBook.Row> rows = ranking == null ? new ArrayList<StatsBook.Row>() : ranking.rows();
        for (int i = 0; i < TOP; i++) {
            if (i < rows.size()) {
                StatsBook.Row row = rows.get(i);
                lines.add(Brand.ACCENT + "" + (i + 1) + ". " + ChatColor.WHITE + row.name() + ChatColor.GRAY + " - "
                        + shown(view, row.value()));
            } else if (i == 0) {
                lines.add(ChatColor.GRAY + "Nobody yet");
            } else {
                lines.add("");
            }
        }
        lines.add("");
        StatsBook.Row own = ranking == null ? null : ranking.rowOf(viewer.getUniqueId());
        lines.add(own == null ? ChatColor.GRAY + "You: " + ChatColor.DARK_GRAY + "not ranked yet"
                : ChatColor.GRAY + "You: " + Brand.ACCENT + "#" + ranking.position(viewer.getUniqueId())
                        + ChatColor.GRAY + " - " + shown(view, own.value()));
        // the board of the week: what its top wins on Monday; a ratio of a day or a week: the games it needs
        int minimum = view.follows == null ? 0 : view.follows.minimumGames(period);
        if (stat.periodic() && period == StatsPeriod.WEEK && weekly.rewards(stat)) {
            lines.add(WeeklyRewards.boardLine(weekly, weeklyBadge));
        } else if (view.overPeriod() && minimum > 0 && (stat == BoardStat.FKDR || stat == BoardStat.WLR)) {
            lines.add(ChatColor.GRAY + "Ranked from " + minimum + (stat == BoardStat.FKDR ? " Bed Wars" : "") + " games "
                    + (period == StatsPeriod.DAY ? "today" : "this week"));
        } else {
            lines.add("");
        }
        switch (stat.choice()) {
            case PERIOD:
                lines.add(ChatColor.GRAY + period.resetText(now));
                break;
            case RANKED_MODE:
                lines.add(ChatColor.GRAY + (view.overPeriod() ? period.resetText(now) : "Season Elo"));
                break;
            default:
                lines.add(ChatColor.GRAY + shownPeriod.resetText(now));
                break;
        }
        if (stats.size() > 1) {
            lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "\u00bb " + ChatColor.GOLD + "Click here: next leaderboard");
        }
        if (stat.choice() == BoardStat.Choice.PERIOD && !fixed.containsKey(key)) {
            lines.add(Brand.ACCENT + "" + ChatColor.BOLD + "\u00bb " + Brand.ACCENT + "Click here: change the period");
        } else if (stat.choice() == BoardStat.Choice.RANKED_MODE) {
            lines.add(Brand.ACCENT + "" + ChatColor.BOLD + "\u00bb " + Brand.ACCENT + "Click here: change the mode");
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
            fakes.add(new FakeHologram(step.clone().add(0, LobbyNpcs.ABOVE_HEAD, 0), 2,
                    viewer -> podiumLines(stats, key, viewer.getUniqueId(), place, System.currentTimeMillis()), open));
        }
    }

    /** The player at this place (0 for the first) of the board for this viewer, or null. */
    StatsBook.Row holder(List<BoardStat> stats, String key, UUID viewer, int place, long now) {
        View view = view(stats, key, viewer, now);
        return view.ranking == null || place >= view.ranking.rows().size() ? null : view.ranking.rows().get(place);
    }

    /**
     * "#1 [PREMIUM+] Sofiene" then "Gold 245": the lines above a step of the podium, for this viewer
     * (the place, the rank and the name of the player, then their score). Two lines, since the
     * name of a 1.8 entity holds 64 characters at most.
     */
    List<String> podiumLines(List<BoardStat> stats, String key, UUID viewer, int place, long now) {
        View view = view(stats, key, viewer, now);
        String rank = "#" + (place + 1);
        if (view.ranking == null || place >= view.ranking.rows().size()) {
            return Collections.singletonList(ChatColor.DARK_GRAY + rank + " " + ChatColor.GRAY + "Nobody yet");
        }
        StatsBook.Row row = view.ranking.rows().get(place);
        String score;
        if (view.overPeriod() && (view.stat == BoardStat.ELO || view.stat == BoardStat.LEVEL)) {
            score = shown(view, row.value()) + ChatColor.GRAY + (view.stat == BoardStat.ELO ? " Elo" : "");
        } else if (view.stat == BoardStat.WLR) {
            score = Brand.ACCENT + BoardStat.hundredths(row.value()) + ChatColor.GRAY + " WLR";
        } else if (view.stat == BoardStat.ELO) {
            score = book.ladder().format(row.value());
        } else if (view.stat == BoardStat.LEVEL) {
            int level = book.levels().level(row.value());
            score = ChatColor.GRAY + "Level " + book.levels().paint(String.valueOf(level), level);
        } else if (view.stat == BoardStat.FKDR) {
            score = Brand.ACCENT + BoardStat.hundredths(row.value()) + ChatColor.GRAY + " FKDR";
        } else {
            score = Brand.ACCENT + String.valueOf(row.value()) + ChatColor.GRAY + " "
                    + view.stat.title().toLowerCase(java.util.Locale.ROOT);
        }
        return java.util.Arrays.asList(PODIUM_COLOURS[Math.min(place, PODIUM_COLOURS.length - 1)].toString() + ChatColor.BOLD + rank
                + " " + names.apply(row.id(), row.name()), score);
    }

    /**
     * The value a board shows: the level for the Network level (ranked by XP), the division with
     * the ELO ("Gold 245"), the ratio for the FKDR and the WLR ("2.50"), the number otherwise. Over
     * a day or a week on a wall of the selector: the XP earned ("1,250 XP"), the ELO won ("+25",
     * "-10").
     */
    private String shown(View view, int value) {
        BoardStat stat = view.stat;
        if (view.overPeriod() && stat == BoardStat.LEVEL) {
            return Brand.ACCENT + String.format(java.util.Locale.US, "%,d", value) + ChatColor.GRAY + " XP";
        }
        if (view.overPeriod() && stat == BoardStat.ELO) {
            return (value > 0 ? ChatColor.GREEN + "+" : value < 0 ? ChatColor.RED + "" : ChatColor.GRAY + "") + value;
        }
        if (stat == BoardStat.WLR) {
            return Brand.ACCENT + BoardStat.hundredths(value);
        }
        if (stat == BoardStat.LEVEL) {
            return book.levels().paint(String.valueOf(book.levels().level(value)), book.levels().level(value));
        }
        if (stat == BoardStat.ELO) {
            return book.ladder().format(value);
        }
        if (stat == BoardStat.FKDR) {
            return Brand.ACCENT + BoardStat.hundredths(value);
        }
        return Brand.ACCENT + String.valueOf(value);
    }

    /** The choice a player made on this board: its own, or the one of the selector it follows. */
    private Choice choice(UUID player, String key) {
        return choiceAt(player, choiceKey(key));
    }

    private Choice choiceAt(UUID player, String key) {
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

    /** The choice a board reads: its own, or the one of the selector it follows, shared by its walls. */
    private String choiceKey(String key) {
        LobbyNpc follows = followers.get(key);
        return follows == null ? key : selectorKey(follows.selector());
    }

    private static String selectorKey(String selector) {
        return "selector:" + selector;
    }

    // --- Leaderboard selector -----------------------------------------------------------------------

    /**
     * The NPC of the leaderboard selector (plan 3.1): the menu of its stats, one item for each, and
     * for the ELO one for each ranked mode. The stat clicked shows on the walls that follow it, on
     * the period of each (Daily, Weekly, Lifetime), for the one who clicked.
     */
    void openSelector(Player player, LobbyNpc selector) {
        List<BoardStat> stats = selector.boards().get(0);
        List<int[]> items = selectorItems(stats, ranked.size());
        ClickMenu chest = new ClickMenu(Math.max(1, (items.size() + 8) / 9), "Leaderboards");
        Choice now = choiceAt(player.getUniqueId(), selectorKey(selector.point()));
        for (int i = 0; i < items.size(); i++) {
            final int stat = items.get(i)[0];
            final int option = items.get(i)[1];
            BoardStat shown = stats.get(stat);
            GameMode mode = shown.choice() == BoardStat.Choice.RANKED_MODE && !ranked.isEmpty() ? ranked.get(option) : null;
            String name = selectorName(shown, mode);
            List<String> lore = new ArrayList<>(selectorLore(shown, selector));
            boolean chosen = now.stat % stats.size() == stat && (mode == null || now.option % ranked.size() == option);
            lore.add("");
            lore.add(chosen ? ChatColor.GREEN + "Shown now" : ChatColor.YELLOW + "Click to show it");
            ItemStack item = ClickMenu.item(selectorIcon(shown), 0, ChatColor.AQUA + "" + ChatColor.BOLD + name, lore);
            chest.set(i, chosen ? ClickMenu.glow(item) : item, (clicker, right) -> {
                select(clicker.getUniqueId(), selector.point(), stat, option);
                clicker.closeInventory();
                clicker.sendMessage(ChatColor.GREEN + "Leaderboards: " + ChatColor.WHITE + name);
            });
        }
        chest.open(player);
    }

    /**
     * The items of the menu of a selector, in order: {the stat's index, its option}; the option is
     * the ranked mode of an ELO (one item for each of them), 0 otherwise.
     */
    static List<int[]> selectorItems(List<BoardStat> stats, int rankedModes) {
        List<int[]> items = new ArrayList<>();
        for (int i = 0; i < stats.size(); i++) {
            if (stats.get(i).choice() == BoardStat.Choice.RANKED_MODE) {
                for (int mode = 0; mode < rankedModes; mode++) {
                    items.add(new int[] {i, mode});
                }
            } else {
                items.add(new int[] {i, 0});
            }
        }
        return items;
    }

    /** The stat this player chose at this selector, and its ranked mode: every wall that follows it shows it. */
    void select(UUID player, String selector, int stat, int option) {
        Choice choice = choiceAt(player, selectorKey(selector));
        choice.stat = stat;
        choice.option = option;
    }

    /** "Kills", "Ranked Elo: Ranked 1v1". */
    private static String selectorName(BoardStat stat, GameMode mode) {
        return mode == null ? stat.title() : stat.title() + ": " + ChatColor.stripColor(mode.name());
    }

    /** What the three periods of a stat rank, for its item in the menu of the selector. */
    static List<String> selectorLore(BoardStat stat, LobbyNpc selector) {
        List<String> lore = new ArrayList<>();
        switch (stat) {
            case LEVEL:
                lore.add(ChatColor.GRAY + "Daily, Weekly: the XP earned");
                lore.add(ChatColor.GRAY + "Lifetime: the Network level");
                break;
            case ELO:
                lore.add(ChatColor.GRAY + "Daily, Weekly: the Elo won");
                lore.add(ChatColor.GRAY + "Lifetime: the Elo of the season");
                break;
            case FKDR:
            case WLR:
                lore.add(ChatColor.GRAY + "Daily, Weekly, Lifetime");
                int day = selector.minimumGames(StatsPeriod.DAY);
                int week = selector.minimumGames(StatsPeriod.WEEK);
                if (day > 0 || week > 0) {
                    lore.add(ChatColor.GRAY + "Ranked from " + day + " games a day, " + week + " a week");
                }
                break;
            default:
                lore.add(ChatColor.GRAY + "Daily, Weekly, Lifetime");
                break;
        }
        return lore;
    }

    private static Material selectorIcon(BoardStat stat) {
        switch (stat) {
            case WINS:
                return Material.GOLD_INGOT;
            case GAMES:
                return Material.PAPER;
            case STREAK:
                return Material.BLAZE_POWDER;
            case KILLS:
                return Material.IRON_SWORD;
            case FKDR:
                return Material.BED;
            case WLR:
                return Material.GOLD_NUGGET;
            case LEVEL:
                return Material.EXP_BOTTLE;
            default:
                return Material.DIAMOND;
        }
    }

    /**
     * A click on a board. With one stat, any click changes its period or mode. With several,
     * {@code change} false goes to the next stat (back to its first period or mode), true changes
     * the period or the mode (or goes to the next stat when it has none).
     */
    void next(Player player, List<BoardStat> stats, String key, boolean change) {
        next(player.getUniqueId(), stats, key, change);
    }

    void next(UUID player, List<BoardStat> stats, String key, boolean change) {
        Choice choice = choice(player, key);
        BoardStat now = stats.get(choice.stat % stats.size());
        boolean nothingToChange = now.choice() == BoardStat.Choice.NONE
                || now.choice() == BoardStat.Choice.PERIOD && fixed.containsKey(key);
        if (stats.size() > 1 && (!change || nothingToChange)) {
            choice.stat = (choice.stat + 1) % stats.size();
            choice.option = 0;
            return;
        }
        BoardStat stat = stats.get(choice.stat % stats.size());
        int count;
        switch (stat.choice()) {
            case PERIOD:
                count = fixed.containsKey(key) ? 1 : StatsPeriod.values().length;
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
     * A click on line {@code line} (0 for the title) of a board, whichever the button: its line
     * "change the period" (or mode) changes it; anywhere else, a board of several leaderboards goes
     * to the next one, and a board of one changes its period or mode.
     */
    void clickLine(UUID player, List<BoardStat> stats, String key, int line, boolean left) {
        int change = stats.size() > 1 ? FIRST_CLICK_LINE + 1 : FIRST_CLICK_LINE;
        next(player, stats, key, line == change || stats.size() == 1);
    }

    /**
     * A click of this player, if they aim at a board within {@link #AIM_REACH} blocks: true if it
     * went to one (the nearest one aimed at), even when it came too soon after the last.
     */
    boolean clickAimed(Player player, boolean left) {
        Aim aim = aimOf(player);
        if (aim == null) {
            return false;
        }
        if (cooled(player)) {
            clickLine(player.getUniqueId(), aim.spot.stats, aim.spot.key, lineAt(aim.height), left);
        }
        return true;
    }

    /** True if this player aims at a board within {@link #AIM_REACH} blocks: a left click goes to it. */
    boolean aimed(Player player) {
        return aimOf(player) != null;
    }

    /** The board a player aims at, and how high on it. */
    private static final class Aim {
        final Spot spot;
        final double height;

        Aim(Spot spot, double height) {
            this.spot = spot;
            this.height = height;
        }
    }

    /** The nearest board this player aims at, or null. */
    private Aim aimOf(Player player) {
        if (spots.isEmpty()) {
            return null;
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
        return aimed == null ? null : new Aim(aimed, height);
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
