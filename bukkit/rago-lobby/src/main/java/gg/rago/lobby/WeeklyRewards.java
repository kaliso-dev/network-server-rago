package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The leaderboards of the week, rewarded. When the week turns (Monday at midnight, in the time of
 * Paris), the top 3 of each leaderboard of the week win Rago Points and the badge of the week,
 * worn until the next results, and the whole server hears it. The leaderboards are the lobby's
 * own (the wins, the best win streaks, the kills and the games of the week, every mode together:
 * semaine.classements in config.yml) and those the other plugins add (the biggest clutches of
 * the week, in the practice). The week counted, the last results, the winners still to tell and
 * the badge given are kept in semaine.yml ({@link State}).
 *
 * <p>A game of the new week starts the counters of the week again: the week before must be
 * rewarded first. So the plugin checks the week once the stats are read, every 20 seconds, and
 * before it counts each game and kill. Pure Java, main thread.
 */
final class WeeklyRewards {

    static final String FILE_NAME = "semaine.yml";
    /** How long after the results a player who arrives reads them: the day of the results. */
    static final long NEWS_MS = 24L * 3600_000L;
    /** A line across the chat, before and after the results. */
    static final String RULE = ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "-----------------------------------------------------";
    /** The colour of the first, the second, the third, then the others. */
    private static final ChatColor[] PLACES = {ChatColor.GOLD, ChatColor.WHITE, ChatColor.RED, ChatColor.GRAY};

    /** semaine: in config.yml. */
    static final class Settings {
        /** The lobby's leaderboards of the week rewarded without the setting. */
        static final List<BoardStat> DEFAULT_BOARDS = Collections.unmodifiableList(Arrays.asList(BoardStat.WINS, BoardStat.STREAK,
                BoardStat.KILLS, BoardStat.GAMES));
        /** The Rago Points of the first, the second and the third without the setting. */
        static final List<Integer> DEFAULT_POINTS = Collections.unmodifiableList(Arrays.asList(2000, 1000, 500));
        /** The badge of the plugin's own styles: the crown of the week. */
        static final String DEFAULT_BADGE = "semaine";
        /** At most this many places of each leaderboard are rewarded. */
        static final int MAX_PLACES = 10;
        /** The highest reward of a place, in Rago Points. */
        static final int MAX_POINTS = 1_000_000;
        /** Nothing rewarded. */
        static final Settings OFF = new Settings(false, Collections.<BoardStat>emptyList(), Collections.<Integer>emptyList(), null);

        final boolean on;
        /** The lobby's leaderboards of the week rewarded (wins, games, streaks, kills). */
        final List<BoardStat> boards;
        /** The Rago Points of the first, the second...: as many places as points. */
        final List<Integer> points;
        /** The id of the badge of the week (styles.badges), or null for none. */
        final String badge;

        Settings(boolean on, List<BoardStat> boards, List<Integer> points, String badge) {
            this.on = on;
            this.boards = Collections.unmodifiableList(new ArrayList<>(boards));
            this.points = Collections.unmodifiableList(new ArrayList<>(points));
            this.badge = badge;
        }

        /** The places rewarded on each leaderboard. */
        int places() {
            return points.size();
        }

        /** The Rago Points of this place, from 1; 0 off the podium. */
        int points(int place) {
            return place >= 1 && place <= points.size() ? points.get(place - 1) : 0;
        }

        /** True if the top of this leaderboard of the week wins something. */
        boolean rewards(BoardStat stat) {
            return on && boards.contains(stat) && (badge != null || total() > 0);
        }

        private int total() {
            int total = 0;
            for (int value : points) {
                total += value;
            }
            return total;
        }
    }

    /** A player on a podium: their place (from 1), who they are, and the score shown. */
    static final class Place {
        final int place;
        final UUID player;
        final String name;
        final String score;

        Place(int place, UUID player, String name, String score) {
            this.place = place;
            this.player = player;
            this.name = name;
            this.score = score;
        }
    }

    /** The podium of one leaderboard of the week: its name ("Wins") and its first places. */
    static final class Podium {
        final String board;
        final List<Place> places;

        Podium(String board, List<Place> places) {
            this.board = board;
            this.places = Collections.unmodifiableList(new ArrayList<>(places));
        }
    }

    /** The results of one week ("2026-W46"), given at this time. */
    static final class Results {
        final String week;
        final long at;
        final List<Podium> podiums;

        Results(String week, long at, List<Podium> podiums) {
            this.week = week;
            this.at = at;
            this.podiums = Collections.unmodifiableList(new ArrayList<>(podiums));
        }

        /** Everyone on a podium. */
        Set<UUID> winners() {
            Set<UUID> winners = new LinkedHashSet<>();
            for (Podium podium : podiums) {
                for (Place place : podium.places) {
                    winners.add(place.player);
                }
            }
            return winners;
        }
    }

    /** A leaderboard of the week of another plugin. */
    interface Board {
        /** Its players for this week ("2026-W46"), best first, each with the score shown ("5.2 blocks"). */
        List<Map.Entry<UUID, String>> ranking(String week);
    }

    /** What semaine.yml keeps. */
    static final class State {
        /** The week counted now; null before the first check. */
        String counting;
        /** The last results; null before the first ones. */
        Results last;
        /** The winners of the last results who have not been told yet (they were away). */
        final Set<UUID> untold = new LinkedHashSet<>();
        /** The key of the badge the last results gave ("badge-semaine"), or null. */
        String badgeGiven;

        String toYaml() {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().header("The leaderboards of the week (written by RagoLobby): the week counted, the last results.");
            yaml.set("semaine", counting);
            if (badgeGiven != null) {
                yaml.set("badge", badgeGiven);
            }
            if (last != null) {
                yaml.set("resultats.semaine", last.week);
                yaml.set("resultats.quand", last.at);
                for (int i = 0; i < last.podiums.size(); i++) {
                    Podium podium = last.podiums.get(i);
                    String path = "resultats.classements." + i;
                    yaml.set(path + ".nom", podium.board);
                    List<String> places = new ArrayList<>();
                    for (Place place : podium.places) {
                        places.add(place.place + ";" + place.player + ";" + place.name + ";" + place.score);
                    }
                    yaml.set(path + ".places", places);
                }
                List<String> untoldList = new ArrayList<>();
                for (UUID id : untold) {
                    untoldList.add(id.toString());
                }
                yaml.set("resultats.a-dire", untoldList);
            }
            return yaml.saveToString();
        }

        static State fromYaml(String text) throws InvalidConfigurationException {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(text);
            State state = new State();
            state.counting = yaml.getString("semaine", null);
            state.badgeGiven = yaml.getString("badge", null);
            ConfigurationSection results = yaml.getConfigurationSection("resultats");
            if (results != null && results.getString("semaine") != null) {
                List<Podium> podiums = new ArrayList<>();
                ConfigurationSection boards = results.getConfigurationSection("classements");
                for (String key : boards == null ? Collections.<String>emptySet() : boards.getKeys(false)) {
                    ConfigurationSection board = boards.getConfigurationSection(key);
                    if (board == null) {
                        continue;
                    }
                    List<Place> places = new ArrayList<>();
                    for (String line : board.getStringList("places")) {
                        String[] parts = line.split(";", 4);
                        if (parts.length == 4) {
                            try {
                                places.add(new Place(Integer.parseInt(parts[0]), UUID.fromString(parts[1]), parts[2], parts[3]));
                            } catch (IllegalArgumentException e) {
                                // a line spoilt by hand: left out
                            }
                        }
                    }
                    podiums.add(new Podium(board.getString("nom", "?"), places));
                }
                state.last = new Results(results.getString("semaine"), results.getLong("quand"), podiums);
                for (String id : results.getStringList("a-dire")) {
                    try {
                        state.untold.add(UUID.fromString(id));
                    } catch (IllegalArgumentException e) {
                        // not a player: left out
                    }
                }
            }
            return state;
        }
    }

    private WeeklyRewards() {
    }

    // --- The week ------------------------------------------------------------------------------

    /**
     * The week that just ended, if the week turned since the last check (the counted week is then
     * the new one), else null. The first check only notes the week: the one before may already be
     * partly started again, so it is not rewarded.
     */
    static String turn(State state, String week) {
        if (state.counting == null) {
            state.counting = week;
            return null;
        }
        if (state.counting.equals(week)) {
            return null;
        }
        String ended = state.counting;
        state.counting = week;
        return ended;
    }

    /** "week 46", from "2026-W46". */
    static String weekName(String week) {
        int at = week.indexOf("-W");
        if (at < 0) {
            return week;
        }
        String number = week.substring(at + 2);
        while (number.length() > 1 && number.charAt(0) == '0') {
            number = number.substring(1);
        }
        return "week " + number;
    }

    // --- The leaderboards ----------------------------------------------------------------------

    /** A player's score on a lobby leaderboard of this week ("2026-W46"): 0 for a week they did not play. */
    static int value(PlayerStats stats, BoardStat stat, String week) {
        switch (stat) {
            case WINS:
                return stats.wins(StatsPeriod.WEEK, null, "", week);
            case GAMES:
                return stats.games(StatsPeriod.WEEK, null, "", week);
            case STREAK:
                return stats.best(StatsPeriod.WEEK, null, "", week);
            case KILLS:
                return stats.kills(StatsPeriod.WEEK, null, "", week);
            default:
                return 0;
        }
    }

    /**
     * The podium of a lobby leaderboard for this week: its first places, as the walls rank them
     * (the players at 0 left out, ties in the order of the names).
     */
    static Podium podium(Collection<PlayerStats> players, BoardStat stat, String week, int places) {
        List<StatsBook.Row> rows = new ArrayList<>();
        for (PlayerStats stats : players) {
            int value = value(stats, stat, week);
            if (value > 0) {
                rows.add(new StatsBook.Row(stats.id(), stats.name(), value));
            }
        }
        Collections.sort(rows, (a, b) -> a.value() != b.value() ? Integer.compare(b.value(), a.value())
                : a.name().compareToIgnoreCase(b.name()));
        List<Place> podium = new ArrayList<>();
        for (int i = 0; i < Math.min(places, rows.size()); i++) {
            StatsBook.Row row = rows.get(i);
            podium.add(new Place(i + 1, row.id(), row.name(), String.valueOf(row.value())));
        }
        return new Podium(stat.title(), podium);
    }

    /** The podium of another plugin's leaderboard: its first places, the names from the stats. */
    static Podium podium(String board, List<Map.Entry<UUID, String>> ranking, Function<UUID, String> names, int places) {
        List<Place> podium = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        for (Map.Entry<UUID, String> entry : ranking) {
            if (podium.size() >= places) {
                break;
            }
            if (entry.getKey() != null && seen.add(entry.getKey())) {
                String name = names.apply(entry.getKey());
                podium.add(new Place(podium.size() + 1, entry.getKey(), name == null ? "?" : name, entry.getValue()));
            }
        }
        return new Podium(board, podium);
    }

    /**
     * The results of a week: the podium of each lobby leaderboard of the settings, then of each
     * leaderboard of the other plugins, those with nobody on them left out.
     */
    static Results results(String week, long at, Settings settings, Collection<PlayerStats> players,
            Map<String, List<Map.Entry<UUID, String>>> others, Function<UUID, String> names) {
        List<Podium> podiums = new ArrayList<>();
        if (settings.on && settings.places() > 0) {
            for (BoardStat stat : settings.boards) {
                Podium podium = podium(players, stat, week, settings.places());
                if (!podium.places.isEmpty()) {
                    podiums.add(podium);
                }
            }
            for (Map.Entry<String, List<Map.Entry<UUID, String>>> other : others.entrySet()) {
                Podium podium = podium(other.getKey(), other.getValue(), names, settings.places());
                if (!podium.places.isEmpty()) {
                    podiums.add(podium);
                }
            }
        }
        return new Results(week, at, podiums);
    }

    /**
     * Gives the results: the Rago Points of each place (a player on two podiums gets both), and the
     * badge of the week to everyone on a podium, taken from all the others (with the badge the
     * results before gave, if it was another). Returns the profiles that changed, to write.
     */
    static Set<PlayerStats> give(Results results, Settings settings, StatsBook book, State state) {
        Set<PlayerStats> changed = new LinkedHashSet<>();
        String badge = settings.badge == null ? null : NameStyle.key(NameStyle.Kind.BADGE, settings.badge);
        Set<UUID> winners = results.winners();
        for (PlayerStats stats : new ArrayList<>(book.all())) {
            boolean winner = winners.contains(stats.id());
            if (state.badgeGiven != null && !state.badgeGiven.equals(badge) && stats.cosmetics().remove(state.badgeGiven)) {
                changed.add(stats);
            }
            if (badge != null && !winner && stats.cosmetics().remove(badge)) {
                changed.add(stats);
            }
        }
        for (Podium podium : results.podiums) {
            for (Place place : podium.places) {
                PlayerStats stats = book.profile(place.player, place.name);
                int points = settings.points(place.place);
                if (points > 0) {
                    stats.addPoints(points);
                    changed.add(stats);
                }
                if (badge != null && stats.cosmetics().add(badge)) {
                    changed.add(stats);
                }
            }
        }
        state.badgeGiven = badge;
        return changed;
    }

    // --- What the players read -----------------------------------------------------------------

    /** "2,000". */
    static String number(int value) {
        return String.format(Locale.US, "%,d", value);
    }

    /** "2,000, 1,000 and 500 Rago Points": what the places win, or null for no points. */
    static String pointsText(Settings settings) {
        List<String> amounts = new ArrayList<>();
        for (int value : settings.points) {
            amounts.add(ChatColor.GOLD + number(value) + ChatColor.GRAY);
        }
        if (settings.points.isEmpty() || settings.total() == 0) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < amounts.size(); i++) {
            text.append(i == 0 ? "" : i == amounts.size() - 1 ? " and " : ", ").append(amounts.get(i));
        }
        return text.append(ChatColor.GOLD).append(" Rago Points").append(ChatColor.GRAY).toString();
    }

    /** "The top 3 win 2,000, 1,000 and 500 Rago Points, and the badge ♛ for a week." */
    static String prizeLine(Settings settings, String badgeText) {
        String points = pointsText(settings);
        String badge = badgeText == null ? null : "the badge " + badgeText + ChatColor.GRAY + " for a week";
        String prize = points != null && badge != null ? points + ", and " + badge : points != null ? points : badge;
        return ChatColor.GRAY + "The top " + settings.places() + " of each win " + (prize == null ? "nothing" : prize) + ".";
    }

    /**
     * The line of a weekly board under the player's place: what the top wins on Monday, short
     * enough for a floating line ("Top 3 on Monday: 2,000 · 1,000 · 500 points + ♛").
     */
    static String boardLine(Settings settings, String badgeText) {
        StringBuilder line = new StringBuilder(ChatColor.GOLD + "Top " + settings.places() + " on Monday: " + ChatColor.YELLOW);
        if (settings.total() > 0 && settings.places() <= 3) {
            for (int i = 0; i < settings.places(); i++) {
                line.append(i == 0 ? "" : " · ").append(number(settings.points.get(i)));
            }
            line.append(" points");
        } else if (settings.total() > 0) {
            line.append("up to ").append(number(Collections.max(settings.points))).append(" points");
        }
        if (badgeText != null) {
            line.append(settings.total() > 0 ? " + " : "").append(badgeText);
        }
        return line.toString();
    }

    /** "#1 Alice (45)": one place of a podium, in its colour. */
    private static String place(Place place) {
        ChatColor colour = PLACES[Math.min(place.place, PLACES.length) - 1];
        return colour + "#" + place.place + " " + ChatColor.WHITE + place.name + ChatColor.GRAY + " (" + place.score + ")";
    }

    /** The lines of the results for everyone: each podium, then what the places won. */
    static List<String> announcement(Results results, Settings settings, String badgeText) {
        List<String> lines = new ArrayList<>();
        lines.add(RULE);
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "LEADERBOARDS OF THE WEEK " + ChatColor.GRAY + "- "
                + weekName(results.week));
        if (results.podiums.isEmpty()) {
            lines.add(ChatColor.GRAY + "Nobody played this week.");
        }
        for (Podium podium : results.podiums) {
            StringBuilder line = new StringBuilder(ChatColor.YELLOW + podium.board + ChatColor.DARK_GRAY + " » ");
            for (int i = 0; i < podium.places.size(); i++) {
                line.append(i == 0 ? "" : ChatColor.DARK_GRAY + " · ").append(place(podium.places.get(i)));
            }
            lines.add(line.toString());
        }
        if (!results.podiums.isEmpty()) {
            lines.add(prizeLine(settings, badgeText));
        }
        lines.add(RULE);
        return lines;
    }

    /** What this player won, for their own message: empty if they are on no podium. */
    static List<String> won(Results results, Settings settings, UUID player, String badgeText) {
        List<String> lines = new ArrayList<>();
        for (Podium podium : results.podiums) {
            for (Place place : podium.places) {
                if (!place.player.equals(player)) {
                    continue;
                }
                int points = settings.points(place.place);
                lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "TOP OF THE WEEK! " + ChatColor.GRAY + "You are "
                        + PLACES[Math.min(place.place, PLACES.length) - 1] + "#" + place.place + ChatColor.GRAY + " in "
                        + ChatColor.YELLOW + podium.board + ChatColor.GRAY + " (" + weekName(results.week) + ")"
                        + (points > 0 ? ": " + ChatColor.GOLD + "+" + number(points) + " Rago Points" : ""));
            }
        }
        if (!lines.isEmpty() && badgeText != null) {
            lines.add(ChatColor.GRAY + "You wear the badge " + badgeText + ChatColor.GRAY + " until the next results, on Monday.");
        }
        return lines;
    }

    /** The results, plain, for the log. */
    static String logLine(Results results) {
        StringBuilder line = new StringBuilder("Leaderboards of the week " + results.week + ":");
        for (Podium podium : results.podiums) {
            line.append(' ').append(podium.board).append(':');
            for (Place place : podium.places) {
                line.append(" #").append(place.place).append(' ').append(place.name).append(" (").append(place.score).append(')');
            }
            line.append(';');
        }
        if (results.podiums.isEmpty()) {
            line.append(" nobody played");
        }
        return line.toString();
    }

    /** The leaderboards of the other plugins, by name, for the week: a broken one is left out. */
    static Map<String, List<Map.Entry<UUID, String>>> rankings(Map<String, Board> boards, String week,
            java.util.function.BiConsumer<String, RuntimeException> broken) {
        Map<String, List<Map.Entry<UUID, String>>> rankings = new LinkedHashMap<>();
        for (Map.Entry<String, Board> board : boards.entrySet()) {
            try {
                List<Map.Entry<UUID, String>> ranking = board.getValue().ranking(week);
                rankings.put(board.getKey(), ranking == null ? Collections.<Map.Entry<UUID, String>>emptyList() : ranking);
            } catch (RuntimeException e) {
                broken.accept(board.getKey(), e);
            }
        }
        return rankings;
    }
}
