package gg.rago.lobby;

/** What a leaderboard ranks (config.yml, classements: ... tableaux:). */
enum BoardStat {

    /** Games won, of the day, of the week or ever: a click changes the period. */
    WINS("victoires", "Wins", Choice.PERIOD),
    /** Games played, of the day, of the week or ever. */
    GAMES("parties", "Games played", Choice.PERIOD),
    /** The longest run of wins reached during the day, the week or ever. */
    STREAK("series", "Best win streak", Choice.PERIOD),
    /** Kills (in a sumo, knocking the other player off), of the day, of the week or ever. */
    KILLS("kills", "Kills", Choice.PERIOD),
    /** The Network level, ranked by XP. */
    LEVEL("niveau", "Network level", Choice.NONE),
    /** The ELO of a ranked mode: a click changes the mode. */
    ELO("elo", "Ranked Elo", Choice.RANKED_MODE);

    /** What a click on a board of this stat changes. */
    enum Choice {
        /** Day, week or ever. */
        PERIOD,
        /** One of the ranked modes. */
        RANKED_MODE,
        /** Nothing: there is one board. */
        NONE
    }

    private final String key;
    private final String title;
    private final Choice choice;

    BoardStat(String key, String title, Choice choice) {
        this.key = key;
        this.title = title;
        this.choice = choice;
    }

    /** The name in config.yml. */
    String key() {
        return key;
    }

    /** The title above the board, without colour. */
    String title() {
        return title;
    }

    Choice choice() {
        return choice;
    }

    /** True if a click changes its period. */
    boolean periodic() {
        return choice == Choice.PERIOD;
    }

    /** The stat of this config.yml name, or null. */
    static BoardStat of(String key) {
        for (BoardStat stat : values()) {
            if (stat.key.equals(key)) {
                return stat;
            }
        }
        return null;
    }

    /** "victoires, parties, series, kills, niveau or elo", for the errors of config.yml. */
    static String keys() {
        StringBuilder text = new StringBuilder();
        BoardStat[] all = values();
        for (int i = 0; i < all.length; i++) {
            text.append(i == 0 ? "" : i == all.length - 1 ? " or " : ", ").append(all[i].key);
        }
        return text.toString();
    }
}
